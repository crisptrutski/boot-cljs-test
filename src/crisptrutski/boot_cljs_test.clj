(ns crisptrutski.boot-cljs-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [boot.core :as core :refer [deftask]]
   [boot.util :refer [info dbug warn fail]]
   [crisptrutski.boot-cljs-test.utils :as u]
   [crisptrutski.boot-error.core :as err])
  (:import
   [java.io File]))

(def deps
  {:adzerk/boot-cljs "1.7.170-3"
   :doo              "0.1.7-SNAPSHOT"})

(def default-js-env :phantom)
(def default-ids    #{"cljs_test/suite"})

;; core

(defn ensure-deps! [keys]
  (core/set-env! :dependencies #(into % (u/filter-deps keys deps))))

(defn- gen-suite-ns
  "Generate source-code for default test suite."
  [ns namespaces]
  (let [ns-spec `(~'ns ~ns (:require [doo.runner :refer-macros [~'doo-tests]] ~@(mapv vector namespaces)))
        run-exp `(~'doo-tests ~@(map u/normalize-sym namespaces))]
    (->> [ns-spec '(enable-console-print!) run-exp]
         (map #(with-out-str (clojure.pprint/pprint %)))
         (str/join "\n" ))))

(defn add-suite-ns!
  "Add test suite bootstrap script to fileset."
  [fileset tmp-main id namespaces]
  (ensure-deps! [:adzerk/boot-cljs])
  (let [relative   #(u/relativize (.getPath tmp-main) (.getPath %))
        out-main   (str id ".cljs")
        src-file   (doto (io/file tmp-main out-main) io/make-parents)
        edn-file   (io/file tmp-main (str out-main ".edn"))
        src-path   (relative src-file)
        edn-path   (relative edn-file)
        exists?    (into #{} (map core/tmp-path) (u/cljs-files fileset))
        suite?     (or (exists? src-path) (exists? (str/replace src-path ".cljs" ".cljc")))
        edn?       (exists? edn-path)
        edn        (when edn? (read-string (slurp (core/tmp-file (core/tmp-get fileset edn-path)))))
        ;; subset namespaces to those directly required by .cljs.edn
        ;; TODO: better would be to use transitive requires, once we have the dep graph
        namespaces (if edn (filter (set (:require edn)) namespaces) namespaces)
        suite-ns   (u/file->ns out-main)]
    (if suite?
      (info "Using %s...\n" src-path)
      (do
        (info "Writing %s...\n" src-path)
        (spit src-file (gen-suite-ns suite-ns namespaces))))
    (if edn?
      ;; ensure that .cljs file is required by .cljs.edn, if it's being created.
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
   w exclusions NS ^:! #{str} "Namespaces or namesaces patterns to exclude."
   i id         VAL str       "TODO: WRITE ME"]
  (let [tmp-main (core/tmp-dir!)]
    (core/with-pre-wrap fileset
      (let [namespaces (u/refine-namespaces fileset namespaces exclusions)]
        (core/empty-dir! tmp-main)
        (add-suite-ns! fileset tmp-main id namespaces)))))

(deftask run-cljs-tests
  "Execute test reporter on compiled tests"
  ;; TODO: perhaps vector rather than set, so you can control the running order
  [i ids        IDS  #{str} ""
   e js-env     VAL  kw     "Environment to execute within, eg. slimer, phantom, ..."
   c cljs-opts  OPTS edn    "Options to pass to the Clojurescript compiler."
   d debug?          bool   "Enable logging to help with debugging."
   x exit?           bool   "Exit process with runner's exit code on completion."]
  (let [js-env (or js-env default-js-env)
        ids    (if (seq ids) ids default-ids)]
    (ensure-deps! [:doo])
    (fn [next-task]
      (fn [fileset]
        (next-task
          (err/track-errors
            (info "Running cljs tests...\n")
            ((u/r doo.core/print-envs) js-env)
            (doseq [id ids]
              (when (> (count ids) 1)
                (info "• %s\n" id))
              (let [file (str id ".js")
                    ;; TODO: perhaps use metadata on fileset from CLJS task rather
                    path (some->> (core/output-files fileset)
                                  (filter (comp #{file} :path))
                                  (sort-by :time)
                                  last
                                  core/tmp-file
                                  (#(.getPath ^File %)))
                    ;; TODO: could also infer :asset-path and :main, perhaps even get boot-cljs to
                    ;;       expose its own parsing logic
                    cljs (merge cljs-opts {:output-to path, :output-dir (str/replace path #".js\z" ".out")})]
                ;; TODO: perform this right at outset
                ;; That should be as early as possible, ie. in `test-cljs` or start if this function if
                ;; called directly. Note that some generated arguments, like :output-dir, would need
                ;; to be fudged.
                ((u/r doo.core/assert-compiler-opts) js-env cljs)
                (if-not path
                  (do (warn "Test script not found: %s\n" file)
                      (err/track-error! {:exit 1 :out ""  :err (format "Test script not found: %s" file)})
                      (when exit? (System/exit 1)))
                  (let [dir (.getParentFile (File. ^String path))
                        {:keys [exit] :as result}
                        ((u/r doo.core/run-script) js-env cljs {:exec-dir dir, :debug debug?})]
                    (when (pos? exit)
                      (err/track-error! result)
                      (when exit?
                        (System/exit exit)))))))
            fileset))))))

(deftask test-cljs
  "Run cljs.test tests via the engine of your choice.

   The --namespaces option specifies the namespaces to test. The default is to
   run tests in all namespaces found in the project."
  [e js-env        VAL   kw      "Environment to execute within, eg. slimer, phantom, ..."
   n namespaces    NS ^:! #{str} "Namepsaces or namespace patterns to run.
                                  If omitted uses all namespaces ending in \"-test\"."
   ;; TODO: support exclusions
   w exclusions    NS ^:! #{str} "Namespaces or namesaces patterns to exclude."
   O optimizations LEVEL kw      "The optimization level, defaults to :none."
   i ids           IDS   #{str}  ""
   o out-file      VAL   str     "DEPRECATED Output file for test script."
   c cljs-opts     OPTS  edn     "Options to pass to the Clojurescript compiler."
   d debug?              bool    "Enable logging to help with debugging."
   u update-fs?          bool    "Enable fileset changes to be passed on to next task.
                                  By default hides all effects for suite isolation."
   x exit?               bool    "Exit process with runner's exit code on completion."]
  (ensure-deps! [:doo :adzerk/boot-cljs])
  (let [ids           (if (seq ids) ids default-ids)
        optimizations (or optimizations :none)
        js-env        (or js-env default-js-env)
        cljs-opts     (merge {:optimizations optimizations}
                             (when (= :node js-env) {:target :nodejs, :hashbang false})
                             cljs-opts)
        wrapper       (if update-fs?
                        identity
                        (fn [wrapped-handler]
                          (fn [handler]
                            (fn [fileset]
                              ((wrapped-handler (fn [_])) fileset)
                              (core/commit! fileset)
                              (handler fileset)))))]
    ;; TODO: this is just one of many warnings doo can emit, rather resuse those
    (if (and (= :none optimizations) (= :rhino js-env))
      (do (fail "Combination of :rhino and :none is not currently supported.\n")
          (if exit? (System/exit 1) identity))
      (wrapper
        (comp (reduce comp
                      (for [id ids]
                        (prep-cljs-tests
                          :id id
                          :namespaces namespaces
                          :exclusions exclusions)))
              ((u/r adzerk.boot-cljs/cljs)
                :ids ids
                :compiler-options cljs-opts)
              (run-cljs-tests
                :ids ids
                :cljs-opts cljs-opts
                :js-env js-env
                :exit? exit?
                :debug? debug?))))))

(deftask exit!
  "Exit with the appropriate error code"
  []
  (fn [_]
    (fn [fs]
      (let [errs (err/get-errors fs)]
        (System/exit (if (seq errs) 1 0))))))
