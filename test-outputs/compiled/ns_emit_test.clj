(ns compiled.ns-emit-test)

(defn ns-emit-number? [x] (number? x))

(defn ns-emit-op-ns-emit-number? [x] (* x 2))

(defn
 ns-emit-op
 [x0]
 (cond
  (ns-emit-number? x0)
  (ns-emit-op-ns-emit-number? x0)
  :else
  :default))
