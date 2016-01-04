# Changes

## 0.3.1

- Support karma install
- Support -vv style verbosity control
- Move useful utils out to boot task library
- Support limiting namespaces via doo-run-all
- Support crunching regexes into one (http://stackoverflow.com/questions/869809/combine-regexp)

## 0.3.0

Use IDs
=======

- Replace :out-file with boot-cljs style IDs
- Support using :require to determine possible namespaces [but what about transitives]
- Support multiple runs via multiple IDs
- Drop :suite-ns parameter, use convention or edn file
~ Add :skip-wrapper option to skip generating 'doo wrapper
x Support early vs late exit (another artifact of multiple IDs)

Karma support
=============

-- Pass all compiler options on to doo
-- Provide sensible :output-dir option to doo

Other
====

- Support :exclusions to filter namespaces
- Support :debug option and share this with doo
- Replace global state for fileset revert with closure scope
- Replace global state for deferred errors to use fileset metadata
- Bump default injected dependencies

## 0.2.1 (pending)

- Experimental: options and helpers around convention based usage.
- Feature: Add `exit!` task, to defer behaviour from `:exit?` flag until later.
- Bugfix: Respect `:exit?` flag for case of unsupported environments

## 0.2.0

- Track boot-cljs 1.7.48 API changes (aebe9c)
- Remove fileset sift hack (aebe9c)
- Support cljs optimization level override (aebe9c)
