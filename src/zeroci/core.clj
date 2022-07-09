(ns zeroci.core
  (:require [clojure.java.io :as io]
            [babashka.fs :as fs]
            [zeroci.cmd :as cmd]))

(def hook-tpl (slurp (io/resource "zeroci/hook.clj.tpl")))

(defn get-root [ztx]
  (get-in @ztx [:zeroci/opts :root]))

(defn get-queue-dir [root]
  (cmd/path root "queue"))

(defn init [ztx]
  (let [root (get-root ztx)]
    (cmd/mkdirs root)
    (cmd/mkdirs root "queue")
    (cmd/mkdirs root "repos")
    (cmd/mkdirs root "workspace")
    (cmd/mkdirs root "progress")
    (cmd/mkdirs root "done")))

(defn init-repo
  "init bare repo, setup hooks"
  [ztx repo-name]
  ;; git init --bare test_repo.git
  (init ztx)
  (let [root (get-root ztx)]
    (if-not (fs/exists? (cmd/path (cmd/path root "repos" repo-name)))
      (do
        (cmd/mkdirs root "repos" repo-name)
        (cmd/$> `[git --bare init] {:dir (cmd/path root "repos" repo-name)})
        (let [hook-file (cmd/path root "repos" repo-name "hooks" "post-receive")]
          (spit hook-file  hook-tpl)
          (cmd/$> `[chmod u+x ~hook-file])))
      :error/file-esists)))

(defn get-jobs [ztx]
  (let [root (get-root ztx)]
    (->> (cmd/files (get-queue-dir root))
         (mapv (fn [f] (read-string (slurp f))))
         (group-by (fn [x] [(:reop x) (:branch x)]))
         (reduce (fn [acc [_ xs]]
                   (conj acc (first (sort-by :ts xs))))
                 []))))

(defn run-job[ztx {repo :repo repo-path :repo-path branch :branch :as job}]
  (println :run-job job)
  (let [root (get-root ztx)
        work-dir (cmd/path root "workspace" repo branch)
        progress-file (cmd/path root "progress" (str repo "-" branch ".edn"))]
    (cmd/mkdirs work-dir)
    (cmd/$> `[git clone ~repo-path -b ~branch ~work-dir])
    (cmd/$> `[git fetch] {:dir work-dir})
    (cmd/$> `[git checkout ~(:new job)] {:dir work-dir})
    (cmd/mv (:file job) progress-file)
    (spit (cmd/path work-dir "job.edn") (pr-str job))

    ))

#_(let [br-dir (str wd "/branches/" (:branch commit))]
    (let [res (exec {:exec ["./runme"] :dir br-dir})]
      (exec {:exec ["rm" (str wd "/progress/" (:branch commit))]})
      (spit (str wd "/done/" (:id commit) ".edn")
            (merge commit res)))
    (watch-services opts))

(defn run-jobs [ztx]
  (doseq [job (get-jobs ztx)]
    (run-job ztx job)))

(defn start-services [ztx])

(defn stop-services [ztx])

(defn backup [ztx])

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


