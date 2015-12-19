(ns clj-test.suite
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [boot-cljs-test-example.lib-test]))

(doo-tests 'boot-cljs-test-example.lib-test)
