(ns crisptrutski.boot-cljs-test.example.app-test
  (:require-macros [cljs.test :refer [deftest testing is async]])
  (:require [cljs.test :as t]
            [crisptrutski.boot-cljs-test.example.app :as app]))

(enable-console-print!)

(deftest test-pass []
  (is (= 2 app/two)))

#_(deftest test-fail []
  (is (= 1 app/two)))

(deftest slow-test []
  (async done
    (let [latch (atom 5)
          countdown (fn countdown []
                      (print (str "Running slow test " @latch "..."))
                      (if (pos? (swap! latch dec))
                        (js/setTimeout countdown 100)
                        (done)))]
      (countdown))))


