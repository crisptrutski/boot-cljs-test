# Changes

## 0.3.0

- Replace :out-file with boot-cljs id convention
- Support using :require to determine namespaces under test.
- Support multiple runs via multiple IDs
- Drop :suite-ns parameter, use convention or edn file
x Support early vs late exit (another artifact of multiple IDs)
- Support runners installed in node_modules
- Pass all compiler options on to doo, including a derived :output-dir
- Support :exclusions to filter namespaces
- Support :debug option and share this with doo
- Remove global state for fileset revert
- Remove global state for deferred errors
- Bump default injected dependencies | list actual version

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
