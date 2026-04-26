# Issue #1 Analysis: plain helper defns not emitted

## The bug

`emit-vars!` assembles the emitted file from five sources:

| Source | Collected via |
|---|---|
| Predicate defns | brain predicate registry |
| Handler defns | brain handler registry |
| Constructor defns | brain infostructure plan registry |
| Default-expression helpers | `extract-default-defn` — reads head symbol of each generic's `:default-expr` |
| Generic dispatch defns | compiled dispatch tree |

Plain `defn` functions defined in a source file — outside any macro — are not registered with the brain. They are loaded into the JVM by `load-file` but are invisible to the compiler. Any handler body that calls one of these helpers emits a call that has no corresponding definition in the runtime file.

## Where exactly

In `emit-vars!` (`runtime_compiler.clj`, around line 367), after:

```clojure
all-defns (topsort-forms (concat pred-defns handler-defns constructor-defns default-defns generic-defns))
```

there is no step that finds symbols referenced in those forms that are not yet defined in the collection.

`extract-default-defn` is a partial version of what is needed: it looks at the head symbol of each generic's default expression and resolves it via `defn-form-from-source`. The fix is a generalisation of this pattern to all handler bodies.

## The fix

After assembling `all-defns`, iterate to a fixed point:

1. Walk all forms in the current collection and collect every symbol that is:
   - referenced in a body position
   - not already defined in the collection
   - not available in `clojure.core`
2. For each such symbol, call `defn-form-from-source` — it searches all loaded namespaces, which includes the helpers loaded by `load-file`.
3. Add any found forms to the collection and repeat until no new symbols are discovered.

This resolves chains of helpers (helpers calling helpers) without any arbitrary depth limit, and it only includes what is actually referenced — no noise.

`defn-form-from-source` works here because `load-file` has already loaded the source files, so helpers like `walk-a-promise` are present as vars in their namespace and are findable by the symbol search.

The function `referenced-names` already exists but only finds references within a known defined-set. A new private function — call it `collect-source-helpers` — will walk the forms looking for symbols not in the defined-set and not in `clojure.core`, then resolve and recurse.

## Alignment with repository principles

**TDD order:**
1. *Specs* — no new data shapes are introduced. The fix is a transformation over the existing set of `defn` forms, so no new spec is needed.
2. *Tests* — one new test in `runtime_compiler_test.clj`: define a source with a plain helper called from a handler, run `emit-runtime!`, assert the helper appears in the emitted file. A second test verifies the fixed-point behaviour: a helper that calls another helper, both emitted.
3. *Implementation* — add `collect-source-helpers` in `runtime_compiler.clj` and wire it into `emit-vars!` after the existing defn assembly step.

**No new abstractions:** `collect-source-helpers` is a private function, not exposed in the public API. `defn-form-from-source` and the dependency infrastructure (`topsort-forms`) already exist and are reused unchanged.

**No backwards-incompatible change:** the compiler's public interface (`emit-runtime!`) and config shape are untouched.

## What is not addressed here

The issue mentions surfacing an error as an alternative to emitting the helpers. Once the fix is in place, that is no longer necessary — the helpers will be found and emitted. If a symbol is referenced but has no loadable source (e.g. it comes from a Java interop call or a macro-generated var), `defn-form-from-source` returns nil and the symbol is silently left unresolved. Detecting and reporting that case is a separate concern and not part of this fix.
