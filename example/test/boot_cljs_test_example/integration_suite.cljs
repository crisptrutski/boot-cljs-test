(ns boot-cljs-test-example.integration_suite
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [boot-cljs-test-example.app-test]))

(doo-tests 'boot-cljs-test-example.app-test)
