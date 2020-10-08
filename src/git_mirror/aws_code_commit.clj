(ns git-mirror.aws-code-commit
  (:require [lambdaisland.uri :as uri]
            [clojure.string :as str]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.client.shared :as aws-shared]
            [cognitect.aws.util :as aws-util]
            [cognitect.aws.credentials :as aws-cred]
            [clj-jgit.porcelain :as git])
  (:import (java.util Date)))

(defn- throw-aws-err
  "Throws an ex-info exception with the given message and info dict along with the AWS response."
  [msg info-map response]
  (throw (ex-info msg (assoc info-map :response response))))

(defn path-munge
  "Returns a string which will work as a CodeCommit repo name for the given repo spec"
  [repo]
  (-> repo :url uri/uri :path
      (str/replace-first "/" "")
      (str/replace "/" ".")))

(defn repo-desc
  "Returns a description string from a repo spec"
  [repo]
  (str "Mirror of " (:url repo)))

(defn get-repository
  "Returns the AWS info about a given CodeCommit repo or nil if it doesn't exist.
  An exception will be thrown if any other type of failure occurs."
  [client repo-name]
  (let [{failed :cognitect.anomalies/category type :__type :as resp}
        (aws/invoke client {:op      :GetRepository
                            :request {:repositoryName repo-name}})]
    (if failed
      (if (= type "RepositoryDoesNotExistException")
        nil                                                 ; If it doesn't exist, return nil.
        (throw-aws-err "error getting CodeCommit repo info" ; If there's some other failure, throw an exception.
                       {:repo-name repo-name} resp))
      (:repositoryMetadata resp))))                         ; Otherwise, return the repository info.

(defn create-repository!
  "Creates an AWS CodeCommit repository and returns it's details."
  [client repo-name repo-desc tag-map]
  (let [resp (aws/invoke client {:op      :CreateRepository
                                 :request {:repositoryName        repo-name
                                           :repositoryDescription repo-desc
                                           :tags                  tag-map}})]
    (if (:cognitect.anomalies/category resp)
      (throw-aws-err "Error creating repository" {:repo-name repo-name :repo-desc repo-desc :tags tag-map} resp)
      (:repositoryMetadata resp))))

(defn- region-from-url
  "Returns the region from a CodeCommit URL"
  [http-url]
  (let [parts (-> http-url uri/uri :host (str/split #"\."))]
    (if (< (count parts) 4) (throw (ex-info "Unable to detect region from URL" {:url http-url}))
                            (second parts))))

;; Java implementation of the temp key:
;; https://github.com/spring-cloud/spring-cloud-config/blob/08b293ce3bddeda8fb6577ea191450d6f6cd1bba/spring-cloud-config-server/src/main/java/org/springframework/cloud/config/server/support/AwsCodeCommitCredentialProvider.java#L157

(defn- canonical-request-with
  "Returns the AWS canonical request string for the given CodeCommit URL"
  [url]
  (let [{:keys [host path]} (uri/uri url)]
    (str/join "\n" ["GIT"                                   ; CodeCommit uses GIT as it's request method
                    path                                    ; Request path is the URL path
                    ""                                      ; Empty query string
                    (str "host:" host "\n")                 ; host is the only header
                    "host\n"])))

(defn- signing-key
  "Returns the bytes of the AWS request signing key"
  [short-date region secret-access-key]
  (-> (.getBytes (str "AWS4" secret-access-key) "UTF-8")
      (aws-util/hmac-sha-256 short-date)
      (aws-util/hmac-sha-256 region)
      (aws-util/hmac-sha-256 "codecommit")
      (aws-util/hmac-sha-256 "aws4_request")))

(defn- sign
  "Returns the hex string signature for a CodeCommit request"
  [secret-access-key short-date region to-sign]
  (-> (signing-key short-date region secret-access-key)
      (aws-util/hmac-sha-256 to-sign)
      (aws-util/hex-encode)))

(defn- temp-password
  "Returns a temporary password for CodeCommit HTTP URLs"
  [secret-access-key url]
  (let [region (region-from-url url)
        now (Date.)
        long-date "20201008T080702Z" #_(aws-util/format-date aws-util/x-amz-date-format now)
        short-date (aws-util/format-date aws-util/x-amz-date-only-format now)]
    (->> (str/join "\n" ["AWS4-HMAC-SHA256"
                         (str/replace long-date #"Z$" "")
                         (str short-date "/" region "/codecommit/aws4_request")
                         (->> url canonical-request-with aws-util/sha-256 aws-util/hex-encode)])
         (sign secret-access-key short-date region)
         (str long-date))))

#_(let [repos git-mirror.gitolite/ALL-REPOS
        repo (->> repos first)]
    (str "Mirror of " (:url repo)))

#_(let [cc (aws/client {:api :codecommit})]
  (let [cc-repo (get-repository cc "delete-me")
        cc-url (:cloneUrlHttp cc-repo)
        local-repo-path "tmp/banner-src.ellucian.com/mobile/ms-notification-core.git"
        local-repo (git/load-repo local-repo-path)
        {access-key-id     :aws/access-key-id
         secret-access-key :aws/secret-access-key} (aws-cred/fetch (aws-shared/credentials-provider))
        pw (temp-password secret-access-key cc-url)]
    (git/with-credentials {:login access-key-id :pw pw}
      (git/git-push local-repo :remote cc-url :all? true :tags? true)))
  #_(create-repository! cc "delete-me" "temporary for testing" {})
  #_(aws/doc cc :CreateRepository)
  #_(->> (aws/ops cc)
         keys
         sort))
