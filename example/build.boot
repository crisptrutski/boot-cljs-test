(set-env!
 :source-paths    #{"src"}
 :resource-paths  #{"resources"}
 :dependencies   '[[adzerk/boot-cljs            "1.7.170-3"      :scope "test"]
                   [adzerk/boot-cljs-repl       "0.3.0"          :scope "test"]
                   [adzerk/boot-reload          "0.4.2"          :scope "test"]
                   [pandeiro/boot-http          "0.7.0"          :scope "test"]
                   [crisptrutski/boot-cljs-test "0.2.1-SNAPSHOT" :scope "test"]
                   [org.clojure/clojurescript   "1.7.170"]
                   [adzerk/boot-test            "1.0.6"]])

(require
 '[adzerk.boot-cljs            :refer [cljs]]
 '[adzerk.boot-cljs-repl       :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload          :refer [reload]]
 '[adzerk.boot-test            :refer :all]
 '[pandeiro.boot-http          :refer [serve]]
 '[crisptrutski.boot-cljs-test :refer [test-cljs exit! with-ns]])

(deftask testing []
  (set-env! :source-paths #(conj % "test"))
  identity)

(deftask deps [])

(deftask test-all []
  (comp (testing)
        (test-cljs :js-env :phantom #_:exit? #_true)
        (test)
        (exit!)))

(deftask auto-test []
  (comp (testing)
        (watch)
        (test-cljs :js-env :phantom)
        (test)))
