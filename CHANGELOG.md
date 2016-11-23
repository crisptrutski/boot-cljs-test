# Changes

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
