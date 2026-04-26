(ns compiled.rc-chain-helper-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-26T13:45:46.953256197Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-chain-helper-test,
 :output \"test-outputs/compiled/rc_chain_helper_test.clj\"}
```")

(defn rc-ch-number? [x] (number? x))

(defn rc-inner-helper [x] (+ x 1))

(defn rc-outer-helper [x] (rc-inner-helper x))

(defn rc-ch-op-rc-ch-number? [x] (rc-outer-helper x))

(defn
  rc-ch-op
  [x0]
  (cond (rc-ch-number? x0) (rc-ch-op-rc-ch-number? x0) :else :default))
