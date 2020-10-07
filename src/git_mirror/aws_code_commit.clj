(ns git-mirror.aws-code-commit
  (:require [lambdaisland.uri :as uri]
            [clojure.string :as str]
            [cognitect.aws.client.api :as aws]))

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

(defn get-repository
  "Returns the AWS info about a given CodeCommit repo or nil if it doesn't exist.
  An exception will be thrown if any other type of failure occurs."
  [client repo-name]
  (let [{failed :cognitect.anomalies/category type :__type :as resp}
        (aws/invoke client {:op      :GetRepository
                            :request {:repositoryName repo-name}})]
    (if failed
      (if (= type "RepositoryDoesNotExistException") nil
                                                     (throw-aws-err "error getting CodeCommit repo info"
                                                                    {:repo-name repo-name} resp))
      resp)))

#_(let [repos git-mirror.gitolite/ALL-REPOS]
    (->> repos
         (map path-munge)
         (map count)
         (reduce max)))

(let [cc (aws/client {:api :codecommit})]
  (aws/doc cc :CreateRepository)
  #_(->> (aws/ops cc)
         keys
         sort))
