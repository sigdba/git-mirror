(ns git-mirror.aws.lambda-handler
  (:use [git-mirror.revision]
        [com.rpl.specter])
  (:require [taoensso.timbre :as log]
            [cognitect.aws.client.api :as aws]
            [clojure.spec.alpha :as s]
            [git-mirror.spec :as ss])
  (:gen-class
    :methods [^:static [handler [String] String]]))

(defn -handler [s]
  (log/infof "Starting: %s" REVISION-INFO)
  "success")

#_(let [spec-ns "git-mirror.spec"]
    (->> (s/registry)
         (filter (fn [[k _]] (= spec-ns (namespace k))))
         (filter (fn [[_ v]] (s/spec? v)))
         ))


#_(let [sqs (aws/client {:api :sqs})]
    #_(->> (aws/ops sqs)
           keys
           sort)
    #_(aws/doc sqs :SendMessageBatch))

(let [json-conf {"source"           {"type"             "gitolite"
                                     "private-key-path" "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
                                     "remote-host"      "banner-src.ellucian.com"}

                 "dest"             {"type"            "code-commit"
                                     "ssm-creds-param" "delete-me-git-mirror-creds"}
                 "local-cache-path" "tmp"
                 "whitelist"        [{"path-regex" "banner_student_admissions"}]
                 "blacklist"        [{"path-regex" "^/banner/plugins/banner_common_api"}]}]

  (->> json-conf
       (transform [MAP-KEYS] keyword)
       (transform [MAP-VALS map? MAP-KEYS] keyword)
       (transform [MAP-VALS vector? ALL map? MAP-KEYS] keyword)
       (transform [:source :type] keyword)
       (transform [:dest :type] keyword)
       (s/explain-data ::ss/mirror-conf))

  #_(-> (s/explain-data ::ss/mirror-conf json-conf)
      ::s/problems))
