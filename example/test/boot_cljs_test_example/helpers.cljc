(ns boot-cljs-test-example.helpers)

#?(:clj
(defmacro async [bind & body]
  `(let [p# (promise)
         ~bind #(deliver p# 1)]
     ~@body
     @p#)))
