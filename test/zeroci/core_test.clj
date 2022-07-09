(ns zeroci.core-test
  (:require
   [zeroci.core :as sub]
   [babashka.fs :as fs]
   [zeroci.cmd :as cmd]
   [clojure.test :as t]))

(t/deftest test-runner
  (def tmp (str "/tmp/zerocitest" (int (rand 1000))))

  (def root (cmd/path tmp "root"))
  (def repo (cmd/path tmp "repo"))
  (def bare (cmd/path root "repos" "zeroci"))
  
  ;; (cmd/$> `[rm -rf ~tmp])

  (def ztx (atom {:zeroci/opts {:root root}}))

  (sub/init-repo ztx "zeroci")

  (cmd/$> `[git clone ~(str (fs/cwd)) ~repo])

  (cmd/$> `[git remote add test ~bare] {:dir repo})

  (cmd/$> `[ls -lah ~bare])

  (cmd/$> `[git push test] {:dir repo})

  (t/is (seq (cmd/lns (cmd/$> `[git log] {:dir bare}))))

  (cmd/lns (cmd/$> `[git branch] {:dir bare}))

  (t/is (slurp (cmd/path bare "hooks" "post-receive")))

  (spit (cmd/path repo "ups.txt") (str (java.util.Date.)))

  (cmd/$> `[git add .] {:dir repo})
  (cmd/$> `[git commit -m "test"] {:dir repo})

  (println
   (cmd/lns
    (cmd/$> `[git push test] {:dir repo})))


  (Thread/sleep 1000)
  (t/is (= 1 (count (sub/get-jobs ztx))))

  ;; (println (sub/get-jobs ztx))

  (cmd/files "/tmp")

  (sub/run-jobs ztx)

  (cmd/$> '[bash -c "rm -rf /tmp/zerocitest*"])

  )




