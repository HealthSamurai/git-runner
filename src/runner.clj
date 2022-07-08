(ns runner
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh] :as shell])
  (:import [java.io File]
           [java.lang ProcessBuilder]
           [java.security MessageDigest]
           [java.math BigInteger]))

(defn read-stream [s]
  (with-open [r (clojure.java.io/reader s)]
    (loop [acc []]
      (if-let [l (.readLine r)]
        (recur (conj acc l))
        acc))))

(defn proc [{dir :dir env :env args :exec}]
  (let [proc (ProcessBuilder. (into-array String args))
        _ (when dir (.directory proc (clojure.java.io/file dir)))
        _ (when env
            (let [e (.environment proc)]
              (doseq [[k v] env]
                (.put e (name k) (str v)))))]
    proc))

(defn exec [{dir :dir env :env args :exec :as opts}]
  (let [prc (proc opts)
        p (.start prc)]
    (.waitFor p)
    (let [res {:status (.exitValue p)
               :stdout (read-stream (.getInputStream p))
               :stderr (read-stream (.getErrorStream p))}]
      (println opts "->" res)
      res)))

(defn get-next-commits [{dir :dir}]
  (->>
   (.listFiles (io/file (str dir "/queue")))
   (mapv (fn [x]
           (assoc (read-string (slurp x)) :file (str x))))
   (sort-by :id)
   (group-by :repo)
   (reduce (fn [acc [repo commits]]
             (->> commits
                  (group-by :branch)
                  (reduce (fn [acc [br commits]]
                            (conj acc (first (sort-by :id commits))))
                          acc))
             ) [])))



(def hook-content
  "#!/usr/local/bin/bb
(ns script
  (:require [clojure.string :as str]
            [babashka.fs :as fs]))
(def queue-dir \"%s\")
(def commit (zipmap [:old :new :ref] (str/split (slurp *in*) #\"\\s+\" )))
(def branch (last (str/split (:ref commit) #\"/\")))
(println branch commit)
(fs/create-dirs (str queue-dir))

(def ts (second (str/split (pr-str (java.util.Date.)) #\"\\\"\")))

(spit (str queue-dir \"/\" ts \".edn\") (pr-str (assoc commit :id ts :branch branch :repo (str (fs/cwd)))))
(println \"Scheduled build \" branch \" \" ts)")

(defn init [{dir :dir}]
  (doseq [repo (into [] (.listFiles (io/file (str dir "/repos"))))]
    (println dir (str repo))
    (let [hook (io/file (str repo "/hooks/post-receive"))]
      (when-not (.exists hook)
        (spit
         (str repo "/hooks/post-receive")
         (format hook-content (str dir "/queue")))
        (exec {:exec ["chmod" "u+x" (str repo "/hooks/post-receive")]})))))

(defonce services (atom {}))
(defn watch-services [opts]
  (doseq [branch (into [] (.listFiles (io/file (str (:dir opts) "/branches"))))]
    (doseq [svc-file (into [] (.listFiles (io/file (str branch "/services"))))]
      (when-let [p (get-in @services [svc-file :proc])]
        (println :stop p)
        (.destroyForcibly p))
      (let [svc  (read-string (slurp svc-file))
            _ (println (:exec svc) :dir (str branch "/" (:dir svc)))
            pb (proc {:exec (:exec svc)
                      :dir (str branch "/" (:dir svc))})
            p (.start pb)
            ]
        (println :start svc-file p)
        (swap! services assoc svc-file (assoc svc :proc p))))))

;; TODO: fix need repo
(defn do-run [{wd :dir :as opts} commit]
  (let [br-dir (str wd "/branches/" (:branch commit))]
    (exec {:exec ["mkdir" "-p" wd]})
    (exec {:exec ["mkdir" "-p" (str wd "/done")]})
    (exec {:exec ["mkdir" "-p" (str wd "/branches")]})
    (exec {:exec ["mkdir" "-p" (str wd "/progress")]})
    (exec {:exec ["git" "clone" (:repo commit) "-b" (:branch commit) br-dir]})
    (exec {:exec ["git" "fetch"] :dir br-dir})
    (exec {:exec ["git" "checkout" (:new commit)] :dir br-dir})
    (exec {:exec ["mv" (str (:file commit)) (str wd "/progress/" (:branch commit))]})
    (spit (str br-dir "/build.edn") (pr-str commit))
    (let [res (exec {:exec ["./runme"] :dir br-dir})]
      (exec {:exec ["rm" (str wd "/progress/" (:branch commit))]})
      (spit (str wd "/done/" (:id commit) ".edn")
       (merge commit res)))
    (watch-services opts)))

(defn watch [{dir :dir :as opts}]
  (def main-loop
    (Thread.
     (fn []
       (loop []
         (print ".") (flush)
         (doseq [commit (get-next-commits opts)]
           (println commit)
           (do-run opts commit))
         (Thread/sleep 2000)
         (recur)))))
  (.start main-loop))

(comment
  (def opts {:dir "/tmp/selfci"})
  (init opts)
  (watch opts)
  (watch-services opts)


  @services
  
  (first (get-next-commits opts))
  
  (def pb (proc {:exec ["clojure" "-M:prod" "zd.aidbox" "4447"]
                 :dir "/tmp/selfci/branches/master/zendocs"}))

  (def p (.start pb))

  (.destroyForcibly p)

  )
