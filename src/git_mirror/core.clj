(ns git-mirror.core
  (:use [git-mirror.util]
        [git-mirror.filter])
  (:require [git-mirror.spec :as ss]
            [git-mirror.gitolite :as gitolite]
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
      (catch Exception e
        (log/errorf e "Error updating %s" remote)
        nil))))

#_(let [conf {:source           {:type             :static
                               :private-key-path "/Users/dboitnot/.ssh/id_rsa_sig_ellucian_git"
                               :urls             ["ssh://git@banner-src.ellucian.com/banner/plugins/banner_student_admissions"
                                                  "ssh://git@banner-src.ellucian.com/banner/plugins/banner_common_api"
                                                  "ssh://git@banner-src.ellucian.com/mobile/ms-notification-core"
                                                  "ssh://git@banner-src.ellucian.com/powercampus/integrationservices-900"]}
            :local-cache-path "tmp"
            :whitelist        [{:path-regex "^/banner"}
                               {:path-regex "^/mobile"}]
            :blacklist [{:path-regex "^/banner/plugins/banner_common_api"}]}]
  (let [{:keys [source whitelist blacklist local-cache-path]} (conform-or-throw ::ss/mirror-conf
                                                                                "Invalid mirror config" conf)]
    (->> source get-remotes
         (filter (whitelist-filter whitelist))
         (remove (blacklist-filter blacklist))
         (map #(update-local-repo local-cache-path %))
         (filter identity))))

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
