(ns strategic-ai.generics-types.impl.macros-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [strategic-ai.generics-types.impl.macros :as m]
            [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]
            [strategic-ai.generics-types.generics.generics :as g]))

(defn with-fresh-brain [f]
  (binding [brain/*brain* (brain/make-brain)]
    (f)))

(use-fixtures :each with-fresh-brain)

(deftest i-can-define-a-predicate-with-defpredicate
  (m/defpredicate positive-number? [x] (and (number? x) (pos? x)))
  (is (true?  (positive-number? 5)))
  (is (false? (positive-number? -1)))
  (is (false? (positive-number? "a"))))

(deftest it-must-be-that-defpredicate-registers-the-predicate
  (m/defpredicate registered-pred? [x] (keyword? x))
  (is (some? (protocol/p:lookup-predicate brain/*brain* 'registered-pred?))))

(deftest it-must-be-that-defpredicate-stores-the-impl
  (m/defpredicate stored-impl? [x] (map? x))
  (let [entry (protocol/p:resolve-predicate! brain/*brain* 'stored-impl?)]
    (is (fn? (:impl entry)))
    (is (true?  ((:impl entry) {})))
    (is (false? ((:impl entry) 42)))))

(deftest it-must-be-that-defpredicate-stores-the-source-expression
  (m/defpredicate expr-pred? [x] (vector? x))
  (let [entry (protocol/p:resolve-predicate! brain/*brain* 'expr-pred?)]
    (is (some? (:expr entry)))))

(deftest it-must-be-that-defpredicate-allows-redefinition
  (m/defpredicate redefine-pred? [x] (string? x))
  (m/defpredicate redefine-pred? [x] (number? x))
  (is (true?  (redefine-pred? 1)))
  (is (false? (redefine-pred? "a"))))

(deftest it-must-be-that-defpredicate-attaches-symbolic-name-to-fn-metadata
  (m/defpredicate sym-name-pred? [x] (keyword? x))
  (is (= 'sym-name-pred? (-> sym-name-pred? meta :symbolic-name))))

(deftest it-must-be-that-defhandler-registers-the-handler-in-the-registry
  (m/defpredicate h-number? [x] (number? x))
  (let [op (g/make-generic-operator 1)]
    (m/defhandler op [h-number?] [x] (* x 2))
    (is (some? (protocol/p:resolve-handler! brain/*brain* 'op-h-number?)))))

(deftest it-must-be-that-defhandler-wires-the-handler-to-the-generic
  (m/defpredicate h-string? [x] (string? x))
  (let [op (g/make-generic-operator 1)]
    (m/defhandler op [h-string?] [x] (str x "!"))
    (is (= "hi!" (op "hi")))))

(deftest it-must-be-that-defrel-implied-by-registers-in-hierarchy
  (m/defrel simple?
    (implied-by number? string?))
  (is (= '[simple?] (protocol/h:implied-by brain/*brain* 'number?)))
  (is (= '[simple?] (protocol/h:implied-by brain/*brain* 'string?))))

(deftest it-must-be-that-defrel-implies-registers-in-hierarchy
  (m/defrel simple?
    (implies any?))
  (is (= '[any?] (protocol/h:implied-by brain/*brain* 'simple?))))

(deftest i-can-combine-implied-by-and-implies-in-defrel
  (m/defrel simple?
    (implied-by number?)
    (implies any?))
  (is (= '[simple?] (protocol/h:implied-by brain/*brain* 'number?)))
  (is (= '[any?]    (protocol/h:implied-by brain/*brain* 'simple?))))

(deftest it-must-be-that-defrel-is-idempotent
  (m/defrel simple? (implied-by number?))
  (m/defrel simple? (implied-by number?))
  (is (= '[simple?] (protocol/h:implied-by brain/*brain* 'number?))))
