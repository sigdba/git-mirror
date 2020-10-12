(ns git-mirror.aws-lambda-handler
  (:use [git-mirror.revision])
  (:gen-class
    :methods [^:static [handler [String] String]]))

(defn -handler [s]
  (str "Hello " s "!" REVISION-INFO))
