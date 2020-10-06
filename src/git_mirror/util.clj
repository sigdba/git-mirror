(ns git-mirror.util
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as exp]))

(defn throw-with-spec [spec msg x]
  (throw (ex-info (str msg "\n" (exp/expound-str spec x)) {:spec spec :x x})))

(defn conform-or-throw
  "returns (spec/conform spec x) when valid, throws an ex-info with msg if not"
  [spec msg x]
  (let [res (s/conform spec x)]
    (case res ::s/invalid (throw-with-spec spec msg x)
              res)))

(defn valid-or-throw
  "returns x if it conforms to spec, throws an ex-info with msg if not"
  [spec msg x]
  (if (s/valid? spec x) x
                        (throw-with-spec spec msg x)))
