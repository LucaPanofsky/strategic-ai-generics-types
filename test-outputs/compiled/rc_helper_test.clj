(ns compiled.rc-helper-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-22T19:40:49.824832057Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-helper-test,
 :output \"test-outputs/compiled/rc_helper_test.clj\"}
```")

(defn rc-emit-helper [x y] (if (= x y) x :mismatch))

(defn rc-helper-op [x0 x1] (rc-emit-helper x0 x1))
