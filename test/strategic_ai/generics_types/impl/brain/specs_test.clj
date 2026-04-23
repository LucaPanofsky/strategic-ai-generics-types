(ns strategic-ai.generics-types.impl.brain.specs-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as s]
            [strategic-ai.generics-types.impl.brain.specs :as specs]))

(comment
  "predicate-entry")

(deftest it-must-be-that-a-valid-predicate-entry-conforms
  (is (s/valid? ::specs/predicate-entry
                {:key 'number? :impl number? :expr '(number? x) :arg 'x :defining-ns *ns*})))

(deftest it-must-not-be-that-a-predicate-entry-with-a-string-key-is-valid
  (is (not (s/valid? ::specs/predicate-entry
                     {:key "number?" :impl number? :expr '(number? x) :arg 'x :defining-ns *ns*}))))

(deftest it-must-not-be-that-a-predicate-entry-with-a-non-fn-impl-is-valid
  (is (not (s/valid? ::specs/predicate-entry
                     {:key 'number? :impl :not-a-fn :expr '(number? x) :arg 'x :defining-ns *ns*}))))

(deftest it-must-be-that-a-platform-predicate-entry-with-only-key-and-impl-is-valid
  (is (s/valid? ::specs/predicate-entry
                {:key 'number? :impl number?})))

(comment
  "handler-entry")

(deftest it-must-be-that-a-valid-handler-entry-conforms
  (is (s/valid? ::specs/handler-entry
                {:key 'merge-number?-interval? :impl (fn [a _] a) :args '[a b]
                 :body '[(+ a b)] :defining-ns *ns*})))

(deftest it-must-not-be-that-a-handler-entry-with-empty-args-is-valid
  (is (not (s/valid? ::specs/handler-entry
                     {:key 'merge-number? :impl (fn [x] x) :args []
                      :body '[(* x 2)] :defining-ns *ns*}))))

(deftest it-must-not-be-that-a-handler-entry-missing-body-is-valid
  (is (not (s/valid? ::specs/handler-entry
                     {:key 'merge-number? :impl (fn [x] x) :args '[x] :defining-ns *ns*}))))

(comment
  "hierarchy")

(deftest it-must-be-that-a-valid-hierarchy-map-conforms
  (is (s/valid? ::specs/hierarchy {'integer? '[number?] 'number? '[any?]})))

(deftest it-must-be-that-an-empty-hierarchy-conforms
  (is (s/valid? ::specs/hierarchy {})))

(deftest it-must-not-be-that-a-hierarchy-with-keyword-keys-is-valid
  (is (not (s/valid? ::specs/hierarchy {:integer? '[number?]}))))

(deftest it-must-not-be-that-a-hierarchy-with-non-symbol-parents-is-valid
  (is (not (s/valid? ::specs/hierarchy {'integer? [:number?]}))))

(comment
  "generic-var")

(deftest it-must-be-that-a-var-is-a-valid-generic-var
  (is (s/valid? ::specs/generic-var #'clojure.core/str)))

(deftest it-must-not-be-that-a-plain-map-is-a-valid-generic-var
  (is (not (s/valid? ::specs/generic-var {}))))

(comment
  "generic-record-content")

(deftest it-must-be-that-a-valid-generic-record-content-conforms
  (is (s/valid? ::specs/generic-record-content
                {:arity 2 :strategy :tree :name 'merge
                 :default-args '[a b] :defining-ns *ns*})))

(deftest it-must-be-that-generic-record-content-with-hierarchy-strategy-conforms
  (is (s/valid? ::specs/generic-record-content
                {:arity 1 :strategy :hierarchy :name 'content
                 :default-args '[x] :defining-ns *ns*})))

(deftest it-must-not-be-that-generic-record-content-with-unknown-strategy-is-valid
  (is (not (s/valid? ::specs/generic-record-content
                     {:arity 1 :strategy :unknown :name 'merge
                      :default-args '[x] :defining-ns *ns*}))))

(deftest it-must-not-be-that-generic-record-content-with-zero-arity-is-valid
  (is (not (s/valid? ::specs/generic-record-content
                     {:arity 0 :strategy :tree :name 'merge
                      :default-args '[] :defining-ns *ns*}))))

(comment
  "brain state")

(deftest it-must-be-that-an-empty-state-conforms
  (is (s/valid? ::specs/state
                {:predicates {} :handlers {} :hierarchy {} :plans {} :generics {}})))

(deftest it-must-not-be-that-a-state-missing-a-key-is-valid
  (is (not (s/valid? ::specs/state
                     {:predicates {} :handlers {} :hierarchy {} :plans {}}))))

(deftest it-must-be-that-a-populated-state-conforms
  (is (s/valid? ::specs/state
                {:predicates {'number? {:key 'number? :impl number? :expr '(number? x)
                                        :arg 'x :defining-ns *ns*}}
                 :handlers   {'merge-number? {:key 'merge-number? :impl (fn [x] x)
                                              :args '[x] :body '[(* x 2)] :defining-ns *ns*}}
                 :hierarchy  {'integer? '[number?]}
                 :plans      {}
                 :generics   {'str #'clojure.core/str}})))

(comment
  "conform!")

(deftest i-can-conform-a-valid-value
  (is (= {:key 'number? :impl number? :expr '(number? x) :arg 'x :defining-ns *ns*}
         (specs/conform! ::specs/predicate-entry
                         {:key 'number? :impl number? :expr '(number? x)
                          :arg 'x :defining-ns *ns*}))))

(deftest i-cannot-conform-an-invalid-value
  (is (thrown? clojure.lang.ExceptionInfo
               (specs/conform! ::specs/predicate-entry {:key "bad"}))))
