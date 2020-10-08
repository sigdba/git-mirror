(ns git-mirror.aws-code-commit
  (:require [lambdaisland.uri :as uri]
            [clojure.string :as str]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.client.shared :as aws-shared]
            [cognitect.aws.credentials :as aws-cred]
            [clj-jgit.porcelain :as git]))

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

#_(let [repos git-mirror.gitolite/ALL-REPOS
        repo (->> repos first)]
    (str "Mirror of " (:url repo)))

#_(aws-cred/fetch (aws-shared/credentials-provider))

#_(let [cc (aws/client {:api :codecommit})]
  (let [cc-repo (get-repository cc "delete-me")
        cc-url (:cloneUrlHttp cc-repo)
        local-repo-path "tmp/banner-src.ellucian.com/mobile/ms-notification-core.git"
        local-repo (git/load-repo local-repo-path)]
    (git/with-credentials {:cred-provider nil})
    (git/git-push local-repo :remote cc-url :all? true :tags? true))
  #_(create-repository! cc "delete-me" "temporary for testing" {})
  #_(aws/doc cc :CreateRepository)
  #_(->> (aws/ops cc)
         keys
         sort))
