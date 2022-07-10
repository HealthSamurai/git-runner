(ns build
  (:require [clojure.test]
            [clojure.java.io :as io])
  (:gen-class))

(defn read-build []
  (read-string (slurp ".zeroci/job.edn")))

(defn run-tests [build]
  (require '[example-test])
  (let [res (clojure.test/run-all-tests)]
    (spit (str ".zeroci/builds/" (:id build) ".edn") (pr-str {:result res :commit build}))))

(defn start []
  (let [commit (read-build)]
    (run-tests commit)
    (let [svc-path (str (System/getProperty "user.dir") "/.zeroci/services")]
      (.mkdirs (io/file svc-path))
      (spit (str svc-path "/demo.edn")
            (str {:exec ["clojure" "-M:prod" "example" :port]
                  :version (:new commit)})))))

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
