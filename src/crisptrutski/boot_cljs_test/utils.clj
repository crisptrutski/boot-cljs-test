(ns crisptrutski.boot-cljs-test.utils
  (:require
    [boot.core :as boot]
    [boot.pod :as pod]
    [boot.util :refer [warn]]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    [java.io File]
    [java.util.regex Pattern]
    [java.nio.file Paths Path]))

(defmacro r
  "Ensure symbol is loaded, and then resolve it. Useful with pods."
  [sym]
  `(do (require '~(symbol (namespace sym))) (resolve '~sym)))

(defn filter-deps
  "Given a list of dependencies, return a list of those which are not already loaded."
  [keys deps]
  (let [dependencies (mapv #(vector (symbol (subs (str %) 1)) (deps %)) keys)]
    (remove pod/dependency-loaded? dependencies)))

(defn file->ns
  "Determine namespace from filename"
  [filename]
  (-> filename
      (str/replace #"\.clj.?$" "")
      (str/replace "_" "-")
      (str/replace #"\/|\\" ".")
      symbol))

(defn ns-regex [ns]
  (if (instance? Pattern ns) ns (re-pattern (str "\\A" (name ns) "\\z"))))

(defn ^Path filename->path
  "A sane constructor `java.nio.file.Path` instances."
  [filename]
  ;; Workaround for bad time with native dispatch and Java splat.
  ;; Otherwise dispatches to URI even with ^String hint.
  (Paths/get filename (into-array String nil)))

(defn relativize
  "Get the relative path from `a` to `b`"
  [a b]
  (str (.relativize (filename->path a) (filename->path b))))

(defn src-file?
  "Is the file a Clojure(Script) file?"
  [filename]
  (re-find #"\.clj(s|c)$" filename))

(defn normalize-sym
  "Useful for macros - ensure the symbol is quoted, like a symbol literal."
  [x]
  (if (symbol? x) (cons 'quote [(symbol (.replace (name x) "'" ""))]) x))

(defn ns-from-dir
  "Given a directory, return a list of the namespaces given by files within it."
  [^String dir]
  (->> (file-seq (File. dir))
       (map #(.getPath %))
       (filter src-file?)
       (map #(file->ns (relativize dir %)))))

(defn refine-namespaces [fs namespaces exclusions]
  (if (and (seq namespaces) (every? symbol? namespaces))
    namespaces
    (let [regexes (map ns-regex namespaces)
          exclude (map ns-regex exclusions)]
      (->> (boot/input-dirs fs)
           (mapv #(.getPath %))
           (into #{} (mapcat ns-from-dir))
           (filter (fn [ns]
                     (let [s (str ns)]
                       (and (or (and (empty? regexes) (re-find #"-test\z" s))
                                (some #(re-find % s) regexes))
                            (not (some #(re-find % s) exclude))))))))))

(defn cljs-files
  "Given a fileset, return a list of all the ClojureScript source files."
  [fileset]
  (->> fileset
       boot/input-files
       (boot/by-ext [".cljs" ".cljc" ".cljs.edn"])
       (sort-by :path)))

(defn find-path
  "Fine most recent filesystem file in fileset with given logical path."
  [fileset filename]
  (some->>
    (boot/output-files fileset)
    (filter (comp #{filename} :path))
    (sort-by :time)
    last ^File boot/tmp-file .getPath))

(defn build-cljs-opts
  "Augment cljs-opts with boot determined values"
  [base output-to output-dir]
  (merge {:asset-path (.getName (io/file output-dir))}
         base
         {:output-to output-to :output-dir output-dir}))

(defn combine-cljs-opts
  "Augment cljs-opts with values determined by other arguments"
  [cljs-opts optimizations js-env]
  (merge {:optimizations optimizations}
         (when (= :node js-env) {:target :nodejs, :hashbang false})
         cljs-opts))
