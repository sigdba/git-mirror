(ns git-mirror.util
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [expound.alpha :as exp])
  (:import (java.io File)))

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

(defn join-path
  "Returns a path string by joining it's arguments using the OS path separator"
  [path & parts]
  (->> ^File (apply io/file path parts)
       .getPath))

(defn dirname
  "Returns the parent directory of the given path."
  [path]
  (-> path io/file .getParent str))

(defn basename
  "Returns the file name portion of the path."
  [path]
  (-> path io/file .getName))
