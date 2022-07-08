TODO

workdir

mkdir -p workdir/repos
git clone --mirror git@github.com:HealthSamurai/<myproject>.git workdir/repos/<myproject>


(def opts {:dir "workdir/repos"})
(init opts)
(watch opts)


git clone workdir/repos/<myproject> play-repo
cd play-repo
date >> todo && git add . && git ci -m 'fix' && git push
