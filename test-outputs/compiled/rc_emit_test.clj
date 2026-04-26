(ns compiled.rc-emit-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-26T13:45:46.759292532Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-emit-test,
 :output \"test-outputs/compiled/rc_emit_test.clj\"}
```")

(defn rc-emit-number? [x] (number? x))

(defn rc-emit-op-rc-emit-number? [x] (* x 3))

(defn
  rc-emit-op
  [x0]
  (cond
    (rc-emit-number? x0)
    (rc-emit-op-rc-emit-number? x0)
    :else
    :default))
