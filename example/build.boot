(set-env!
 :source-paths    #{"src"}
 :resource-paths  #{"resources"}
 :dependencies   '[[adzerk/boot-cljs            "1.7.170-3"      :scope "test"]
                   [adzerk/boot-cljs-repl       "0.3.0"          :scope "test"]
                   [adzerk/boot-reload          "0.4.2"          :scope "test"]
                   [pandeiro/boot-http          "0.7.0"          :scope "test"]
                   [crisptrutski/boot-cljs-test "0.2.1-SNAPSHOT" :scope "test"]
                   [org.clojure/clojurescript   "1.7.189"]
                   [adzerk/boot-test            "1.0.6"]])

(require
 '[adzerk.boot-cljs            :refer [cljs]]
 '[adzerk.boot-cljs-repl       :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload          :refer [reload]]
 '[adzerk.boot-test            :refer :all]
 '[pandeiro.boot-http          :refer [serve]]
 '[crisptrutski.boot-cljs-test :refer [test-cljs exit! with-ns testing]])

(deftask deps [])

(deftask test-default []
  (test-cljs :conventions? true :exit? true))

(deftask test-suite []
  (test-cljs :conventions? true
             :exit? true
             :suite-ns 'boot-cljs-test-example.suite))

(deftask test-all []
  (comp (testing)
        (with-ns test-cljs)
        (with-ns test)
        (exit!)))

(deftask auto-test []
  (comp (testing)
        (watch)
        (with-ns test-cljs)
        (with-ns test)))

(deftask test-basic []
  (comp (testing)
        (watch)
        (test-cljs)
        (test)))
