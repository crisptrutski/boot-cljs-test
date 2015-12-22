# Changes

## 0.2.2

- Bugfix: Don't compile or load unrelated namespaces #33

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
