(ns boot-cljs-test-example.suite
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [boot-cljs-test-example.lib-test]))

(enable-console-print!)

(println "** custom suite **")

(doo-tests 'boot-cljs-test-example.lib-test)
