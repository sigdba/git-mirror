(ns git-mirror.gitolite
  (:use [git-mirror.util])
  (:require [clojure.string :as str]
            [clj-ssh.cli :as ssh-cli]
            [clj-ssh.ssh :as ssh]
            [git-mirror.spec :as ss]))

(defn- ssh-repo-list-fetch-fn
  "Connects to remote gitolite server hostname and return a map with :exit code, :out, and :err messages."
  [hostname private-key-path]
  (let [agent (ssh/ssh-agent {})]
    (ssh/add-identity agent {:private-key-path private-key-path})
    (ssh-cli/with-ssh-agent agent
      (ssh-cli/ssh hostname "" :username "git" :strict-host-key-checking :no))))

(defn- parse-fetch-return
  "Returns a seq of repository URLs from the return value of a function like ssh-repo-list-fetch-fn or throws an error
   if it failed."
  [hostname fetch-ret]
  (let [{:keys [exit out]} fetch-ret
        remote-user "git"]
    (if-not (= 0 exit)
      (throw (ex-info "error fetching repository list" fetch-ret))
      (->> out str/split-lines
           (map str/trim)
           (map #(re-matches #"^R\s+(.+)" %))
           (filter identity)
           (map second)
           (map #(format "ssh://%s@%s/%s" remote-user hostname %))))))

(defn get-repos
  "Returns a seq of repo URLs"

  ([source-conf]
   (let [{:keys [remote-host private-key-path]}
         (conform-or-throw ::ss/gitolite-source-conf
                           "invalid gitolite source configuration" source-conf)]
     (get-repos remote-host private-key-path #(ssh-repo-list-fetch-fn remote-host private-key-path))))

  ([hostname private-key-path fetch-fn]
   (->> (fetch-fn)                                          ; fetch the list from the remote host
        (parse-fetch-return hostname)
        (map #(assoc {:private-key-path private-key-path} :url %)))))

#_(def ALL-REPOS
  (let [private-key-path "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
        hostname "banner-src.ellucian.com"]
    (get-repos {:remote-host hostname :private-key-path private-key-path})))
