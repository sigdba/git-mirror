(ns git-mirror.aws.aws-util
  (:require [cognitect.aws.client.api :as aws]))

(defn throw-aws-err
  "Throws an ex-info exception with the given message and info dict along with the AWS response."
  [msg info-map response]
  (throw (ex-info msg (assoc info-map :response response))))

(defn aws-invoke-throw
  "Invokes the given AWS request and returns the response or throws an exception if it failed."
  [client op-map msg info-map]
  (let [resp (aws/invoke client op-map)]
    (if (:cognitect.anomalies/category resp)
      (throw-aws-err msg info-map resp)
      resp)))

(defn get-ssm-param
  [client param-name]
  (->> (aws-invoke-throw client {:op      :GetParameter
                                 :request {:Name           param-name
                                           :WithDecryption true}}
                         "Error fetching SSM parameter"
                         {:param-name param-name})
       :Parameter :Value))
