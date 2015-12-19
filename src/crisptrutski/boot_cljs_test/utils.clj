(ns crisptrutski.boot-cljs-test.utils
  (:require
   [boot.pod :as pod]
   [clojure.string :as str]
   [boot.core :as core])
  (:import
   [java.io File]
   [java.nio.file Paths]))

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

(defn ns->cljs-path
  "Determine filename from namespace"
  [ns]
  (-> (str ns)
      (str/replace "-" "_")
      (str/replace "." "/")
      (str ".cljs")))

(defn filename->path
  "A sane constructor `java.nio.file.Path` instancees."
  [filename]
  ;; Workaround for bad time with types (can't hint (Paths/get ^String))
  (Paths/get filename (into-array String nil)))

(defn relativize
  "Get the relative path from `a` to `b`"
  [a b]
  (str (.relativize (filename->path a)
                    (filename->path b))))

(defn src-file?
  "Is the file a Clojure(Script) file?"
  [filename]
  (re-find #"\.clj.?$" filename))

(defn normalize-sym
  "Useful for macros - ensure the symbol is quoted, like a symbol literal."
  [x]
  (if (symbol? x) (cons 'quote [(symbol (.replace (name x) "'" ""))]) x))

(defn ns-from-dir
  "Given a directory, return a list of the namespaces given by files within it."
  [dir]
  (->> (file-seq (File. dir))
       (map #(.getPath %))
       (filter src-file?)
       (map #(file->ns (relativize dir %)))))

(defn cljs-files
  "Given a fileset, return a list of all the ClojureScript source files."
  [fileset]
  (->> fileset
       core/input-files
       (core/by-ext [".cljs" ".cljc"])
       (sort-by :path)))

(defn wrap-task
  "Apply boot task with dynamically calculated arguments."
  [task-fn args-fn]
  (fn [next]
    (fn [fileset]
      (let [args (args-fn fileset)
            task (apply task-fn (mapcat identity args))
            func (task next)]
        (func fileset)))))
