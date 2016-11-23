# boot-cljs-test

Boot task to run ClojureScript tests.

[![Circle
CI](https://circleci.com/gh/crisptrutski/boot-cljs-test.svg?style=svg)](https://circleci.com/gh/crisptrutski/boot-cljs-test)

[![Clojars Project](https://img.shields.io/clojars/v/crisptrutski/boot-cljs-test.svg)](https://clojars.org/crisptrutski/boot-cljs-test)

[](dependency)
```clojure
[crisptrutski/boot-cljs-test "0.3.0-SNAPSHOT"] ;; latest release
```
[](/dependency)

## Getting started

```
;; build.boot
(set-env! :dependencies '[[crisptrutski/boot-cljs-test "0.3.0-SNAPSHOT" :scope "test"]])
(deftask testing [] (merge-env! :source-paths #{"test"}) identity)

;; cli
> boot testing test-cljs
```

And you should get output like this:

```
;; ======================================================================
;; Testing with Phantom:

;; Testing your.awesome.thing-test

Ran 1337 tests containing 9001 assertions.
0 failures, 0 errors
```

Supported task options:

```
  -h, --help                 Print this help info.
  -j, --js-env VAL           Environment to execute within, eg. slimer, phantom, ...
  -n, --namespaces NS        Namespaces to run, supports regexes. If omitted tries "*-test" then "*".
  -e, --exclusions NS        Namespaces to exclude, supports regexes.
  -i, --ids IDS              Test runner ids. Generates each config if not found.
  -c, --cljs-opts OPTS       Options to pass on to CLJS compiler.
  -O, --optimizations LEVEL  Sets optimization level for CLJS compiler, defaults to :none.
  -d, --doo-opts VAL         Sets options to pass on to Doo.
  -u, --update-fs?           Skip fileset rollback before running next task.
                             By default fileset is rolled back to support additional cljs suites, clean JARs, etc.
  -x, --exit?                Throw exception on error or inability to run tests.
  -k, --keep-errors?         Retain memory of test errors after rollback.
  -v, --verbosity VAL        Log level, from 1 to 3.
```

### More advanced usage:

This library provides some lower level "plumbing" tasks:

 1. `prep-cljs-tests`, which generates boot-cljs edn files and test runner cljs files (if necessary)
 2. `run-cljs-tests`, which executes a test runner
 3. `report-errors!`, which throws if any errors were reported in upstream tests
 4. `clear-errors`, which clears any error reports from upstream tests
 5. `wrap-fs-snapshot`, which passes current snapshot state down pipeline as metadata
 6. `wrap-fs-restore`, which rolls back to the snapshot passed down as metadata

The "porcelain" "`test-cljs` task adds conveniences around default values and composes these tasks in the obvious way.

The goal is for `test-cljs` to support most workflows, but it can be inefficient for builds with lots of suites and/or runners.

To carve out extra efficiency, or to perform more exotic workflows, we recommend using these tasks directly, which form part of the public API.

Some example workflows that currently would require using "plumbing" API:

1. Running the same suite(s) in multiple environments (eg. headless and multiple browsers) without recompiling.
2. Building multiple test suites based on different regexes within the same `adzerk.boot-cljs/cljs` run.
3. Building non-test `ids` within the same `adzerk.boot-cljs/cljs` run.
4. Run all tests suites before reporting failure from any of them

Some of these workflows, eg. (1) and (4) are not actually terribly
