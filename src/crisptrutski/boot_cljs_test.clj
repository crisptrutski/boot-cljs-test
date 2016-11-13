(ns crisptrutski.boot-cljs-test
  (:require
    [boot.core :as core :refer [deftask]]
    [boot.util :refer [info dbug warn fail]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [crisptrutski.boot-cljs-test.utils :as u]
    [crisptrutski.boot-error.core :as err])
  (:import
    [java.io File]))

(def deps
  {:adzerk/boot-cljs "1.9.293"
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
    (warn "Adding: %s to :dependencies" deps)
    (core/merge-env! :dependencies (scope-as "test" deps))))

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
        exists? (into #{} (map core/tmp-path) (u/cljs-files fileset))
        suite? (or (exists? src-path) (exists? (str/replace src-path ".cljs" ".cljc")))
        edn? (exists? edn-path)
        edn (when edn? (read-string (slurp (core/tmp-file (core/tmp-get fileset edn-path)))))
        namespaces (if edn (filter (set (:require edn)) namespaces) namespaces)
        suite-ns (u/file->ns out-main)
        info (if (pos? verbosity) info no-op)]
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
      (core/commit! (core/add-source fileset tmp-main)))))

(deftask prep-cljs-tests
  "Prepare fileset to compile main entry point for the test suite."
  [n namespaces NS ^:! #{str} "Namespaces whose tests will be run. All tests will be run if
                               ommitted.
                               Use symbols for literals.
                               Regexes are also supported.
                               Strings will be coerced to entire regexes."
   e exclusions NS ^:! #{str} "Namespaces or namesaces patterns to exclude."
   v verbosity  VAL    int    "Log level, from 1 to 3"
   i id VAL str "TODO: WRITE ME"]
  (let [tmp-main (core/tmp-dir!)
        verbosity (or verbosity @boot.util/*verbosity* 1)]
    (core/with-pre-wrap fileset
      (let [namespaces (u/refine-namespaces fileset namespaces exclusions)]
        (core/empty-dir! tmp-main)
        (add-suite-ns! fileset tmp-main id namespaces verbosity)))))

(defn- info? [verbosity & args]
  (when (pos? verbosity) (apply info args)))

(defn run-cljs-tests! [ids js-env cljs-opts v exit? doo-opts verbosity fileset]
  (err/with-errors!
    (info? v "Running cljs tests...\n")
    ((u/r doo.core/print-envs) js-env)
    (doseq [id ids]
      (when (> (count ids) 1) (info? v "• %s\n" id))
      (let [filename (str id ".js")
            output-to (u/find-path fileset filename)
            output-dir (str/replace output-to #"\.js\z" ".out")
            cljs-opts (u/build-cljs-opts cljs-opts output-to output-dir)]
        ((u/r doo.core/assert-compiler-opts) js-env cljs-opts)
        (if-not output-to
          (do (warn "Test script not found: %s\n" filename)
              (err/track-error! {:exit 1 :out "" :err (format "Test script not found: %s" filename)})
              (when exit? (System/exit 1)))
          (let [dir (.getParentFile (File. ^String output-to))
                {:keys [exit] :as result}
                ((u/r doo.core/run-script) js-env cljs-opts
                  (merge
                    doo-opts
                    {:exec-dir dir
                     :verbose (>= verbosity 1)
                     :debug (>= verbosity 2)}))]
            (when (pos? exit)
              (err/track-error! result)
              (when exit? (System/exit exit)))))))
    fileset))

(deftask run-cljs-tests
  "Execute test reporter on compiled tests"
  [i ids       IDS [str] ""
   j js-env    VAL kw    "Environment to execute within, eg. slimer, phantom, ..."
   c cljs-opts OPTS edn  "Options to pass to the Clojurescript compiler."
   v verbosity VAL int   "Log level"
   d doo-opts  VAL code  "Options for doo"
   x exit?         bool  "Exit process with runner's exit code on completion."]
  (ensure-deps! [:doo])
  (let [js-env (or js-env default-js-env)
        ids (if (seq ids) ids default-ids)
        verbosity (or verbosity @boot.util/*verbosity*)]
    (validate-cljs-opts! js-env cljs-opts)
    (fn [next-task]
      (fn [fileset]
        (next-task (run-cljs-tests! ids js-env cljs-opts verbosity exit? doo-opts verbosity fileset))))))

(defn -test-cljs
  [js-env namespaces exclusions optimizations ids out-file cljs-opts verbosity update-fs? exit?]
  (ensure-deps! [:adzerk/boot-cljs])
  (when out-file (warn "[boot-cljs] :out-file is deprecated, please use :ids\n"))
  (let [verbosity (or verbosity @boot.util/*verbosity*)
        ids (if (seq ids) ids (if out-file (str/replace out-file #"\.js\z" "") default-ids))
        optimizations (or optimizations :none)
        js-env (or js-env default-js-env)
        cljs-opts (u/combine-cljs-opts cljs-opts optimizations js-env)
        wrapper (if update-fs? identity u/wrap-fs-rolback)]
    (validate-cljs-opts! js-env cljs-opts)
    (wrapper
      (comp (reduce
              comp
              (for [id ids]
                (prep-cljs-tests
                  :id id
                  :namespaces namespaces
                  :exclusions exclusions)))
            ((u/r adzerk.boot-cljs/cljs)
              :ids (set ids)
              :compiler-options cljs-opts)
            (run-cljs-tests
              :ids (vec (distinct ids))
              :cljs-opts cljs-opts
              :js-env js-env
              :exit? exit?
              :verbosity verbosity)))))

(deftask test-cljs
  "Run cljs.test tests via the engine of your choice.

   The --namespaces option specifies the namespaces to test. The default is to
   run tests in all namespaces found in the project."
  [j js-env        VAL    kw     "Environment to execute within, eg. slimer, phantom, ..."
   n namespaces    NS ^:! #{str} "Namepsaces or namespace patterns to run.
                                  If omitted uses all namespaces ending in \"-test\"."
   e exclusions    NS ^:! #{str} "Namespaces or namesaces patterns to exclude."
   O optimizations LEVEL  kw     "The optimization level, defaults to :none."
   i ids           IDS    [str]  ""
   o out-file      VAL    str    "DEPRECATED Output file for test script."
   c cljs-opts     OPTS   edn    "Options to pass to the Clojurescript compiler."
   u update-fs?           bool   "Enable fileset changes to be passed on to next task.
                                  By default hides all effects for suite isolation."
   v verbosity     VAL    int    "Verbosity level"
   x exit? bool "Exit process with runner's exit code on completion."]
  (ensure-deps! [:adzerk/boot-cljs])
  (-test-cljs
    js-env namespaces exclusions optimizations ids out-file cljs-opts verbosity update-fs? exit?))

(deftask exit!
  "Exit with the appropriate error code"
  []
  (fn [_]
    (fn [fs]
      (let [errs (err/get-errors fs)]
        (System/exit (if (seq errs) 1 0))))))
