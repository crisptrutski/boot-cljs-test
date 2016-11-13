(ns crisptrutski.boot-error.core)

(def ^:dynamic *errs* (atom nil))

(defn- conjv
  "Push item onto end of collection. Coerces to vector so that subsequent appends are cheap."
  [xs x]
  (conj (vec xs) x))

(defn track-error
  "Record an error as fileset metadata"
  [fs error]
  (with-meta fs (update (meta fs) :errors #(conjv % error))))

(defn track-errors
  "Record errors as fileset metadata"
  [fs errors]
  (with-meta fs (update (meta fs) :errors #(into (vec %) errors))))

(defn flush-errors!
  "Record all errors in dynamic scope to filset metadata."
  [fs]
  (let [errs @*errs*]
    (reset! *errs* nil)
    (reduce track-error fs errs)))

(defn get-errors
  "Read back all errors recorded in fileset metadata."
  [fs]
  (:errors (meta fs)))

(defn track-error!
  "Record an error in dynamic scope"
  [error]
  (swap! *errs* conjv error))

(defmacro with-errors!
  "Flush all errors captured within given body and record in result metadata."
  [& body]
  `(binding [*errs* (atom nil)]
     (flush-errors! (do ~@body))))
