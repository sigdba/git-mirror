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
  [mirror-conf]
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

;; TODO: spec the ops so we can give better errors when they're malformed
(defn perform-op
  "Perform a single operation"
  [mirror-conf op-map]
  (let [{:keys [op]} op-map]
    (log/infof "Message:\n%s" (prn-str op-map))
    (case op
      "ping" (log/infof "Conf:\n%s" (prn-str mirror-conf))
      "list-repos" (op-list-repos mirror-conf)
      "queue-for-mirror" (op-queue-for-mirror mirror-conf op-map)
      "mirror" (op-mirror mirror-conf op-map)
      (throw (ex-info "unrecognized request" {:op-map op-map})))))

(defn handle-sqs-event
  "Process a single SQS event, removing it from the queue afterward."
  [mirror-conf event]
  (let [{:keys [body eventSourceARN receiptHandle]} event]
    ;; The event source mapping will keep retrying failed events forever, so we delete the message from the queue
    ;; BEFORE processing it to avoid thrashing.
    (aws-invoke-throw :sqs {:op      :DeleteMessage
                            :request {:QueueUrl      (get-queue-url eventSourceARN)
                                      :ReceiptHandle receiptHandle}}
                      "Error deleting event" {:event event})
    (perform-op mirror-conf body)))

(defn handle-message
  "Handle the various forms that a message may come in."
  [mirror-conf input]
  (let [handle (partial handle-message mirror-conf)]
    (log/debugf "Handling message:\n%s" (prn-str input))
    (cond
      (instance? InputStream input) (->> input slurp handle)
      (string? input) (->> input parse-message handle)
      (sequential? input) (->> input (map handle) doall)
      (:op input) (->> input (perform-op mirror-conf))
      (:Records input) (->> input extract-sqs-events (map #(handle-sqs-event mirror-conf %)) doall)
      :else (throw (ex-info "Unrecognized message" {:input input})))))

(defn -handler [input]
  (log/infof "Starting: %s" REVISION-INFO)
  (let [private-key (get-private-key)
        mirror-conf (conform-or-throw ::ss/mirror-conf "Invalid mirror configuration"
                                      (-> (get-conf)
                                          (assoc-in [:source :private-key-path] private-key)
                                          (assoc :local-cache-path (get-cache-path))))]
    (handle-message mirror-conf input)))

(let [private-key (get-private-key)
      mirror-conf {:source           {:type             :gitolite
                                      :remote-host      "banner-src.ellucian.com"
                                      :private-key-path private-key}
                   :dest             {:type            :code-commit
                                      :ssm-creds-param "delete-me-git-mirror-creds"}
                   :local-cache-path "tmp"
                   :whitelist        [{:path-regex "^/banner"}
                                      #_{:path-regex "banner_student_admissions"}]
                   :blacklist        [{:path-regex "^/banner/tcc/"}]}
      queue-arn "arn:aws:sqs:us-east-1:803071473383:gm-git-mirror-SqsQueue-1QP2ALYFXFMQ0"
      mirror-op {:op         "mirror"
                 :remote-url "ssh://git@banner-src.ellucian.com/banner/plugins/banner_student_admissions"}
      ping-op {:op "ping"}
      list-op {:op "list-repos"}]

  (-handler mirror-op))



#_(let [sqs (aws/client {:api :sqs})]
    (->> (aws/ops sqs)
         keys
         sort)
    (aws/doc sqs :SendMessage))
