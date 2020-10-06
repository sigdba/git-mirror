(ns git-mirror.filter
  (:use [git-mirror.util])
  (:require [git-mirror.spec :as ss]
            [lambdaisland.uri :as uri]))

(defn single-filter-with
  "Returns a filter function for the given filter conf"
  [conf]
  (let [{:keys [path-regex]} (conform-or-throw ::ss/repo-filter "Invalid filter configuration" conf)]
    (fn [{:keys [url]}]
      (let [{:keys [path]} (uri/uri url)]
        (-> path-regex re-pattern (re-find path))))))

(defn match-any-filter
  "Returns a single filter function which returns true-ish if it's argument matches any filter"
  [conf-list]
  (let [filter-fns (map single-filter-with conf-list)]
    (fn [repo]
      (->> filter-fns
           (filter #(% repo))
           first))))

(defn whitelist-filter
  "Returns a filter function where conf-list is treated as a whitelist"
  [conf-list]
  (if (empty? conf-list) (constantly true)                  ; If the whitelist is nil, always return true
                         (match-any-filter conf-list)))     ; Otherwise, only return true on match

(defn blacklist-filter
  "Returns a filter function where conf-list is treated as a blacklist"
  [conf-list]
  (if (empty? conf-list) (constantly nil)                   ; If the blacklist is nil, always return nil
                         (match-any-filter conf-list)))     ; Otherwise, return true on match
