(ns compiled.merge-test)

(defn interval? [x] (and (map? x) (contains? x :lo) (contains? x :hi)))

(defn generic-merge-nil?-any? [_ b] b)

(defn generic-merge-interval?-interval? [_ _] :merge-int-int)

(defn generic-merge-interval?-number? [_ _] :merge-int-num)

(defn generic-merge-number?-interval? [_ _] :merge-num-int)

(defn default-merge [a b] (if (nil? b) a :no-merge))

(defn
 generic-merge
 [x0 x1]
 (cond
  (nil? x0)
  (cond
   (any? x1)
   (generic-merge-nil?-any? x0 x1)
   :else
   (default-merge x0 x1))
  (interval? x0)
  (cond
   (interval? x1)
   (generic-merge-interval?-interval? x0 x1)
   (number? x1)
   (generic-merge-interval?-number? x0 x1)
   :else
   (default-merge x0 x1))
  (number? x0)
  (cond
   (interval? x1)
   (generic-merge-number?-interval? x0 x1)
   :else
   (default-merge x0 x1))
  :else
  (default-merge x0 x1)))
