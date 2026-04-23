(ns compiled.hier-test)

(defn
 h-interval?
 [x]
 (and (map? x) (contains? x :lo) (contains? x :hi)))

(defn h-number? [x] (number? x))

(defn h-some? [x] (some? x))

(defn h-nil? [x] (nil? x))

(defn hier-op-h-interval?-h-number? [_ _] :merge-int-num)

(defn hier-op-h-number?-h-interval? [_ _] :merge-num-int)

(defn hier-op-h-some?-h-nil? [_ _] :merge-some-nil)

(defn default-hier-merge [_ _] :no-merge)

(defn
 hier-op
 [x0 x1]
 (cond
  (h-interval? x0)
  (cond
   (h-number? x1)
   (hier-op-h-interval?-h-number? x0 x1)
   (h-nil? x1)
   (hier-op-h-some?-h-nil? x0 x1)
   :else
   (default-hier-merge x0 x1))
  (h-number? x0)
  (cond
   (h-interval? x1)
   (hier-op-h-number?-h-interval? x0 x1)
   (h-nil? x1)
   (hier-op-h-some?-h-nil? x0 x1)
   :else
   (default-hier-merge x0 x1))
  (h-some? x0)
  (cond
   (h-nil? x1)
   (hier-op-h-some?-h-nil? x0 x1)
   :else
   (default-hier-merge x0 x1))
  :else
  (default-hier-merge x0 x1)))
