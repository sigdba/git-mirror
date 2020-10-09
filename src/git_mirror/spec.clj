(ns git-mirror.spec
  (:require [clojure.spec.alpha :as s]))

;;
;; Common
;;

(s/def ::private-key-path string?)
(s/def ::url string?)

;;
;; Source Configuration
;;

(s/def ::urls (s/* ::url))
(defmulti source-type :type)

(s/def ::static-source-conf (s/keys :req-un [::urls ::private-key-path]))
(defmethod source-type :static [_] ::static-source-conf)

(s/def ::remote-host string?)
(s/def ::gitolite-source-conf (s/keys :req-un [::remote-host ::private-key-path]))
(defmethod source-type :gitolite [_] ::gitolite-source-conf)

(s/def ::source (s/multi-spec source-type :type))

;;
;; Destination Configuration
;;

(s/def ::ssm-creds-param string?)

(defmulti dest-type :type)

(s/def ::code-commit-dest-conf (s/keys :opt-un [::ssm-creds-param]))
(defmethod dest-type :code-commit [_] ::code-commit-dest-conf)

(s/def ::dest (s/multi-spec dest-type :type))

;;
;; Overall Configuration
;;

(s/def ::path-regex string?)
(s/def ::repo-filter (s/keys :req-un [::path-regex]))       ; Defined like this to allow future expansion
(s/def ::whitelist (s/coll-of ::repo-filter))
(s/def ::blacklist (s/coll-of ::repo-filter))
(s/def ::local-cache-path string?)

(s/def ::mirror-conf (s/keys :req-un [::source ::local-cache-path]
                             :opt-un [::whitelist ::blacklist]))

;;
;; Repository Info
;;

(s/def ::remote-spec (s/keys :req-un [::url ::private-key-path]))
