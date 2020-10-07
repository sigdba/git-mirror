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

(let [source-conf {:type             :gitolite
                   :remote-host      "banner-src.ellucian.com"
                   :private-key-path "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
                   :urls             ["url1" "url2"]}]
  (s/conform ::gitolite-source-conf source-conf))

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
