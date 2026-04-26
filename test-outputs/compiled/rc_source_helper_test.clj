(ns compiled.rc-source-helper-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-26T13:45:47.053987902Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-source-helper-test,
 :output \"test-outputs/compiled/rc_source_helper_test.clj\"}
```")

(defn rc-sh-number? [x] (number? x))

(defn rc-source-helper [x] (* x 100))

(defn rc-sh-op-rc-sh-number? [x] (rc-source-helper x))

(defn
  rc-sh-op
  [x0]
  (cond (rc-sh-number? x0) (rc-sh-op-rc-sh-number? x0) :else :default))
