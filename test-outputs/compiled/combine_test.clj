(ns compiled.combine-test (:require [clojure.string]))

(defn combine-string? [x] (string? x))

(defn combine-number? [x] (number? x))

(defn
 combine-op-combine-string?-combine-string?
 [x y]
 (clojure.string/join " " [x y]))

(defn combine-op-combine-number?-combine-string? [x y] (str x ": " y))

(defn combine-op-combine-number?-combine-number? [x y] (+ x y))

(defn combine-unknown [x y] (str "unknown: " x ", " y))

(defn
 combine-op
 [x0 x1]
 (cond
  (combine-string? x0)
  (cond
   (combine-string? x1)
   (combine-op-combine-string?-combine-string? x0 x1)
   :else
   (combine-unknown x0 x1))
  (combine-number? x0)
  (cond
   (combine-string? x1)
   (combine-op-combine-number?-combine-string? x0 x1)
   (combine-number? x1)
   (combine-op-combine-number?-combine-number? x0 x1)
   :else
   (combine-unknown x0 x1))
  :else
  (combine-unknown x0 x1)))
