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
  (def bare (cmd/path root "repos" "example"))

  (def workdir (cmd/path root "workspace" "example" "master"))
  
  ;; (cmd/$> `[rm -rf ~tmp])

  (def ztx (atom {:zeroci/opts {:root root}}))

  (sub/init-repo ztx "example")

  (cmd/$> `[cp -R ~(cmd/path (fs/cwd) "example") ~repo])

  (cmd/$> `[git init && git add . && git commit -m "init"] {:dir repo})
  (cmd/$> `[git remote add test ~bare] {:dir repo})

  (cmd/$> `[ls -lah ~bare])

  (cmd/$> `[git push --set-upstream test master] {:dir repo})

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

  (sub/run-jobs ztx)

  ;; (cmd/$> '[ls -Ra] {:dir workdir})

  ;; (cmd/$> '[bash -c "rm -rf /tmp/zerocitest*"])


  )




