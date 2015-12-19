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

;; speculative helpers

(deftask testing
  "Add default test location to :source-paths"
  []
  (core/set-env! :source-paths #(conj % "test"))
  identity)

(defn test-ns? [sym]
  (re-find #"-test$" (name sym)))

(defn ns-from-dirs [dirs]
  (into #{} (mapcat u/ns-from-dir) dirs))

(defn- compute-ns [dirs]
  (filter test-ns? (ns-from-dirs dirs)))

(defn with-ns
  "Compute test :namespaces dynamically"
  ([task-fn]
   (with-ns task-fn nil))
  ([task-fn opts]
   (with-ns task-fn opts ["test"]))
  ([task-fn opts dirs]
   (u/wrap-task task-fn (fn [_] (assoc opts :namespaces (compute-ns dirs))))))

;; core

(defn ensure-deps! [keys]
  (core/set-env! :dependencies #(into % (u/filter-deps keys deps))))

(defn- gen-suite-ns
  "Generate source-code for default test suite."
  [ns sources test-namespaces]
  (let [ns-spec `(~'ns ~ns (:require [doo.runner :refer-macros [~'doo-tests ~'doo-all-tests]]
                                     ~@(mapv vector sources)))
        run-exp (if (seq test-namespaces)
                  `(~'doo-tests ~@(map u/normalize-sym test-namespaces))
                  '(doo-all-tests))]
    (->> [ns-spec run-exp]
         (map #(with-out-str (clojure.pprint/pprint %)))
         (str/join "\n" ))))

(defn add-suite-ns!
  "Add test suite bootstrap script to fileset."
  [fileset tmp-main suite-ns test-namespaces]
  (ensure-deps! [:adzerk/boot-cljs])
  (let [out-main (u/ns->cljs-path suite-ns)
        out-file (doto (io/file tmp-main out-main) io/make-parents)
        cljs     (u/cljs-files fileset)]
    (info "Writing %s...\n" (.getName out-file))
    (spit out-file (gen-suite-ns suite-ns
                                 (mapv (comp symbol (u/r adzerk.boot-cljs.util/path->ns)
                                             core/tmp-path)
                                       cljs)
                                 test-namespaces))
    (-> fileset (core/add-source tmp-main) core/commit!)))

(deftask prep-cljs-tests
  "Prepare fileset to compile main entry point for the test suite."
  [o out-file   VAL str    "Output file for test script."
   n namespaces NS  #{sym} "Namespaces whose tests will be run. All tests will be run if
                            ommitted."
   s suite-ns   NS  sym    "Test entry point. If this is not provided, a namespace will be
                            generated."]
  (let [out-file (or out-file default-output)
        suite-ns (or suite-ns default-suite-ns)
        tmp-main (core/tmp-dir!)]
    (core/with-pre-wrap fileset
      (core/empty-dir! tmp-main)
      (add-suite-ns! fileset tmp-main suite-ns namespaces))))

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
                {:keys [exit] :as result} ((u/r doo.core/run-script)
                                           js-env
                                           {:output-to path}
                                           {:exec-dir dir})]
            (when (pos? exit) (reset! failures? true))
            (when exit? (System/exit exit))
            (next-task fileset))
          (do (warn (str "Test script not found: " out-file))
              (when exit? (System/exit 1))))))))

(deftask test-cljs
  "Run cljs.test tests via the engine of your choice.

   The --namespaces option specifies the namespaces to test. The default is to
   run tests in all namespaces found in the project."
  [e js-env        VAL   kw     "The environment to run tests within, eg. slimer, phantom, node,
                                 or rhino"
   n namespaces    NS    #{sym} "Namespaces whose tests will be run. All tests will be run if
                                 ommitted."
   d test-dirs     STRS  #{str} "Test namespaces ending in -test, found in given directories"
   t conventions?        bool   "Opinionated mode: :test-dirs is \"test\". Sets up :src-paths."
   s suite-ns      NS    sym    "Test entry point. If this is not provided, a namespace will be
                                 generated."
   O optimizations LEVEL kw     "The optimization level."
   o out-file      VAL   str    "Output file for test script."
   c cljs-opts     VAL   code   "Compiler options for CLJS"
   x exit?               bool   "Exit immediately with reporter's exit code."]
  (ensure-deps! [:doo :adzerk/boot-cljs])
  (let [out-file      (or out-file default-output)
        out-id        (str/replace out-file #"\.js$" "")
        optimizations (or optimizations :none)
        js-env        (or js-env default-js-env)
        suite-ns      (or suite-ns default-suite-ns)
        namespaces    (or namespaces (compute-ns (if conventions? ["test"] test-dirs)))
        cljs-opts     (merge {:main suite-ns, :optimizations optimizations}
                             (when (= :node js-env) {:target :nodejs, :hashbang false})
                             cljs-opts)]
    (if (and (= :none optimizations)
             (= :rhino js-env))
      (do
        (fail "Combination of :rhino and :none is not currently supported.\n")
        (if exit?
          (System/exit 1)
          identity))
      (comp (if conventions? (testing) identity)
            (prep-cljs-tests :out-file out-file
                             :namespaces namespaces
                             :suite-ns suite-ns)
            ((u/r adzerk.boot-cljs/cljs)
             :ids              #{out-id}
             :compiler-options cljs-opts)
            (run-cljs-tests :out-file out-file
                            :cljs-opts cljs-opts
                            :js-env js-env
                            :exit? exit?)))))

(deftask exit!
  "Exit with the appropriate error code"
  []
  (fn [_]
    (fn [_]
      (System/exit (if @failures? 1 0)))))
