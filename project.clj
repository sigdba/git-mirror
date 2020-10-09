(defproject git-mirror "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.taoensso/timbre "4.10.0"]             ; Logging
                 [com.fzakaria/slf4j-timbre "0.3.19"]       ; SLF4J logging to timbre
                 [expound "0.8.4"]                          ; Improved spec messages
                 [lambdaisland/uri "1.4.54"]                ; URL parsing
                 [clj-commons/clj-ssh "0.5.15"]             ; Pure Java SSH client
                 [clj-jgit "1.0.0"]                         ; Pure Java Git client

                 ;; Lambda integration
                 [com.amazonaws/aws-lambda-java-core "1.2.1"]

                 ;; AWS API
                 [com.cognitect.aws/api "0.8.474"]
                 [com.cognitect.aws/endpoints "1.1.11.842"]
                 [com.cognitect.aws/ssm "807.2.729.0"]
                 [com.cognitect.aws/codecommit "801.2.704.0"]]
  :repl-options {:init-ns git-mirror.core}
  :aot :all)
