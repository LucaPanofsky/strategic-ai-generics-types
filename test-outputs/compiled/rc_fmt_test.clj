(ns compiled.rc-fmt-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-26T13:45:46.876184407Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-fmt-test,
 :output \"test-outputs/compiled/rc_fmt_test.clj\"}
```")

(defn rc-fmt-number? [x] (number? x))

(defn rc-fmt-op-rc-fmt-number? [x] (* x 2))

(defn
  rc-fmt-op
  [x0]
  (cond
    (rc-fmt-number? x0)
    (rc-fmt-op-rc-fmt-number? x0)
    :else
    :default))
