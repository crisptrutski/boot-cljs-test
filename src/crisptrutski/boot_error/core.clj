(ns crisptrutski.boot-error.core)

(defn- conjv [xs x] (conj (vec xs) x))

(defn track-error
  "Record an error as fileset metadata"
  [fs error]
  (with-meta fs (update (meta fs) ::errors #(conjv % error))))

(defn track-errors
  "Record errors as fileset metadata"
  [fs errors]
  (with-meta fs (update (meta fs) ::errors #(into (vec %) errors))))

(defn clear-errors
  "Remove errors from fileset"
  [fs]
  (with-meta fs (dissoc (meta fs) ::errors)))

(defn get-errors
  "Read back all errors recorded in fileset metadata."
  [fs]
  (::errors (meta fs)))

(def ^:dynamic *errs* (atom nil))

(defn track-error!
  "Record an error in dynamic scope"
  [error]
  (swap! *errs* conjv error))

(defmacro with-errors!
  "Flush all errors captured within given body and record in result metadata."
  [& body]
  `(binding [*errs* (atom nil)]
     (let [fs# (do ~@body)
           errs# @*errs*]
       (reset! *errs* nil)
       (track-errors fs# errs#))))
