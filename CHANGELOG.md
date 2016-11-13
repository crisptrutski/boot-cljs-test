# Changes

## 0.3.0

Use IDs:

- Replace :out-file with boot-cljs style IDs
- Support using :require to determine possible namespaces [but what about transitives]
- Support multiple runs via multiple IDs
- Drop :suite-ns parameter, use convention or edn file
~ Add :skip-wrapper option to skip generating 'doo wrapper
x Support early vs late exit (another artifact of multiple IDs)

Karma support:

- Support runners installed in node_modules
- Use karma server when running with `watch`
- Pass all compiler options on to doo, including a derived :output-dir

Other:

- Support :exclusions to filter namespaces
- Support :debug option and share this with doo
- Replace global state for fileset revert with closure scope
- Replace global state for deferred errors to use fileset metadata
- Bump default injected dependencies

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
