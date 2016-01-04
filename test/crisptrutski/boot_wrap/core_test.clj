(ns crisptrutski.boot-wrap.core-test
  (:require [clojure.test :refer :all]
            [crisptrutski.boot-wrap.core :refer :all]))

(defn- minc [m] (update m :value inc))
(defn- run-pipeline [pipeline]
  ((pipeline identity) 1))

(deftest fs-push-pop-peek-test
  (is (= [{:value 4} {:value 3} {:value 3} {:value 2}]
         (-> {:value 1}
             minc
             push-fs
             minc
             push-fs
             minc
             peek-fs
             push-fs
             minc
             peek-fs
             minc
             peek-fs
             minc
             push-fs
             meta
             :fs-stack))))
(deftest run-if-test
  (testing "with values"
    (is (= 51
           (run-pipeline
             (comp (lift-pre inc)
                   (run-if false (lift-pre #(+ % 5)))
                   (run-if true (lift-pre #(+ % 49))))))))
  (testing "with predicates"
    (is (= 5
           (run-pipeline
             (comp (lift-pre inc)
                   (run-if even? (lift-pre #(+ % 3)))
                   (run-if even? (lift-pre #(+ % 30)))))))))

(defn task-a []
  (fn [h] (fn [fs] (h (minc fs)))))

(defn task-b [x]
  (fn [h] (fn [fs] (h (update fs :value #(+ x %))))))

(defn task-c [x]
  (fn [h] (fn [fs] (h (update fs :value #(* x %))))))

(deftest fs-tasks-test
  (is (= [{:value 10N} {:value 10N} {:value 9N} {:value 27} {:value 9}]
         (let [pipeline (comp (task-a)
                              (task-b 5)
                              (fs-push)
                              (task-c 3)
                              (fs-push)
                              (task-c 1/3)
                              (fs-push)
                              (task-a)
                              (fs-push)
                              (task-b 10)
                              (fs-peek)
                              (fs-push))
               runner (pipeline (comp :fs-stack meta))]
           (runner {:value 3})))))

(deftest fs-wrap-test
  (is (= {:value 18}
         (let [pipeline (comp (task-a)
                              (task-b 5)
                              (fs-wrap
                                :task
                                (comp
                                  (task-c 3)
                                  (task-b -5)))
                              ;; this run on the pre wrap-one value
                              (task-c 2))
               runner (pipeline identity)]
           (runner {:value 3})))))