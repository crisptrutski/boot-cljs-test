(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies
  '[[crisptrutski/boot-cljs-test "0.3.0" :scope "test"]
    [adzerk/boot-test            "1.0.6"]])

(require
  '[adzerk.boot-test            :refer :all]
  '[crisptrutski.boot-cljs-test :refer [test-cljs report-errors!] :as cljs-test])

(deftask deps [] identity)

(deftask testing []
  (merge-env! :source-paths #{"test"})
  identity)

(deftask test-id []
  (comp (testing)
        (test-cljs
          :exit? true
          :ids ["unit"])))

(deftask test-ids []
  (comp (testing)
        (test-cljs
          :ids ["unit" 
                "boot_cljs_test_example/integration_suite"])))

(deftask test-asset-path []
  (comp (testing)
        (test-cljs
          :exit? true
          :ids ["js/unit_repeat"])))

(deftask test-exclusions []
  (comp (testing)
        (test-cljs :exclusions #{#"lib"})))

(deftask test-namespaces []
  (comp (testing)
        (test-cljs
          :namespaces [#".*\.lib.*" "wutc"]
          :exit? true)))

(deftask test-all []
  (comp (testing)
        (test-cljs :keep-errors? true)
        (test)
        (report-errors!)))

(deftask test-watch-karma []
  (comp (testing)
        (watch)
        (speak)
        (test-cljs :js-env :chrome)
        (test)))

(defn prn-errors [label]
  (fn [handler]
    (fn [fs]
     (prn label "errors" (crisptrutski.boot-error.core/get-errors fs))
     (handler fs))))

(defn cljs [& args]
  (merge-env! :dependencies '[[adzerk/boot-cljs "1.7.170-3" :scope "test"]
                              [org.clojure/clojurescript "1.7.228" :scope "test"]])
  (require 'adzerk.boot-cljs)
  (apply @(resolve 'adzerk.boot-cljs/cljs) args))

(deftask test-plumbing []
  (comp (testing)
        ;; warn, no snapshot yet
        (cljs-test/fs-restore)
        (cljs-test/fs-snapshot)
        (cljs-test/prep-cljs-tests :id "beep/boop")
        (cljs-test/prep-cljs-tests :id "boop/beep")
        (cljs :ids #{"beep/boop"})
        (cljs-test/run-cljs-tests :ids ["beep/boop"] :verbosity 2)
        (prn-errors "tracked")
        (cljs-test/fs-restore)
        (prn-errors "cleared")
        (cljs :ids #{"boop/beep"})
        (cljs-test/run-cljs-tests :ids ["boop/beep"] :verbosity 1)
        (cljs-test/fs-restore :keep-errors? true)
        (prn-errors "retained")
        ;; fails, compiled suite rolled back
        (cljs-test/run-cljs-tests :ids ["beep/boop"] :verbosity 2)))
