# GENERAL GUIDELINES 

## Providing documentation 

Whenever you are asked to deliver some kind of documentation, use the following principles:
- The documentation shall explain why the thing being documented exists.
- The documentation shall explain what problem the thing being documented solves.
- When documenting functions:
  - Include input and output documentation, i.e., what are the inputs and what are the outputs.
  - Include example usage.

It is **mandatory** to keep documentation:
- updated
- written with a uniform style
- short and relevant

Provide documentation if explicitly required; otherwise, use your judgment. Avoid unnecessary documentation.

## Code netiquettes in Clojure 

- Avoid comments with `;;`.
- Whenever comments are necessary, use the `(comment ...)` idiom and put the comment above the thing that must be commented.
- As a general rule, follow established conventions.
- When working with sequences, prefer transducers whenever possible.
- Do not use Java methods unless necessary or explicitly required.
- It is important to use the correct names:
  - Avoid short abbreviations; prefer clarity.
  - Avoid patterns like `s'`, `s''`, `s'''`.

## Testing code 

Tests are organized semantically into three different kinds of tests:
- I can / I cannot tests
- It must be / It must not be tests
- Unit tests

"I can" tests describe what a system can do. "It must be" tests describe what the constraints are, whereas unit tests are useful and traditional unit tests.

Use the following naming conventions for tests:
```text
i-can-do-something-..
i-cannot-do-something-..
...
it-must-be-that-merge-is-idempotent
it-must-not-be-that-something-is-nil
...
unit-<unit test name>
....

```

## Design by TDD strategy

Before writing any implementation, verify that requirements are clear enough to be expressed as specs. If the problem cannot yet be described in terms of inputs, outputs, and constraints, it is not ready for implementation — clarify the design first.

Once requirements are clear, the strategy proceeds in three steps:

1. **Describe the problem through specs** — define the shape of inputs and outputs using `clojure.spec`. This forces precision about what the system accepts and what it produces.
2. **Design the tests and validate with the user** — write tests that express the expected behaviour before any implementation exists. Verify that the tests are rational and sound before proceeding.
3. **Address the implementation** — with specs and tests agreed upon, implement the solution to make the tests pass.

The concrete sequencing depends on the specifics of the problem (a pure transformation, a macro, a stateful registry each have different natural orderings), but this general three-step approach always applies.

### Lessons learned

- **Specs for both input and output** — speccing the DSL input shape and the output data shape in the same `specs.clj` makes the contract of the transformation explicit before any implementation exists.
- **Pure core first** — isolate the pure transformation function from side effects and implement it first. It is the easiest to test and the most likely to reveal design issues early.
- **Mirror existing patterns** — when a codebase has an established structure (e.g. `specs.clj` + `data.clj`), following that pattern keeps the new code consistent and reduces cognitive load.
- **Global state isolation** — tests that assert on a global atom must own their fixtures. When registration happens at namespace load time (e.g. via a macro at the top level), use `use-fixtures :once` to re-register before the tests run, not `use-fixtures :each`. Do not assume load-time side effects survive across test namespaces.
- **Macros emitting computed data with embedded forms** — when a macro computes a data structure at expansion time that contains Clojure forms (handler bodies, expressions), do not inline the data directly into the expanded `do` block — those forms will be evaluated as code. Pass the data via a quoted runtime call instead (e.g. `(compiler/parse-infostructure (quote ~form))`).
- **Test realistic false cases for generated predicates** — when a macro generates a predicate, test not only the positive case but also structurally similar values that should be rejected (e.g. a vector with the same tag as a list-based type).