#!/usr/local/bin/bb
(ns script
  (:require [clojure.string :as str]
            [babashka.fs :as fs]))

(def commit (zipmap [:old :new :ref] (str/split (slurp *in*) #"\s+" )))
(def branch (last (str/split (:ref commit) #"/")))
(println branch commit)

(def ts (str/replace (second (str/split (pr-str (java.util.Date.)) #"\"")) #"-00:00$" ""))
(def repo-path (str (fs/cwd)))
(def repo-dirs (str/split repo-path #"/"))
(def repo (last repo-dirs))
(def queue-dir (str (str/join  "/" (into [] (butlast (butlast repo-dirs)))) "/queue"))

(def id (str  repo "-" branch "-" (:new commit)))

(fs/create-dirs (str queue-dir))

(println "out" (str queue-dir "/" ts ".edn"))
(def job-file (str queue-dir "/" id ".edn"))

(spit job-file (pr-str (assoc commit :id id :date ts :branch branch :file job-file :repo-path repo-path :repo repo)))

(println "Scheduled build " repo ":" branch " " (:new commit))
