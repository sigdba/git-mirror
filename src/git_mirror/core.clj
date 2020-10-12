(ns git-mirror.core
  (:use [git-mirror.util]
        [git-mirror.filter])
  (:require [git-mirror.spec :as ss]
            [git-mirror.gitolite :as gitolite]
            [git-mirror.aws.code-commit :as cc]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [lambdaisland.uri :as uri]
            [clj-jgit.porcelain :as git])
  (:import (org.eclipse.jgit.lib ProgressMonitor)))

(defn- logging-progress-monitor
  "Returns a org.eclipse.jgit.lib.ProgressMonitor which logs at DEBUG level"
  [prefix]
  (let [task-count (atom 0)
        cur-task-num (atom 0)
        total-work (atom 0)
        work-completed (atom 0)
        task-title (atom nil)]
    (reify ProgressMonitor
      (beginTask [_ title totalWork]
        (reset! task-title title)
        (reset! work-completed 0)
        (reset! total-work totalWork)
        (swap! cur-task-num inc)
        (log/debugf "%s [%s/%s] %s (%s)" prefix @cur-task-num @task-count title totalWork))
      (endTask [_] nil)
      (isCancelled [_] false)
      (start [_ totalTasks]
        (reset! task-count totalTasks))
      (update [_ completed]
        (swap! work-completed + completed)
        (log/tracef "%s [%s/%s] %s (%s/%s)" prefix @cur-task-num @task-count @task-title @work-completed @total-work)))))

(defmulti get-remotes
  "Returns a seq of remote-spec's for the given source"
  :type)

(defmethod get-remotes :static [conf]
  (let [{:keys [private-key-path urls]} (conform-or-throw ::ss/static-source-conf "invalid static source conf" conf)]
    (map #(assoc {:private-key-path private-key-path} :url %) urls)))

(defmethod get-remotes :gitolite [conf]
  (gitolite/get-repos conf))

(defmulti get-dest-update-fn
  "Returns a function which accepts a remote-spec with local-path to update from the local repo"
  :type)

(defmethod get-dest-update-fn :code-commit [dest-conf]
  (conform-or-throw ::ss/code-commit-dest-conf "invalid CodeCommit destination conf" dest-conf)
  (partial cc/update-cc-from-local dest-conf))

(defn update-local-repo
  "Updates a repo on local disk from the given remote and returns the jgit repository object of the local repo"
  [base-path remote]
  (let [{:keys [private-key-path url]} remote
        {remote-host :host remote-path :path} (uri/uri url)
        local-path (str (join-path base-path remote-host) remote-path ".git")
        local-fp (io/file local-path)
        progress-monitor (logging-progress-monitor local-path)]
    (try
      (git/with-identity {:name       (basename private-key-path)
                          :key-dir    (dirname private-key-path)
                          :trust-all? true}
        (if (.exists local-fp)
          (if-not (.isDirectory local-fp)
            (throw (ex-info (str "Local path exists but it's not a directory: " local-path) {:remote remote}))

            ;; Local path already exists (and is a directory) so fetch into it rather than re-cloning.
            (do (log/infof "Fetching updates: %s -> %s" url local-path)
                (-> (git/load-repo local-path)
                    (git/git-fetch :tag-opt :fetch-tags :monitor progress-monitor))))

          ;; Local path doesn't exist so this is a new clone.
          (do (log/infof "Cloning new repo: %s -> %s" url local-path)
              (git/git-clone url :dir local-path :mirror? true :monitor progress-monitor))))

      ;; Return the remote along with the local path
      (assoc remote :local-path local-path)

      (catch Exception e
        (log/errorf e "Error updating %s" remote)
        nil))))

(defn- mirror-single
  "Mirror a single repository"
  [local-cache-path update-dest-fn remote-spec]
  (log/infof "Mirroring %s" (:url remote-spec))
  (when-let [r (update-local-repo local-cache-path remote-spec)]
    (update-dest-fn r)))

(defn mirror-all
  "Mirror all repositories based on conf"
  [conf]
  (let [{:keys [source dest whitelist blacklist local-cache-path]} (conform-or-throw ::ss/mirror-conf
                                                                                     "Invalid mirror config" conf)
        update-dest (get-dest-update-fn dest)]
    (->> source get-remotes
         (filter (whitelist-filter whitelist))
         (remove (blacklist-filter blacklist))
         (map #(mirror-single local-cache-path update-dest %))
         doall)))

#_(let [static-source {:type             :static
                     :private-key-path "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
                     :urls             ["ssh://git@banner-src.ellucian.com/banner/plugins/banner_student_admissions"
                                        "ssh://git@banner-src.ellucian.com/banner/plugins/banner_common_api"
                                        "ssh://git@banner-src.ellucian.com/mobile/ms-notification-core"
                                        "ssh://git@banner-src.ellucian.com/powercampus/integrationservices-900"]}

      gitolite-source {:type             :gitolite
                       :private-key-path "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
                       :remote-host      "banner-src.ellucian.com"}
      conf {:source           gitolite-source

            :dest             {:type            :code-commit
                               :ssm-creds-param "delete-me-git-mirror-creds"}
            :local-cache-path "tmp"
            :whitelist        [#_{:path-regex "^/banner"}
                               {:path-regex "banner_student_admissions"}]
            :blacklist        [{:path-regex "^/banner/plugins/banner_common_api"}]}]
  (mirror-all conf))

#_(let [static-conf {:type             :static
                     :private-key-path "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
                     :urls             ["ssh://git@banner-src.ellucian.com/banner/plugins/banner_codenarc"]}
        gitolite-conf {:type             :gitolite
                       :private-key-path "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
                       :remote-host      "banner-src.ellucian.com"}
        conf gitolite-conf
        base-path "tmp"]
    (->> (get-remotes conf)
         #_(map #(update-local-repo base-path %))
         #_doall))

#_(git/git-clone "ssh://git@banner-src.ellucian.com/banner/plugins/banner_student_capp"
                 :dir "/Users/dboitnot/projects/git-mirror/tmp/banner/plugins/banner_student_capp.git"
                 :mirror? true)

#_(let [url "ssh://git@banner-src.ellucian.com/banner/plugins/banner_student_faculty_ui"]
    (->> url uri :path))
