(ns git-mirror.aws.aws-util
  (:require [cognitect.aws.client.api :as aws]
            [taoensso.timbre :as log]))

(defn throw-aws-err
  "Throws an ex-info exception with the given message and info dict along with the AWS response."
  [msg info-map response]
  (throw (ex-info msg (assoc info-map :response response))))

;; TODO: This would be more flexible if it used a *ref*
(def get-aws-client (memoize (fn [api] (aws/client {:api api}))))

(defn aws-invoke-throw
  "Invokes the given AWS request and returns the response or throws an exception if it failed."
  [api op-map msg info-map]
  (let [client (if (keyword? api) (get-aws-client api) api)]
    (log/debugf "Invoking AWS %s: %s" api (prn-str op-map))
    (let [resp (aws/invoke client op-map)]
      (if (:cognitect.anomalies/category resp)
        (throw-aws-err msg info-map resp)
        resp))))

(defn get-ssm-param
  ([param-name] (get-ssm-param :ssm param-name))
  ([client param-name]
   (->> (aws-invoke-throw client {:op      :GetParameter
                                  :request {:Name           param-name
                                            :WithDecryption true}}
                          "Error fetching SSM parameter"
                          {:param-name param-name})
        :Parameter :Value)))
