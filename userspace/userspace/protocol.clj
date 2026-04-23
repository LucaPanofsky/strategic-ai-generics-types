(ns userspace.protocol
  (:refer-clojure :exclude [await merge flatten])
  (:require
   [strategic-ai.generics-types.generics.generics
    :refer [defgeneric]]
   [strategic-ai.generics-types.infostructure.macros
    :refer [definfostructure]]))

(declare contradiction contradiction?)

(defgeneric unpack [thing f] (f thing))

(defgeneric flatten [thing] thing)

(defgeneric bind [x f] (flatten (unpack x f)))

(defgeneric content [x] x)

(defgeneric equality? [x y] (= (content x) (content y)))

(defgeneric await [x] x)

(defn base-merge [x y]
  (if (equality? x y)
    x
    (contradiction :merge/equality #{x y})))

(defgeneric merge [x y] {:strategy :hierarchy}
  (base-merge x y))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(definfostructure nil
  (hierarchy
   (implies any?))
  (generics
   (bind [(nil? this) (fn? other)] this)
   (unpack [(nil? this) (fn? other)] this)
   (merge [(nil? this) (any? other)] other)
   (merge [(some? this) (nil? other)] this)))

(definfostructure contradiction
  (hierarchy
   (implies any?)
   (implies some?))
  (constructor [type args])
  (generics
   (bind [(contradiction? this) (fn? other)] this)
   (unpack [(contradiction? this) (fn? other)] this)
   (merge [(contradiction? this) (any? other)] this)
   (merge [(some? this) (contradiction? other)] other)))

(comment
  (equality?
   (merge 1 nil)
   (merge nil 1))

  (equality?
   (merge 1 2)
   (merge 2 1))

  (=
   (merge 1 2)
   (merge 2 1))

  (contradiction? (merge 1 2))

  (equality?
   (merge 1 1)
   (merge 1 1))

  (equality? 1  (bind 0 inc))
  (equality? nil  (bind nil inc))

  (contradiction?
   (contradiction :unexplicable #{nil #{}}))

  '(:contradiction :unexplicable #{nil #{}})


  (defgeneric describe [x] {:strategy :hierarchy} :unknown)

  (definfostructure information
    (hierarchy (implies any?))
    (generics
     (describe [(information? this)] :is-information)))

  (describe (list :information))
  (describe 1)

  "For (merge nil 1), the dispatch does not go through the hierarchy — it's a direct tree match:
   
   - Tree checks (nil? nil) → true, (any? 1) → true → handler found immediately, returns 1
   
   The hierarchy fallback is only consulted when the tree returns no match"

  (definfostructure my-nothing
    (hierarchy (implies nil?)))
  (merge (list :my-nothing) 1))


