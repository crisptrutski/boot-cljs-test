(set-env!
  :dependencies '[[org.clojure/clojure       "1.7.0"          :scope "provided"]
                  [boot/core                 "2.6.0"          :scope "provided"]
                  [adzerk/bootlaces          "0.1.13"         :scope "test"]
                  [org.clojure/clojurescript "1.7.228"        :scope "test"]
                  [adzerk/boot-cljs          "1.7.228-2"      :scope "test"]
                  [doo                       "0.1.7"          :scope "test"]
                  [adzerk/boot-test          "1.1.2"          :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[adzerk.boot-test :refer :all])

(def +version+ "0.3.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
 pom  {:project     'crisptrutski/boot-cljs-test
       :version     +version+
       :description "Boot task to run ClojureScript tests."
       :url         "https://github.com/crisptrutski/boot-cljs-test"
       :scm         {:url "https://github.com/crisptrutski/boot-cljs-test"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask deps[])

(deftask testing []
  (merge-env! :source-paths #{"test"}))

