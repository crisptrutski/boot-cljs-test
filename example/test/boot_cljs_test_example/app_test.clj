(ns boot-cljs-test-example.app-test
  (:require [clojure.test :refer :all :as t]
            [boot-cljs-test-example.app :as app]))

(deftest test-pass []
  (is (= 200 app/two)))
