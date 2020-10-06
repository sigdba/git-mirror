(ns git-mirror.core
  (:require [clj-jgit.porcelain :as git]
            [lambdaisland.uri :refer [uri]]))

#_(git/git-clone "ssh://git@banner-src.ellucian.com/banner/plugins/banner_student_capp"
                 :dir "/Users/dboitnot/projects/git-mirror/tmp/banner/plugins/banner_student_capp.git"
                 :mirror? true)

(let [url "ssh://git@banner-src.ellucian.com/banner/plugins/banner_student_faculty_ui"]
  (->> url uri :path))
