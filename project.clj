(defproject git-mirror "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.taoensso/timbre "4.10.0"]             ; Logging
                 [com.fzakaria/slf4j-timbre "0.3.19"]       ; SLF4J logging to timbre
                 [expound "0.8.4"]                          ; Improved spec messages
                 [lambdaisland/uri "1.4.54"]                ; URL parsing
                 [clj-commons/clj-ssh "0.5.15"]             ; Pure Java SSH client
                 [clj-jgit "1.0.0"]]                        ; Pure Java Git client
  :repl-options {:init-ns git-mirror.core})
