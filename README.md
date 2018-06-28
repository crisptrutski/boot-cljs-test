# boot-cljs-test

Boot task to make ClojureScript testing quick, easy, and consistent with testing Clojure.

[![Circle
CI](https://circleci.com/gh/crisptrutski/boot-cljs-test.svg?style=svg)](https://circleci.com/gh/crisptrutski/boot-cljs-test) [![Clojars Project](https://img.shields.io/clojars/v/crisptrutski/boot-cljs-test.svg)](https://clojars.org/crisptrutski/boot-cljs-test)

[](dependency)
```clojure
[crisptrutski/boot-cljs-test "0.3.5-SNAPSHOT"] ;; latest release
```
[](/dependency)

## Getting started

Add to `build.boot` and make sure the tests are added to the classpath.

```
(set-env! :dependencies '[[crisptrutski/boot-cljs-test "0.3.0" :scope "test"]])
(require '[crisptrutski.boot-cljs-test :refer [test-cljs]])
(deftask testing [] (merge-env! :source-paths #{"test"}) identity)
```

Run `boot testing test-cljs`

```
;; Testing your.awesome.foo-test
;; Testing your.spectacular.bar-test

Ran 1337 tests containing 9001 assertions.
0 failures, 0 errors
```

The task takes care of generating tedious runner namespaces for you!

You can also use our own runner namespaces - they will be picked up when by a matching `id`.

eg. `boot testing test-cljs --ids="my/awesome/test_runner"`

The heavy lifting of running and reporting errors is handled by the excellent [Doo](https://github.com/bensu/doo)

There are a lot of options and conveniences, some of which are demo'd in [this example project](/example)

Supported task options:

```
  -h, --help                 Print this help info.
  -j, --js-env VAL           VAL sets environment to execute within, eg. slimer, phantom, ...
  -n, --namespaces NS        Conj NS onto namespaces to run, supports regexes.
                             If omitted tries "*-test" then "*".
  -e, --exclusions NS        Conj NS onto namespaces to exclude, supports regexes.
  -i, --ids IDS              Conj IDS onto test runner ids. Generates each config if not found.
  -c, --cljs-opts OPTS       OPTS sets options to pass on to CLJS compiler.
  -O, --optimizations LEVEL  LEVEL sets optimization level for CLJS compiler, defaults to :none.
  -d, --doo-opts VAL         VAL sets options to pass on to Doo.
  -u, --update-fs?           Skip fileset rollback before running next task.
                             By default fileset is rolled back to support additional cljs suites, clean JARs, etc.
  -x, --exit?                Throw exception on error or inability to run tests.
  -k, --keep-errors?         Retain memory of test errors after rollback.
  -s, --symlink?             Use symlinks to copy resources and node dependencies into test output folder.
  -v, --verbosity VAL        VAL sets log level, from 0 to 3.
  -o, --out-file VAL         VAL sets DEPRECATED Output file for test script.
```

## Getting fancy

To steal the Git terminology of "plumbing" vs "porcelain", the `test-cljs` task is the high level porcelain API.

The plumbing is also stable and open for business:

 1. `prep-cljs-tests` - generates boot-cljs edn files and test runner cljs files (if necessary)
 2. `run-cljs-tests` - executes a test runner
 3. `report-errors!` - throws if any errors were reported in upstream tests
 4. `clear-errors` - clears any error reports from upstream tests
 5. `fs-snapshot` - passes current snapshot state down pipeline as metadata
 6. `fs-restore` - rolls back to the snapshot passed down as metadata

These could also be referred to as the "simple" and "easy" APIs :smile:

The `test-cljs` task (roughly speaking) composes these tasks in the obvious way:

`fs-snapshot -> prep-cljs-tests -> run-cljs-tests -> report-errors -> fs-restore`

If you need to support more exotic workflows, or carve out efficiency - just use these tasks directly!
