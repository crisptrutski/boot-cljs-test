(ns crisptrutski.boot-cljs-test.example.app-test
  (:require-macros [cljs.test :refer [deftest testing is]])
  (:require [cljs.test :as t]
            [crisptrutski.boot-cljs-test.example.app :as app]))

(deftest test-pass []
  (is (= 2 app/two)))

#_(deftest test-fail []
  (is (= 1 app/two)))
