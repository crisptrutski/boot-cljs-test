(ns crisptrutski.boot-error.core-test
  (:require
    [clojure.test :refer :all]
    [crisptrutski.boot-error.core :refer :all]))

(deftest pure-test
  (testing "Errors can be read/written to fileset without using environment"
    (is (= [1 :b {'c "d"}]
           (-> {:a 1}
               (track-error 1)
               (track-error :b)
               (track-error {'c "d"})
               (get-errors))))))

(deftest track-errors-test
  (testing "Errors can be thrown imperatively and flushed to fileset via scope")
  (is (= [:a :b 0 1 2]
         (get-errors
           (track-errors
             (track-error! :a)
             (+ 1 2 3 4)
             (track-error! :b)
             (mapv (comp (constantly 5) track-error!) (range 3)))))))
