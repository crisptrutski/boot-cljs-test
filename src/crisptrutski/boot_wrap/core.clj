(ns crisptrutski.boot-wrap.core
  (:require
    [boot.core :as core :refer [deftask]]
    [boot.util :refer [info dbug warn fail]])
  (:import
    (boot.tmpdir ITmpFileSet)))

(defn- commit-fs
  "Return a synchronised filset value given the latest value, and a (potentially nil) pervious value."
  [new-fs old-fs]
  (when-not old-fs
    (warn "Tried to pop empty fileset stack. Using current value instead.\n"))
  (when (and old-fs (not= old-fs new-fs))
    (if (instance? ITmpFileSet old-fs)
      ;; ensure temp directories synced with old fs
      (core/commit! old-fs)
      (warn "%s is not a fileset.\n" old-fs)))
  (or old-fs new-fs))

(defn- with-fs-stack
  "Override :fs-stack value on object's metadata" [fs stack]
  (when fs
    (with-meta fs (assoc (meta fs) :fs-stack stack))))

(defn push-fs
  "Append current value to :fs-stack on its own metadata"
  [fs]
  (with-meta fs (update (meta fs) :fs-stack conj fs)))

(defn pop-fs
  "Revert to previous value from :fs-stack"
  [fs]
  (let [stack (:fs-stack (meta fs))]
    (commit-fs fs (with-fs-stack (first stack) (rest stack)))))

(defn peek-fs
  "Return previous value from :fs-stack, but hold onto full stack"
  [fs]
  (let [stack (:fs-stack (meta fs))]
    (commit-fs fs (with-fs-stack (first stack) stack))))

;; fs light

(defn rollback-fs [wrapped-handler]
  (fn [handler]
      (fn [fileset]
        ((wrapped-handler (fn [_])) fileset)
        ;; (core/commit! fileset)
        (handler fileset))))

;; combinators

(defn lift-pre
  "Like `with-pre-wrap` but a combinator taking `fileset -> fileset`"
  [f]
  #(comp % f))

(defn lift-post
  "Like `with-post-wrap` but a combinator taking `fileset -> fileset`"
  [f]
  #(comp f %))

(defn run-if
  "Given `apply?` and `task`, return a new task that applies `task` only if `apply?` is truthy.
   If `apply?` is a function, treat it as a predicate on the current fileset value, otherwise
   use the regular Clojure truthiness."
  [apply? task]
  (if-not (ifn? apply?)
    ;; optimise for this case to avoid creating trivial functions
    (if apply? task identity)
    (fn [next]
      (fn [fs]
        (if (apply? fs)
          ((task next) fs)
          (next fs))))))

;; tasks

(declare fs-push fs-pop fs-peek fs-wrap)

(deftask fs-push [] (lift-pre push-fs))
(deftask fs-pop  [] (lift-pre pop-fs))
(deftask fs-peek [] (lift-pre peek-fs))

(deftask fs-wrap [t task VAL code "Task to wrap up"]
  (comp (lift-pre push-fs) task (lift-pre pop-fs) ))
