(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies
  '[[org.clojure/clojure "1.8.0" :scope "provided"]
    [crisptrutski/boot-cljs-test "0.4.0-SNAPSHOT" :scope "test"]
    [adzerk/boot-test            "1.0.6"]])

(require
  '[adzerk.boot-test            :refer :all]
  '[crisptrutski.boot-cljs-test :refer [test-cljs] :as cljs-test])

(deftask deps [] identity)

(deftask testing []
  (merge-env! :source-paths #{"test"})
  identity)

(deftask test-id []
  (comp (testing)
        (test-cljs
          :ids ["boot_cljs_test_example/suite"])))

(deftask test-ids []
  (comp (testing)
        (test-cljs
          :ids ["boot_cljs_test_example/unit"
                "boot_cljs_test_example/integration_suite"])))

(deftask test-exclusions []
  (comp (testing)
        (test-cljs :exclusions #{#"lib"})))

(deftask test-namespaces []
  (comp (testing)
        (test-cljs
          :namespaces [#".*\.lib.*" "wutc"])))

(deftask test-all []
  (comp (testing)
        (test-cljs)
        (test)))

(deftask test-watch-karma []
  (comp (testing)
        (watch)
        (speak)
        (test-cljs :js-env :chrome)
        (test)))

(defn cljs [& args]
  (merge-env! :dependencies '[[adzerk/boot-cljs "1.7.228-2" :scope "test"]
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
        (cljs-test/fs-restore)
        (cljs :ids #{"boop/beep"})
        (cljs-test/run-cljs-tests :ids ["boop/beep"] :verbosity 1)
        (cljs-test/fs-restore)
        ;; fails, compiled suite rolled back
        (cljs-test/run-cljs-tests :ids ["beep/boop"] :verbosity 2)))
