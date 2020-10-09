(ns git-mirror.aws-lambda-handler
  (:gen-class
    :methods [^:static [handler [String] String]]))

(defn -handler [s]
  (str "Hello " s "!"))
