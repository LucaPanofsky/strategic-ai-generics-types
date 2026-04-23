# GENERIC TYPES

A module of the **strategic-ai** project.

---

## What is generic-types?

**generic-types** is a library for defining **predicate-based type dispatch** in Clojure. It lets you describe what values look like (predicates), declare how operations should behave for different value shapes (generic operators and handlers), and organise those shapes into a hierarchy.

The result is a runtime dispatch system that can be compiled down to a standalone, efficient Clojure file with no runtime dependency on this library.

---

## Abstractions

### Predicates

A predicate is a named unary function that tests a value. Registering it gives the system a symbolic handle to build and inspect dispatch forms.

```clojure
(defpredicate interval? [x]
  (and (map? x) (contains? x :lo) (contains? x :hi)))
```

### Hierarchy

Predicates can be related to each other. `defrel` declares that one predicate implies another — establishing that a value matching the child also logically matches the parent.

```clojure
(defrel number?
  (implied-by integer? float?))

(defrel some?
  (implied-by number? interval?))
```

The hierarchy is used both at runtime (`:hierarchy` dispatch strategy) and at compile time (to sort cond branches by specificity).

### Generic operators

A generic operator dispatches to the right handler based on the predicates satisfied by its arguments. A default expression handles the case where no handler matches.

```clojure
(defgeneric merge [a b] :no-merge)

(defgeneric merge [a b] {:strategy :hierarchy} :no-merge)
```

Two dispatch strategies are available:

| Strategy | Behaviour |
|---|---|
| `:tree` | Default. Walks a nested predicate tree; exact predicate matches only. |
| `:hierarchy` | Also considers handlers registered for parent predicates in the hierarchy. |

### Handlers

A handler is the implementation for a specific combination of predicates. Its name is auto-generated as `op-pred1-pred2-...` and stored in a registry alongside its source form for later compilation.

```clojure
(defhandler merge [number? interval?] [a b]
  {:lo (+ (:lo b) a) :hi (+ (:hi b) a)})
```

### Information structures

`definfostructure` is a higher-level macro that defines a named type — its predicate, constructor, hierarchy position, and generic handlers — in one form. It is a consumer of the generics layer, not a part of it.

```clojure
(definfostructure interval
  (hierarchy
   (implies any?)
   (implies some?))
  (constructor [lo hi])
  (generics
   (merge [(interval? this) (number? other)]
          (interval (+ (:lo this) other) (+ (:hi this) other)))))
```

---

## Compilation

`emit-runtime!` is the sole compiler. It loads a protocol file and any infostructure extension files, compiles all generic operators, and writes a single standalone `.clj` file. The emitted file has no runtime dependency on this library.

```clojure
(emit-runtime!
  {:protocol    "src/userspace/protocol.clj"
   :sources     ["src/userspace/interval.clj"
                 "src/userspace/quine.clj"]
   :namespace   'compiled.runtime
   :output      "out/compiled/runtime.clj"
   :requires    '[[userspace.library :as library]]
   :exclude/clj '[merge flatten]})
```

| Key | Required | Description |
|---|---|---|
| `:protocol` | yes | Path to the protocol file — source of truth for generic operators. Loaded first. |
| `:sources` | no | Ordered list of infostructure extension file paths to load and inline. |
| `:namespace` | yes | Symbol naming the emitted namespace. Never derived from `:output`. |
| `:output` | yes | File path where the compiled source is written. |
| `:requires` | no | Explicit `[ns :as alias]` entries in the emitted `ns` form. Must be quoted. |
| `:exclude/clj` | no | Symbols to exclude from `clojure.core` via `(:refer-clojure :exclude [...])`. |

The emitted file contains `defn` forms for all predicates, handlers, constructors, helper functions, and compiled generics, in dependency order. Trivial dispatch forms are simplified. The output is formatted with cljfmt. Consumers are encouraged to lint the emitted file independently before use.

---

## clj-kondo integration

This library exports clj-kondo configuration for its macros (`defpredicate`, `defgeneric`, `defhandler`, `defrel`, `definfostructure`). Consumers can import it automatically so kondo understands the DSL without manual configuration.

Run this once from your project root, using whatever alias puts this library on the classpath:

```bash
clj-kondo --copy-configs --dependencies --lint "$(clj -A:<your-alias> -Spath)"
```

kondo will write the imported config to `.clj-kondo/imports/generic-types/generic-types/` and pick it up automatically on subsequent runs. Commit the imports directory so the setup is shared with your team.

---

## User workflow

```
defpredicate / defrel          — declare types and their relationships
defgeneric                     — declare operations and defaults
defhandler / definfostructure  — implement operations for specific types
emit-runtime!                  — produce an efficient, standalone runtime
```

`emit-runtime!` is typically invoked via a `deps.edn` alias using `-X`, not called directly at the REPL. The `:exec-args` in the alias carry the full config map.
