# Changes

## 0.3.5

- Update `doo` to version 0.1.8

## 0.3.4

- Fix regression in Windows support (broken file separators)
- Fix using :compiler-options from .cljs.edn test suites

## 0.3.3

- Add support for adzerk/boot-cljs ^2.1.0

## 0.3.2

- Revert to deep copy of resources in response to #62
- Provide opt-in to use symlinks for speed up (-s or --symlink)
  This is not enabled by default as there are issues with shared volumes
  in certain CI environments.

## 0.3.1

THIS VERSION HAS BEEN PULLED: there have been reports of workspace
files being deleted on OSX, presumably when filesets are swept.

See: https://github.com/crisptrutski/boot-cljs-test/issues/62

- Fix behaviour of `update-fs?` (had the reverse behaviour)
- Use hard links to speed up copying of test resources to output path

## 0.3.0

- Removed: :suite-ns replaced with `boot-cljs` compatible ids.
- Deprecated: :out-file to be determined by `id`
- Support multiple runs via multiple IDs
- Support early vs late exit
- Support composition with notify/speak task
- Support custom doo options
- Support :exclusions to filter namespaces
- Support verbosity control with boot -v syntax and share this with doo.
- Support using :require to determine namespaces under test.
- Support runners installed in node_modules
- Support Karma
- Pass compiler options on to doo, including derived :output-dir
- More powerful plumbing API
- Remove global state for fileset revert
- Remove global state for deferred errors
- Bump default injected dependencies

## 0.2.2

- Bugfix: Don't compile or load unrelated namespaces #33
- Feature: Support arbitrary doo arguments

## 0.2.1

- Change: Search input dirs for namespaces, rather than using runtime metadata.
- Change: If no namespace terms, default to "ends with -test" filter.
- Feature: Support regular expressions for namespaces
- Feature: Add `exit!` task, to defer behaviour from `:exit?` flag until later.
- Bugfix: Respect `:exit?` flag for case of unsupported environments
- Bugfix: Pass all compiler options down to doo, not just :output-to

## 0.2.0

- Track boot-cljs 1.7.48 API changes (aebe9c)
- Remove fileset sift hack (aebe9c)
- Support cljs optimization level override (aebe9c)
