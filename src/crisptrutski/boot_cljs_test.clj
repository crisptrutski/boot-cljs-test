(ns crisptrutski.boot-cljs-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [boot.core :as core :refer [deftask]]
   [boot.util :refer [info dbug warn fail]]
   [crisptrutski.boot-cljs-test.utils :as u])
  (:import
   [java.io File]))

(def deps
  {:adzerk/boot-cljs "1.7.170-3"
   :doo              "0.1.7-SNAPSHOT"})

(def default-js-env   :phantom)
(def default-suite-ns 'clj-test.suite)
(def default-output   "output.js")

;; state

(def failures? (atom false))

;; core

(defn ensure-deps! [keys]
  (core/set-env! :dependencies #(into % (u/filter-deps keys deps))))

(defn- gen-suite-ns
  "Generate source-code for default test suite."
  [ns test-namespaces]
  (let [ns-spec `(~'ns ~ns (:require [doo.runner :refer-macros [~'doo-tests ~'doo-all-tests]]
                                     ~@(mapv vector test-namespaces)))
        run-exp `(~'doo-tests ~@(map u/normalize-sym test-namespaces))]
    (->> [ns-spec run-exp]
         (map #(with-out-str (clojure.pprint/pprint %)))
         (str/join "\n" ))))

(defn add-suite-ns!
  "Add test suite bootstrap script to fileset."
  [fileset tmp-main suite-ns test-namespaces]
  (ensure-deps! [:adzerk/boot-cljs])
  (let [out-main (u/ns->cljs-path suite-ns)
        out-file (doto (io/file tmp-main out-main) io/make-parents)
        out-path (u/relativize (.getPath tmp-main) (.getPath out-file))
        cljs     (u/cljs-files fileset)]
    (if (contains? (into #{} (map core/tmp-path) cljs) out-path)
      (info "Using %s...\n" out-path)
      (do (info "Writing %s...\n" out-path)
          (spit out-file (gen-suite-ns suite-ns test-namespaces))))))

(deftask prep-cljs-tests
  "Prepare fileset to compile main entry point for the test suite."
  [o out-file   VAL str       "Output file for test script."
   n namespaces NS ^:! #{str} "Namespaces whose tests will be run. All tests will be run if
                               ommitted.
                               Use symbols for literals.
                               Regexes are also supported.
                               Strings will be coerced to entire regexes."
   s suite-ns   NS  sym       "Test entry point. If this is not provided, a namespace will be
                               generated."]
  (let [out-file (or out-file default-output)
        out-id   (str/replace out-file #"\.js$" "")
        suite-ns (or suite-ns default-suite-ns)
        tmp-main (core/tmp-dir!)]
    (core/with-pre-wrap fileset
      (let [namespaces (u/refine-namespaces fileset namespaces)]
        (core/empty-dir! tmp-main)
        (info "Writing %s...\n" (str out-id ".cljs.edn"))
        (spit (doto (io/file tmp-main (str out-id ".cljs.edn")) io/make-parents)
              (pr-str {:require [suite-ns]}))
        (add-suite-ns! fileset tmp-main suite-ns namespaces)
        (-> fileset (core/add-source tmp-main) core/commit!)))))

(deftask run-cljs-tests
  "Execute test reporter on compiled tests"
  [o out-file   VAL str  "Output file for test script."
   e js-env     VAL kw   "The environment to run tests within, eg. slimer, phantom, node,
                          or rhino."
   c cljs-opts  VAL code "Compiler options for CLJS"
   x exit?          bool "Exit immediately with reporter's exit code."]
  (let [js-env   (or js-env default-js-env)
        out-file (or out-file default-output)]
    (ensure-deps! [:doo])
    ;;((r doo.core/assert-compiler-opts) js-env {:output-to out-file})
    (fn [next-task]
      (fn [fileset]
        (info "Running cljs tests...")
        (if-let [path (some->> (core/output-files fileset)
                               (filter (comp #{out-file} :path))
                               (sort-by :time)
                               (last)
                               (core/tmp-file)
                               (.getPath))]
          (let [dir (.getParentFile (File. path))
                ;; TODO: could infer :asset-path and :main too
                ;; TODO: perhaps better to get boot-cljs to share this parsing logic
                cljs (merge cljs-opts {:output-to path, :output-dir (str/replace path #".js\z" ".out")})
                opts {:exec-dir dir}
                {:keys [exit] :as result}
                ((u/r doo.core/run-script) js-env cljs opts)]
            (when (pos? exit) (reset! failures? true))
            (when exit? (System/exit exit))
            (next-task fileset))
          (do (warn (str "Test script not found: " out-file))
              (when exit? (System/exit 1))))))))

(defn- capture-fileset [fs-atom]
  (fn [next-task]
    (fn [fileset]
      (reset! fs-atom fileset)
      (next-task fileset))))

(defn- return-fileset [fs-atom]
  (fn [next-task]
    (fn [_]
      (let [fileset @fs-atom]
        (core/commit! fileset)
        (next-task fileset)))))

(deftask test-cljs
  "Run cljs.test tests via the engine of your choice.

   The --namespaces option specifies the namespaces to test. The default is to
   run tests in all namespaces found in the project."
  [e js-env        VAL   kw      "The environment to run tests within, eg. slimer, phantom, node,
                                  or rhino"
   n namespaces    NS ^:! #{str} "Namespaces whose tests will be run. All tests will be run if
                                  ommitted."
   s suite-ns      NS    sym     "Test entry point. If this is not provided, a namespace will be
                                  generated."
   O optimizations LEVEL kw      "The optimization level."
   o out-file      VAL   str     "Output file for test script."
   c cljs-opts     VAL   code    "Compiler options for CLJS"
   u update-fs?          bool    "Only if this is set does the next task's filset include
                                  and generated or compiled cljs from the tests."
   x exit?               bool    "Exit immediately with reporter's exit code."]
  (ensure-deps! [:doo :adzerk/boot-cljs])
  (let [out-file      (or out-file default-output)
        out-id        (str/replace out-file #"\.js$" "")
        optimizations (or optimizations :none)
        js-env        (or js-env default-js-env)
        suite-ns      (or suite-ns default-suite-ns)
        cljs-opts     (merge {:main suite-ns, :optimizations optimizations}
                             (when (= :node js-env) {:target :nodejs, :hashbang false})
                             cljs-opts)
        capture-atom  (atom nil)
        ->fs          (if update-fs? identity (capture-fileset capture-atom))
        fs->          (if update-fs? identity (return-fileset capture-atom))]
    (if (and (= :none optimizations)
             (= :rhino js-env))
      (do
        (fail "Combination of :rhino and :none is not currently supported.\n")
        (if exit?
          (System/exit 1)
          identity))
      (comp ->fs
            (prep-cljs-tests :out-file out-file
                             :namespaces namespaces
                             :suite-ns suite-ns)
            ((u/r adzerk.boot-cljs/cljs)
             :ids              #{out-id}
             :compiler-options cljs-opts)
            (run-cljs-tests :out-file out-file
                            :cljs-opts cljs-opts
                            :js-env js-env
                            :exit? exit?)
            fs->))))

(deftask exit!
  "Exit with the appropriate error code"
  []
  (fn [_]
    (fn [_]
      (System/exit (if @failures? 1 0)))))
