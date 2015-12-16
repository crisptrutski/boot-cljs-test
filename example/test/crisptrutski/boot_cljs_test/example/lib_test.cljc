(ns crisptrutski.boot-cljs-test.example.lib-test
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is async]]))
  (:require #?(:clj [clojure.test :refer :all :as t]
               :cljs [cljs.test :as t])
            [crisptrutski.boot-cljs-test.example.lib :as lib]))

#?(:cljs (enable-console-print!))

#?(:clj
(defmacro async [bind & body]
  `(let [p# (promise)
         ~bind #(deliver p# 1)]
     ~@body
     @p#)))

(deftest test-pass []
  (is (= 3 lib/three)))

(deftest slow-test []
  (async done
    (let [latch (atom 5)
          countdown (fn countdown []
                      (println (str "Running slow test " @latch "..."))
                      (is (integer? @latch))
                      (if (pos? (swap! latch dec))
                        #?(:cljs (js/setTimeout countdown 100)
                           :clj  (do (Thread/sleep 100) (countdown)))
                        (done)))]
      (countdown))))


