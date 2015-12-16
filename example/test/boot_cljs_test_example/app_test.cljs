(ns boot-cljs-test-example.app-test
  (:require-macros [cljs.test :refer [deftest testing is async]])
  (:require [cljs.test :as t]
            [boot-cljs-test-example.app :as app]))

(deftest test-pass []
  (is (= 2 app/two)))
