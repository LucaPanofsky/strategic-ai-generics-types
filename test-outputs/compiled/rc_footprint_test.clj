(ns compiled.rc-footprint-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-26T13:45:47.016011638Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-footprint-test,
 :output \"test-outputs/compiled/rc_footprint_test.clj\"}
```")

(defn rc-footprint-number? [x] (number? x))

(defn rc-footprint-op-rc-footprint-number? [x] (* x 2))

(defn
  rc-footprint-op
  [x0]
  (cond
    (rc-footprint-number? x0)
    (rc-footprint-op-rc-footprint-number? x0)
    :else
    :default))
