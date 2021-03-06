(ns build
  (:require [clojure.test]
            [clojure.java.io :as io])
  (:gen-class))

(defn read-build []
  (read-string (slurp "job.edn")))

(defn run-tests [build]
  (require '[zeroci.core-test])
  (let [res (clojure.test/run-all-tests)]
    (spit (str "docs/" (:id build) ".html") (pr-str {:result res :commit build}))))


(defn start []
  (let [commit (read-build)]
    (run-tests commit)
    (let [svc-path (str (System/getProperty "user.dir") "/services")]
      (.mkdirs (io/file svc-path))
      (spit (str svc-path "/docs.edn")
            (str {:exec ["clojure" "-M:prod" "zd.aidbox" "4445"]
                  :version (:new commit)
                  :dir "zendocs"})))))

(defn -main [& args]
  (start)
  (System/exit 0))

(comment
  (start )

  ;; run tests
  ;; generate docs

  (.mkdirs (io/file (str (System/getProperty "user.dir") "/services")))

  (start)


  )
