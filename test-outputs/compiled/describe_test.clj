(ns compiled.describe-test (:require [clojure.string]))

(defn describe-keyword? [x] (keyword? x))

(defn describe-string? [x] (string? x))

(defn describe-number? [x] (number? x))

(defn describe-op-describe-keyword? [x] (str "keyword: " (name x)))

(defn describe-op-describe-string? [x] (clojure.string/upper-case x))

(defn describe-op-describe-number? [x] (str "number: " x))

(defn describe-unknown [x] (str "unknown: " x))

(defn
 describe-op
 [x0]
 (cond
  (describe-keyword? x0)
  (describe-op-describe-keyword? x0)
  (describe-number? x0)
  (describe-op-describe-number? x0)
  (describe-string? x0)
  (describe-op-describe-string? x0)
  :else
  (describe-unknown x0)))
