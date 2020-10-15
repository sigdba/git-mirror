(ns git-mirror.aws.lambda-handler
  (:use [git-mirror.revision]
        [git-mirror.util]
        [git-mirror.aws.aws-util])
  (:require [taoensso.timbre :as log]
            [git-mirror.spec :as ss]
            [git-mirror.core :refer [get-source-repos mirror-single-with-conf]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.edn :as edn])
  (:import (java.io File InputStream))
  (:gen-class
    :methods [^:static [handler [java.io.InputStream] void]]))

(defn- parse-message
  "Returns a data structure from a string or the original structure if it's not a string"
  [s]
  (if (string? s) (json/parse-string s true) s))

(defn- extract-sqs-events
  "Extracts and parses the message bodies from an SQS return"
  [recv]
  (->> recv :Records
       (map #(update % :body parse-message))))

(defn- require-env-var
  "Gets an environment variable and throws an exception if it's not defined"
  [env-var]
  (let [ret (System/getenv env-var)]
    (if-not ret (throw (ex-info (str "Missing environment variable: " env-var) {:env-var env-var})))
    ret))

(defn- ssm-param-from-env
  "Fetches an SSM param specified by an environment variable"
  [env-var]
  (->> env-var require-env-var (get-ssm-param)))

(defn- get-conf
  "Read the mirror-conf from an SSM parameter specified in an environment variable"
  []
  (->> (ssm-param-from-env "GITMIRROR_CONF_SSM_PARAM")
       edn/read-string))

(defn- get-private-key
  "Read the private key from an SSM parameter specified in an environment variable, write it to a file,
   and return the path."
  []
  (let [fp (.getAbsolutePath (File/createTempFile "gmk" ".asc"))]
    (->> (ssm-param-from-env "GITMIRROR_SOURCE_PRIVKEY_SSM_PARAM")
         (spit fp))
    fp))

(defn- get-cache-path
  "Reads the cache path from an environment variable"
  []
  (require-env-var "GITMIRROR_CACHE_DIR"))

(defn- -get-queue-url
  "Retrieve the URL for the given queue"
  [arn]
  (let [name (-> arn (str/split #":") last)]
    (-> (aws-invoke-throw :sqs {:op      :GetQueueUrl
                                :request {:QueueName name}}
                          "Error retrieving SQS queue URL" {})
        :QueueUrl)))

(def get-queue-url (memoize -get-queue-url))

(defn op-queue-for-mirror
  "Fetch the list of repositories to mirror and push them into the SQS queue"
  [mirror-conf op-map]
  (log/info "Queuing repos for mirroring...")
  (let [{:keys [queue-arn]} op-map
        queue-url (get-queue-url queue-arn)]
    (->> (get-source-repos mirror-conf)
         (map :url)
         (map #(assoc {:op "mirror"} :remote-url %))
         (map json/encode)
         (map #(aws-invoke-throw :sqs {:op      :SendMessage
                                       :request {:QueueUrl    queue-url
                                                 :MessageBody %}}
                                 "Failed to queue repo for mirroring" {}))
         doall)))

(defn op-list-repos
  "Fetch the list of repositories to mirror and dump it to the log"
  [mirror-conf _]
  (log/debug "Fetching repository list")
  (->> (get-source-repos mirror-conf)
       (map :url)
       (str/join "\n")
       (log/infof "Repositories to mirror:\n%s")))

(defn op-mirror
  "Mirror a single repository"
  [mirror-conf op-map]
  (let [{:keys [remote-url]} op-map]
    (mirror-single-with-conf mirror-conf {:url              remote-url
                                          :private-key-path (get-in mirror-conf [:source :private-key-path])})))

(def get-mirror-conf (memoize (fn [] (conform-or-throw ::ss/mirror-conf "Invalid mirror configuration"
                                                       (-> (get-conf)
                                                           (assoc-in [:source :private-key-path] (get-private-key))
                                                           (assoc :local-cache-path (get-cache-path)))))))

(defn- get-op-fn
  "Returns the function for the named op"
  [op-name]
  (let [op-fn-name (str "op-" op-name)
        sym (->> (ns-publics 'git-mirror.aws.lambda-handler)
                 (filter (fn [[s _]] (= (name s) op-fn-name)))
                 (filter (fn [[_ f]] (->> f meta :arglists (map count) (reduce max) (= 2))))
                 (map first)
                 first)]
    (and sym (resolve sym))))

;; TODO: spec the op-map's so we can give better errors when they're malformed
(defn perform-op
  "Perform a single operation"
  [op-map]
  (let [{:keys [op]} op-map
        op-fn (get-op-fn op)]
    (when-not op-fn (throw (ex-info (str "Unrecognized op: " op) {:op-map op-map})))
    (op-fn (get-mirror-conf) op-map)))

(defn handle-sqs-event
  "Process a single SQS event, removing it from the queue afterward."
  [event]
  (let [{:keys [body eventSourceARN receiptHandle]} event]
    (perform-op body)
    (aws-invoke-throw :sqs {:op      :DeleteMessage
                            :request {:QueueUrl      (get-queue-url eventSourceARN)
                                      :ReceiptHandle receiptHandle}}
                      "Error deleting event" {:event event})))

(defn handle-message
  "Handle the various forms that a message may come in."
  [input]
  (log/debugf "Handling message:\n%s" (prn-str input))
  (cond
    (instance? InputStream input) (->> input slurp handle-message)
    (string? input) (->> input parse-message handle-message)
    (sequential? input) (->> input (map handle-message) doall)
    (:op input) (perform-op input)
    (:Records input) (->> input extract-sqs-events (map handle-sqs-event) doall)
    :else (throw (ex-info "Unrecognized message" {:input input}))))

(defn -handler [input]
  (log/infof "Starting: %s" REVISION-INFO)
  (handle-message input))
