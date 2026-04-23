(ns compiled.ns-ext-test (:require [clojure.string]))

(defn ns-ext-string? [x] (string? x))

(defn ns-ext-op-ns-ext-string? [x] (clojure.string/upper-case x))

(defn
 ns-ext-op
 [x0]
 (cond
  (ns-ext-string? x0)
  (ns-ext-op-ns-ext-string? x0)
  :else
  (str "not-a-string: " x0)))
