(ns compiled.ns-named-test (:require [clojure.string]))

(defn ns-named-string? [x] (string? x))

(defn ns-named-op-ns-named-string? [x] (clojure.string/upper-case x))

(defn ns-not-string-default [x] (str "not-a-string: " x))

(defn
 ns-named-op
 [x0]
 (cond
  (ns-named-string? x0)
  (ns-named-op-ns-named-string? x0)
  :else
  (ns-not-string-default x0)))
