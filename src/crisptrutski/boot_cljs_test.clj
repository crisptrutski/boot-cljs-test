(ns crisptrutski.boot-cljs-test
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [boot.task.built-in :as b]
            [boot.core :as core :refer [deftask]]
            [boot.pod :as pod]
            [boot.util :refer [info dbug warn fail]]))

(defmacro ^:private r
  [sym]
  `(do (require '~(symbol (namespace sym))) (resolve '~sym)))

(def deps
  {:adzerk/boot-cljs "1.7.48-SNAPSHOT"
   :doo              "0.1.4"})

(defn- filter-deps [keys]
  (let [dependencies (mapv #(vector (symbol (subs (str %) 1)) (deps %)) keys)]
    (remove pod/dependency-loaded? dependencies)))

(defn ensure-deps! [keys]
  (core/set-env! :dependencies #(into % (filter-deps keys))))

(def default-js-env   :phantom)
(def default-suite-ns 'clj-test.suite)
(def default-output   "output.js")

(defn- ns->cljs-path [ns]
  (-> (str ns)
      (str/replace "-" "_")
      (str/replace "." "/")
      (str ".cljs")))

(defn- gen-suite-ns
  "Generate source-code for default test suite."
  [ns sources test-namespaces]
  (let [ns-spec `(~'ns ~ns (:require [doo.runner :refer-macros [~'doo-tests ~'doo-all-tests]]
                                     ~@(mapv vector sources)))
        run-exp (if (seq test-namespaces)
                  `(~'doo-tests ~test-namespaces)
                  '(doo-all-tests))]
    (->> [ns-spec run-exp]
         (map #(with-out-str (clojure.pprint/pprint %)))
         (str/join "\n" ))))

(defn- cljs-files
  [fileset]
  (->> fileset core/input-files (core/by-ext [".cljs" ".cljc"]) (sort-by :path)))

(defn add-suite-ns!
  "Add test suite bootstrap script to fileset."
  [fileset tmp-main suite-ns test-namespaces]
  (ensure-deps! [:adzerk/boot-cljs])
  (let [out-main (ns->cljs-path suite-ns)
        out-file (doto (io/file tmp-main out-main) io/make-parents)
        cljs     (cljs-files fileset)]
    (info "Writing %s...\n" (.getName out-file))
    (spit out-file (gen-suite-ns suite-ns
                                 (mapv (comp symbol (r adzerk.boot-cljs.util/path->ns)
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
  (let [js-env     (or js-env default-js-env)
        out-file   (or out-file default-output)]
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
          (let [{:keys [exit] :as result} ((r doo.core/run-script)
                                           js-env
                                           path)]
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
        cljs-opts     (merge {:main suite-ns, :optimizations optimizations}
                             (when (= :node js-env) {:target :nodejs, :hashbang false})
                             cljs-opts)]
    (when (and (= :none optimizations)
               (= :rhino js-env))
      (fail "Combination of :rhino and :none is not currently supported.\n")
      (System/exit 1))
    (comp (prep-cljs-tests :out-file out-file
                           :namespaces namespaces
                           :suite-ns suite-ns)
          ((r adzerk.boot-cljs/cljs)
           :ids              #{out-id}
           :compiler-options cljs-opts)
          (run-cljs-tests :out-file out-file
                          :cljs-opts cljs-opts
                          :js-env js-env
                          :exit? exit?))))
