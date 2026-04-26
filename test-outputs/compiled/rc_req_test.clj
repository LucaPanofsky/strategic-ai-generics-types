(ns compiled.rc-req-test
  "Compiled by @strategic-ai/generic-types 0.0.1-alpha
Generated: 2026-04-26T13:45:47.082171733Z

## footprint

```edn
{:protocol \"src/protocol.clj\",
 :namespace compiled.rc-req-test,
 :output \"test-outputs/compiled/rc_req_test.clj\",
 :requires [[clojure.string :as str]]}
```"
  (:require [clojure.string :as str]))

(defn rc-req-string? [x] (string? x))

(defn rc-req-op-rc-req-string? [x] (clojure.string/upper-case x))

(defn
  rc-req-op
  [x0]
  (cond
    (rc-req-string? x0)
    (rc-req-op-rc-req-string? x0)
    :else
    :default))
