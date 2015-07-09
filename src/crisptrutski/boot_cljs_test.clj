(ns crisptrutski.boot-cljs-test
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [boot.core :as core :refer [deftask]]
            [boot.util :refer [info dbug warn]]
            ;; TODO: load doo and boot-cljs in pods, in appropriate tasks
            ;; NOTE: worth logic to skip pod if dependency detected?
            [adzerk.boot-cljs :as cljs]
            [adzerk.boot-cljs.util  :as util]
            [adzerk.boot-cljs.js-deps :as deps]
            [doo.core :as doo]))

(def default-js-env :phantom)

(def default-suite-ns 'clj-test.suite)

(def default-output "output.js")

(defn- ns->path [ns]
  (-> (str ns)
      (str/replace "-" "_")
      (str/replace "." "/")))

(defn- ns->cljs-path [ns]
  (-> ns ns->path (str ".cljs")))

(defn- ns->edn-path [ns]
  (-> ns ns->path deps/add-extension))

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

(defn add-suite-ns!
  "Add test suite bootstrap script to fileset."
  [fileset tmp-main suite-ns test-namespaces]
  ;; TODO: check that ns isn't already in the fileset
  (let [out-main (ns->cljs-path suite-ns)
        out-file  (doto (io/file tmp-main out-main) io/make-parents)
        {:keys [cljs]} (deps/scan-fileset fileset)]
    (info "Writing %s...\n" (.getName out-file))
    (spit out-file (gen-suite-ns suite-ns
                                  (mapv (comp symbol util/path->ns core/tmp-path) cljs)
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
   x exit?          bool "Exit immediately with reporter's exit code."]
  (let [js-env     (or js-env default-js-env)
        out-file   (or out-file default-output)]
    (fn [next-task]
      (fn [fileset]
        (let [file (->> (core/output-files fileset)
                        (filter (comp #{out-file} :path))
                        (sort-by :time)
                        (last))
              path (.getPath (core/tmp-file file))]
          ;; TODO: use a pod for this task, to load `doo`
          (let [{:keys [exit] :as result} (doo/run-script js-env path)]
            (when exit? (System/exit exit))
            (next-task fileset)))))))

(deftask test-cljs
  "Run cljs.test tests via the engine of your choice.

   The --namespaces option specifies the namespaces to test. The default is to
   run tests in all namespaces found in the project."
  [e js-env     VAL kw     "The environment to run tests within, eg. slimer, phantom, node,
                            or rhino"
   n namespaces NS  #{sym} "Namespaces whose tests will be run. All tests will be run if
                            ommitted."
   s suite-ns   NS  sym    "Test entry point. If this is not provided, a namespace will be
                            generated."
   o out-file   VAL str    "Output file for test script."
   x exit?          bool   "Exit immediately with reporter's exit code."]
  (fn [next-task]
    ((comp (prep-cljs-tests :out-file out-file :namespaces namespaces :suite-ns suite-ns)
           (cljs/cljs :optimizations :whitespace
                      :compiler-options {:output-to (or out-file default-output)})
           (run-cljs-tests :out-file out-file
                           :js-env js-env
                           :exit exit?))
     next-task)))
