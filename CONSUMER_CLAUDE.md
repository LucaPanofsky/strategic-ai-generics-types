# Consumer FAQ

Answers to questions a new consumer of this library is likely to ask.

---

## Setup

**How do I add this library to my project?**

Add it as a git dependency in `deps.edn` using the SHA of the commit you want to pin:

```clojure
{:deps {io.github.LucaPanofsky/strategic-ai-generics-types
        {:git/url "https://github.com/LucaPanofsky/strategic-ai-generics-types"
         :git/sha "<sha>"}}}
```

Once a tag is published, you can use `:git/tag "v0.0.1-alpha"` instead.

---

**Do I need this library at runtime in production?**

No. The intended workflow is to compile your generics into a standalone `.clj` file using `emit-runtime!`. The emitted file has no dependency on this library — only plain Clojure. Your production artifact depends only on that compiled file.

You still need this library on the classpath during development and at compile time.

---

## Project structure

**What goes in the protocol file vs the source files?**

The protocol file is the source of truth for your generic operators. It defines all `defgeneric` declarations and any infostructures that are core to the domain. It is loaded first by the compiler.

Source files extend the protocol with additional infostructures for specific types. They are loaded in order after the protocol. A source file should never define new generics — only handlers for generics already declared in the protocol.

```clojure
{:protocol "src/my_project/protocol.clj"
 :sources  ["src/my_project/interval.clj"
            "src/my_project/quine.clj"]
 :namespace compiled.my-project.runtime
 :output    "runtimes/runtime.clj"}
```

---

**Can I use platform predicates like `number?` directly in `defhandler`?**

Yes. Platform predicates (anything already defined in `clojure.core`) are auto-registered when first used in `defhandler`. You do not need to wrap them with `defpredicate`. They are excluded from the emitted file because they are already available in every Clojure namespace.

---

**What is `definfostructure` and when should I use it instead of `defhandler`?**

`definfostructure` is a higher-level macro for defining a named type all at once: its predicate, optional constructor, hierarchy position, and all its generic handlers. Use it when you are introducing a new domain type. Use `defhandler` directly when you are adding a handler for a combination of existing types that does not correspond to a single named concept.

---

## Dispatch

**What is the difference between `:tree` and `:hierarchy` dispatch?**

`:tree` (the default) only dispatches to a handler if the registered predicates match exactly. It does not walk the hierarchy.

`:hierarchy` first tries the tree. If no exact match is found, it checks whether any argument satisfies a child predicate of a registered predicate and dispatches to the parent handler.

Example: if `merge` is registered for `[any?]` and `dog?` implies `any?`, then under `:hierarchy`, calling `merge` on a dog value will find and use the `any?` handler. Under `:tree`, it would not.

Use `:hierarchy` when you want a handler to cover subtypes automatically. Use `:tree` when you want strict exact dispatch.

---

**What happens when no handler matches?**

The default expression from `defgeneric` is evaluated and returned:

```clojure
(defgeneric merge [a b] :no-merge)
```

If no default expression is provided, calling the generic with an unmatched argument set throws `ExceptionInfo`.

---

**Can I use generic operators without compiling?**

Yes. Generics dispatch correctly at runtime without ever calling `emit-runtime!`. Compilation is an optimisation step — it produces a faster, dependency-free artifact for production. During development you can call your generics directly.

---

## Compilation

**How do I invoke `emit-runtime!`?**

The standard approach is a `deps.edn` alias using `-X`:

```clojure
{:aliases
 {:compile
  {:extra-paths ["src/my_project"]
   :extra-deps  {io.github.LucaPanofsky/strategic-ai-generics-types {...}}
   :exec-fn     strategic-ai.generics-types.generics.compilers.runtime-compiler/emit-runtime!
   :exec-args   {:protocol  "src/my_project/protocol.clj"
                 :namespace compiled.my-project.runtime
                 :output    "runtimes/runtime.clj"}}}}
```

Run with `clj -X:compile`. The `:exec-args` map is passed directly to `emit-runtime!` as the config.

---

**Why must `:requires` be quoted in the config?**

```clojure
:requires '[[clojure.string :as str]]
```

Namespace symbols in a data literal are resolved as Java classes at compile time if unquoted, causing `ClassNotFoundException`. The quote prevents evaluation — the symbols are treated as data and emitted verbatim into the generated `ns` form.

---

**The emitted file references functions from another namespace. How do I make that work?**

Use `:requires` in the config to add `[that.namespace :as alias]` entries to the emitted `ns` form. The functions themselves must be provided by the consumer of the compiled file — the compiler does not inline external namespaces.

---

## Testing

**How do I isolate tests that use the registry?**

Rebind `brain/*brain*` to a fresh instance for each test:

```clojure
(ns my-project.my-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(defn with-fresh-brain [f]
  (binding [brain/*brain* (brain/make-brain)]
    (f)))

(use-fixtures :each with-fresh-brain)
```

Each test then runs against an empty brain with no cross-test contamination. Never call `protocol/clear!` from production code — it is for test isolation only.

---

**What is `brain/*brain*` and should I interact with it?**

It is the internal dynamic registry that backs all macros (`defpredicate`, `defhandler`, `defgeneric`, `definfostructure`). You do not need to interact with it in normal usage. The only case where you touch it directly is in tests, to achieve isolation via `binding`.

---

## clj-kondo

**How do I get kondo to understand the macros?**

Run once from your project root, substituting the alias that puts this library on the classpath:

```bash
clj-kondo --copy-configs --dependencies --lint "$(clj -A:<your-alias> -Spath)"
```

Commit the generated `.clj-kondo/imports/` directory so the setup is shared with your team.

---

## Something is not working

**How do I report a bug or request a feature?**

Open an issue on the [GitHub repository](https://github.com/LucaPanofsky/strategic-ai-generics-types). Describe what you expected, what happened instead, and a minimal reproduction if applicable. Issues are the starting point for both bug reports and feature requests.

**Can I contribute a fix or implementation?**

Pull requests are welcome. The development process follows the guidelines in `CLAUDE.md` — read it before writing code. In particular: TDD order (specs → tests → implementation), no `;;` comments, and test isolation via `binding`.

**Who decides whether a PR is merged?**

Stakeholders review and approve all changes. Explicit approval is required before anything lands on `main` — PRs are never self-merged. If you have opened an issue and a stakeholder decides to implement the requested feature directly, they will reference the issue in the PR.
