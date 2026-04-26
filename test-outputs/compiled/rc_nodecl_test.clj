(ns compiled.rc-nodecl-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-26T13:45:46.805377368Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-nodecl-test,
 :output \"test-outputs/compiled/rc_nodecl_test.clj\"}
```")

(defn rc-nodecl-number? [x] (number? x))

(defn rc-nodecl-op-rc-nodecl-number? [x] (* x 2))

(defn
  rc-nodecl-op
  [x0]
  (cond
    (rc-nodecl-number? x0)
    (rc-nodecl-op-rc-nodecl-number? x0)
    :else
    :default))
