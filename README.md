# boot-cljs-test

Boot task to run ClojureScript tests.

[](dependency)
```clojure
[crisptrutski/boot-cljs-test "0.2.0-SNAPSHOT"] ;; latest release
```
[](/dependency)

There are no stable releases yet - this is brand spanking new.

## Usage

### The simple way:

1. Add top-level require for `'[crisptrutski.boot-cljs-test :refer [test-cljs]]` to `build.boot`.
2. Set `(task-options! test-cljs {:js-env :phantom})`, substituting test runner of your choice.
3. Create a task to add tests to class path (ie. `(set-env! :source-paths #(conj % "test"))`) and run `test-cljs`.

### More advanced usage:

This library provides two lower level tasks, `prep-cljs-tests` and `run-cljs-tests`, which are designed to run before and after Clojurescript compilation (eg. by `cljs` task in `boot-cljs`).

The `test-cljs` task merely composes those tasks with the `cljs` task, with sensible defaults. That includes ignoring any `*.cljs.edn` files in your fileset,
to ensure tests and generated suite file are included in the compile.

Examples of workflows achievable by composing these smaller tasks manually:

1. configuring requires and entry point for output file in a `.cljs.edn` file
2. building multiple test suites in a single pass
3. building test suites and dev / production / other outputs in same `cljs` pass
4. running the same test in multiple environments (eg. v8 and spidermonkey, or headless and browser)
