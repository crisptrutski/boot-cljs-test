(ns crisptrutski.boot-cljs-test
  (:require
    [boot.core :as boot :refer [deftask]]
    [boot.file :as file]
    [boot.util :refer [info dbug warn fail]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [crisptrutski.boot-cljs-test.utils :as u]
    [crisptrutski.boot-error.core :as err])
  (:import
    [java.io File]))

(def deps
  {:adzerk/boot-cljs "1.7.228-2"
   :org.clojure/clojurescript "1.7.228"
   :doo "0.1.7"})

(def default-js-env :phantom)
(def default-ids ["cljs_test/generated_test_suite"])

;; core

(defn no-op [& _])

(defn- scope-as
  "Modify dependency co-ords to have particular scope.
   Assumes not currently scoped"
  [scope deps]
  (for [co-ords deps]
    (conj co-ords :scope scope)))

(defn ensure-deps! [keys]
  (when-let [deps (seq (u/filter-deps keys deps))]
    (warn "Adding: %s to :dependencies\n" deps)
    (boot/merge-env! :dependencies (scope-as "test" deps))))

(defn validate-cljs-opts! [js-env cljs-opts]
  (ensure-deps! [:doo])
  ((u/r doo.core/assert-compiler-opts)
    js-env
    (assoc cljs-opts
      :output-to "placeholder"
      :output-dir "placeholder"
      :assert-path "placeholder")))

(defn- gen-suite-ns
  "Generate source-code for default test suite."
  [ns namespaces]
  (let [ns-spec `(~'ns ~ns (:require [doo.runner :refer-macros [~'doo-tests]] ~@(mapv vector namespaces)))
        run-exp `(~'doo-tests ~@(map u/normalize-sym namespaces))]
    (->> [ns-spec '(enable-console-print!) run-exp]
         (map #(with-out-str (clojure.pprint/pprint %)))
         (str/join "\n"))))

(defn add-suite-ns!
  "Add test suite bootstrap script to fileset."
  [fileset tmp-main id namespaces verbosity]
  (let [relative #(u/relativize (.getPath tmp-main) (.getPath %))
        out-main (str id ".cljs")
        src-file (doto (io/file tmp-main out-main) io/make-parents)
        edn-file (io/file tmp-main (str out-main ".edn"))
        src-path (relative src-file)
        edn-path (relative edn-file)
        exists? (into #{} (map boot/tmp-path) (u/cljs-files fileset))
        suite? (or (exists? src-path) (exists? (str/replace src-path ".cljs" ".cljc")))
        edn? (exists? edn-path)
        edn (when edn? (read-string (slurp (boot/tmp-file (boot/tmp-get fileset edn-path)))))
        namespaces (if edn (filter (set (:require edn)) namespaces) namespaces)
        suite-ns (u/file->ns out-main)
        info (if (> verbosity 1) info no-op)]
    (if suite?
      (info "Using %s...\n" src-path)
      (do
        (info "Writing %s...\n" src-path)
        (spit src-file (gen-suite-ns suite-ns namespaces))))
    (if edn?
      ;; ensure that .cljs file is required by .cljs.edn, if it's being created?
      (if suite?
        (info "Using %s...\n" edn-path)
        (do (info "Updating %s...\n" edn-path)
            (spit edn-file (update edn :require (fn [xs] (when-not (some #{suite-ns} xs)
                                                           (conj xs suite-ns)))))))
      (do (info "Writing %s...\n" edn-path)
          (spit edn-file {:require [suite-ns]})))
    (if (and suite? edn?)
      fileset
      (boot/commit! (boot/add-source fileset tmp-main)))))

(deftask prep-cljs-tests
  "Prepare fileset to compile main entry point for the test suite."
  [n namespaces NS ^:! #{str} "Namespaces to run, supports regexes. If omitted tries \"*-test\" then \"*\"."
   e exclusions NS ^:! #{str} "Namespaces to exclude, supports regexes."
   i id         ID     str    "Test runner id. Generates config if not found."
   v verbosity  VAL    int    "Log level, from 0 to 3"]
  (let [tmp-main (boot/tmp-dir!)
        verbosity (or verbosity @boot.util/*verbosity* 1)]
    (boot/with-pre-wrap fileset
      (let [namespaces (u/refine-namespaces fileset namespaces exclusions)]
        (boot/empty-dir! tmp-main)
        (add-suite-ns! fileset tmp-main id namespaces verbosity)))))

(defn- info? [verbosity & args]
  (when (> verbosity 1) (apply info args)))

(defn link-resources! [dir]
  (doseq [path (conj (boot/get-env :resource-paths) "node_modules")
          :let [f (io/file path)]
          :when (.exists f)]
    (file/sym-link f (doto (io/file dir path)
                       (io/make-parents)))))

(defn run-tests! [ids js-env cljs-opts v exit? doo-opts doo-installed? verbosity fileset]
  (err/with-errors!
    (info? v "Running cljs tests...\n")
    ((u/r doo.core/print-envs) js-env)
    (doseq [id ids]
      (when (> (count ids) 1) (info? v "• %s\n" id))
      (let [filename (str id ".js")
            karma? ((u/r doo.karma/env?) js-env)
            output-to (u/find-path fileset filename)
            output-dir (when output-to (str/replace output-to #"\.js\z" ".out"))
            cljs-opts (when output-to (u/build-cljs-opts cljs-opts output-to output-dir))
            err (if exit?
                  #(throw (RuntimeException. ^String (:out % %)))
                  #(err/track-error! (if (map? %) % {:exit 1 :out "" :err %})))]
        (when output-to
          ((u/r doo.core/assert-compiler-opts) js-env cljs-opts))
        (if-not output-to
          (do (warn "Test script not found: %s\n" filename)
              (swap! boot/*warnings* inc)
              (err (format "Test script not found: %s" filename)))
          (let [dir (.getParentFile (File. ^String output-to))
                doo-opts (merge
                           {:verbose (>= verbosity 1)
                            :debug (> verbosity 2)}
                           doo-opts
                           {:exec-dir dir})
                _ (link-resources! dir)
                _ (when karma?
                    (when-not @doo-installed?
                      (reset! doo-installed? true)
                      ((u/r doo.core/install!) [js-env] cljs-opts doo-opts)
                      (Thread/sleep 1000)))]
            (if karma?
              ((u/r doo.core/karma-run!) doo-opts)
              (let [{:keys [exit] :as result}
                    ((u/r doo.core/run-script) js-env cljs-opts doo-opts)]
                (when (pos? exit)
                  (err result))))))))
    fileset))

(deftask run-cljs-tests
  "Execute test reporter on compiled tests"
  [i ids       IDS  [str] "Test runner ids. Generates each config if not found."
   j js-env    VAL  kw    "Environment to execute within, eg. slimer, phantom, ..."
   c cljs-opts OPTS edn   "Options to pass on to CLJS compiler."
   v verbosity VAL  int   "Log level, from 0 to 3"
   d doo-opts  VAL  code  "Options to pass on to Doo."
   x exit?          bool  "Throw exception on error or inability to run tests."]
  (ensure-deps! [:doo])
  (let [js-env (or js-env default-js-env)
        ids (if (seq ids) ids default-ids)
        verbosity (or verbosity @boot.util/*verbosity*)
        doo-installed? (atom false)]
    (validate-cljs-opts! js-env cljs-opts)
    (fn [next-task]
      (fn [fileset]
        (next-task (run-tests! ids js-env cljs-opts verbosity exit? doo-opts doo-installed? verbosity fileset))))))

(deftask clear-errors
  "Clear any test errors from the fileset."
  []
  (fn [handler]
    (fn [fs]
      (handler (err/clear-errors fs)))))

(deftask report-errors!
  "Throw exception if any test errors have been tracked against the fileset."
  []
  (fn [handler]
    (fn [fs]
      (when (seq (err/get-errors fs))
        (throw (RuntimeException. "Some tests failed or errored")))
      (handler (err/clear-errors fs)))))

(deftask fs-snapshot
  "Embed snapshot of fileset within itself"
  []
  (fn [handler] (fn [fs] (handler (vary-meta fs assoc ::snapshot fs)))))

(deftask fs-restore
  "Rollback to embedded snapshot, if it exists"
  [k keep-errors? bool "Retain memory of test errors after rollback."]
  (fn [handler]
    (fn [fs]
      (let [old-fs (::snapshot (meta fs))]
        (if old-fs
          (boot/commit! old-fs)
          (warn "Fileset snapshot not found\n"))
        (handler
          (if keep-errors?
            (err/track-errors (or old-fs fs) (err/get-errors fs))
            (or old-fs fs)))))))

(defn multi-comp
  "Like to `clojure.core/comp`, but support nils and collections of fns."
  [& fns]
  (apply comp (remove nil? (flatten fns))))

(deftask test-cljs
  "Run cljs.test tests via the engine of your choice.

   The --namespaces option specifies the namespaces to test. The default is to
   run tests in all namespaces found in the project."
  [j js-env        VAL    kw     "Environment to execute within, eg. slimer, phantom, ..."
   n namespaces    NS ^:! #{str} "Namespaces to run, supports regexes. If omitted tries \"*-test\" then \"*\"."
   e exclusions    NS ^:! #{str} "Namespaces to exclude, supports regexes."
   i ids           IDS    [str]  "Test runner ids. Generates each config if not found."
   c cljs-opts     OPTS   edn    "Options to pass on to CLJS compiler."
   O optimizations LEVEL  kw     "Optimization level for CLJS compiler, defaults to :none."
   d doo-opts      VAL    code   "Options to pass on to Doo."
   u update-fs?           bool   "Skip fileset rollback before running next task.
                                  By default fileset is rolled back to support additional cljs suites, clean JARs, etc."
   x exit?                bool   "Throw exception on error or inability to run tests."
   k keep-errors?         bool   "Retain memory of test errors after rollback."
   v verbosity     VAL    int    "Log level, from 0 to 3"
   o out-file      VAL    str    "DEPRECATED Output file for test script."]
  (ensure-deps! [:org.clojure/clojurescript :adzerk/boot-cljs :doo])
  (when out-file
    (warn "[boot-cljs] :out-file is deprecated, please use :ids\n")
    (swap! boot/*warnings* inc))
  (let [verbosity (or verbosity @boot.util/*verbosity*)
        ids (cond
              (seq ids) ids
              out-file (str/replace out-file #"\.js\z" "")
              :else default-ids)
        ids (map #(str/replace % "/" File/separator) ids)
        ids (vec (distinct ids))
        optimizations (or optimizations :none)
        js-env (or js-env default-js-env)
        cljs-opts (u/combine-cljs-opts cljs-opts optimizations js-env)
        ;; karma process is external, so coordinating rollback is not feasible.
        update-fs? (or update-fs? ((u/r doo.karma/env?) js-env))]
    (validate-cljs-opts! js-env cljs-opts)
    (multi-comp
      (when-not update-fs? (fs-snapshot))
      (for [id ids]
        (prep-cljs-tests
          :id id
          :namespaces namespaces
          :exclusions exclusions))
      ((u/r adzerk.boot-cljs/cljs)
        :ids (set ids)
        :compiler-options cljs-opts)
      (run-cljs-tests
        :ids ids
        :cljs-opts cljs-opts
        :doo-opts doo-opts
        :js-env js-env
        :exit? exit?
        :verbosity verbosity)
      (when exit? (report-errors!))
      (when-not update-fs? (fs-restore :keep-errors? keep-errors?)))))
