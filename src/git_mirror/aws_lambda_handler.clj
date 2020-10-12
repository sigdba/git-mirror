(ns git-mirror.aws-lambda-handler
  (:use [git-mirror.revision])
  (:require [taoensso.timbre :as log])
  (:gen-class
    :methods [^:static [handler [String] String]]))

(defn -handler [s]
  (log/infof "Starting: %s" REVISION-INFO)
  "success")
