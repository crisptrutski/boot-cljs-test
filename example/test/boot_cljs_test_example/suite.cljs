(ns boot-cljs-test-example.suite
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [boot-cljs-test-example.lib-test]))

(doo-tests 'boot-cljs-test-example.lib-test)
