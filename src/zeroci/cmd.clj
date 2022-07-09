(ns zeroci.cmd
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.java.shell :refer [sh] :as shell])
  (:import [java.io File]
           [java.lang ProcessBuilder]
           [java.security MessageDigest]
           [java.math BigInteger]))

(defn path [& args]
  (str/join "/" args ))


(defn files [dir]
  (let [d (io/file dir)]
    (when (.exists d)
      (into [] (.listFiles d)))))

(defn u [& args]
  (->> args
       (reduce (fn [acc x]
                 (if (nil? x)
                   acc
                   (conj acc
                         (cond
                           (vector? x)
                           (apply u x)
                           (string? x) (str \" x \")
                           (or (keyword? x)  (symbol? x))
                           (name x)

                           (map? x)
                           (->> x
                                (mapv (fn [[k v]]
                                        (str "-" (name k)
                                             (when-not (= true v)
                                               (str " " v)))))
                                (str/join " "))
                           :else (str x)))))
               [])
       (str/join " ")))


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

(defn proc-res [res]
  (if (= (:exit res) 0)
    (do 
      (println :ok (:cmd res))
      (when-let [o (:out res)]
        (when-not (str/blank? o)
          (println :out o)))
      (when-let [o (:error res)]
        (when-not (str/blank? o)
          (println :error o)))
      (:out res))
    (do
      (println :error (:cmd res))
      (when-let [o (:out res)]
        (println :out o))
      (when-let [o (:error res)]
        (println :error o))
      (throw (Exception. (str (:cmd res) ": " (:err res)))))))

(defn $> [cmd & [opts]]
  (let [cmd* (u cmd)
        res (apply clojure.java.shell/sh "bash" "-c" cmd* (apply concat (vec opts)))]
    (proc-res (assoc res :cmd cmd*))))


(defn lns [s]
  (str/split s #"\n"))

(defn mkdirs [& pth]
  ($> `[mkdir -p ~(apply path pth)]))

(defn mv [from to]
  ($> `[mv ~from ~to]))
