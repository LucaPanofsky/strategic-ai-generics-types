(ns strategic-ai.generics-types.impl.brain.hierarchy-test
  (:require [clojure.test :refer [deftest is]]
            [strategic-ai.generics-types.impl.brain.brain :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(comment
  "h:declare-implies! / h:implied-by")

(deftest i-can-declare-an-implies-relationship
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/h:declare-implies! brain/*brain* 'integer? 'number?)
    (is (= '[number?] (protocol/h:implied-by brain/*brain* 'integer?)))))

(deftest it-must-be-that-implied-by-returns-empty-vector-for-unknown-predicate
  (binding [brain/*brain* (brain/make-brain)]
    (is (= [] (protocol/h:implied-by brain/*brain* 'unknown?)))))

(deftest it-must-be-that-declare-implies-is-idempotent
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/h:declare-implies! brain/*brain* 'integer? 'number?)
    (protocol/h:declare-implies! brain/*brain* 'integer? 'number?)
    (is (= '[number?] (protocol/h:implied-by brain/*brain* 'integer?)))))

(deftest it-must-be-that-multiple-parents-can-be-declared-for-one-predicate
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/h:declare-implies! brain/*brain* 'integer? 'number?)
    (protocol/h:declare-implies! brain/*brain* 'integer? 'any?)
    (is (= '[number? any?] (protocol/h:implied-by brain/*brain* 'integer?)))))

(comment
  "h:children-of")

(deftest i-can-query-children-of-a-parent-predicate
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/h:declare-implies! brain/*brain* 'integer? 'number?)
    (is (= '(integer?) (protocol/h:children-of brain/*brain* 'number?)))))

(deftest it-must-be-that-children-of-returns-empty-for-unknown-parent
  (binding [brain/*brain* (brain/make-brain)]
    (is (empty? (protocol/h:children-of brain/*brain* 'unknown?)))))

(deftest it-must-be-that-multiple-children-can-be-declared-for-one-parent
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/h:declare-implies! brain/*brain* 'integer? 'number?)
    (protocol/h:declare-implies! brain/*brain* 'float? 'number?)
    (is (= #{'integer? 'float?} (set (protocol/h:children-of brain/*brain* 'number?))))))

(comment
  "binding isolation")

(deftest it-must-be-that-hierarchy-bindings-are-isolated
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/h:declare-implies! brain/*brain* 'integer? 'number?)
    (binding [brain/*brain* (brain/make-brain)]
      (is (= [] (protocol/h:implied-by brain/*brain* 'integer?))))))
