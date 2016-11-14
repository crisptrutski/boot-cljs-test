(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies
  '[[adzerk/boot-cljs            "1.7.170-3"      :scope "test"]
    [adzerk/boot-cljs-repl       "0.3.0"          :scope "test"]
    [adzerk/boot-reload          "0.4.2"          :scope "test"]
    [pandeiro/boot-http          "0.7.0"          :scope "test"]
    [crisptrutski/boot-cljs-test "0.3.0-SNAPSHOT" :scope "test"]
    [org.clojure/clojurescript   "1.7.189"]
    [adzerk/boot-test            "1.0.6"]])

(require
  '[adzerk.boot-cljs            :refer [cljs]]
  '[adzerk.boot-cljs-repl       :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload          :refer [reload]]
  '[adzerk.boot-test            :refer :all]
  '[pandeiro.boot-http          :refer [serve]]
  '[crisptrutski.boot-cljs-test :refer [test-cljs report-errors!]])

(deftask deps [] identity)

(deftask testing []
  (merge-env! :source-paths #{"test"})
  identity)

(deftask test-suite []
  (comp (testing)
        (test-cljs :exit? true
                   :ids ["boot_cljs_test_example/suite"])))

(deftask test-ids []
  (comp (testing)
        (test-cljs :ids ["boot_cljs_test_example/unit"
                         "boot_cljs_test_example/integration_suite"])))

(deftask test-exclude []
         (comp (testing)
               (test-cljs :exclusions #{#"lib"})))

(deftask test-some []
  (comp (testing)
        (test-cljs :namespaces [#".*\.lib.*" "wutc"])
        (report-errors!)))

(deftask test-all []
         (comp (testing)
               (test-cljs)
               (test)
               (report-errors!)))

(deftask auto-test []
  (comp (testing)
        (watch)
        (speak)
        (test-cljs :js-env :chrome)
        (test)
        (report-errors!)))
