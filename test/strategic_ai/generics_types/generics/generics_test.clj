(ns strategic-ai.generics-types.generics.generics-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [strategic-ai.generics-types.generics.generics   :as g]
            [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(defn with-fresh-brain [f]
  (binding [brain/*brain* (brain/make-brain)]
    (f)))

(use-fixtures :each with-fresh-brain)

(deftest unit-find-handler-returns-nil-on-empty-tree
  (is (nil? (g/find-handler [] [1]))))

(deftest unit-find-handler-matches-predicate
  (let [tree [[number? :number-handler]]]
    (is (= :number-handler (g/find-handler tree [42])))))

(deftest unit-find-handler-returns-nil-on-no-match
  (let [tree [[string? :string-handler]]]
    (is (nil? (g/find-handler tree [42])))))

(deftest unit-find-handler-matches-nested-predicates
  (let [tree [[number? [[string? :handler]]]]]
    (is (= :handler (g/find-handler tree [1 "a"])))
    (is (nil? (g/find-handler tree [1 2])))))

(deftest unit-find-handler-returns-leaf-when-args-exhausted
  (let [tree [[number? :leaf]]]
    (is (= :leaf (g/find-handler tree [5])))))

(deftest unit-bind-in-tree-inserts-new-entry
  (let [tree (g/bind-in-tree [number?] :handler [])]
    (is (= 1 (count tree)))
    (is (= number? (ffirst tree)))
    (is (= :handler (second (first tree))))))

(deftest unit-bind-in-tree-reuses-existing-subtree-for-same-predicate
  (let [tree  (g/bind-in-tree [number? string?] :h1 [])
        tree2 (g/bind-in-tree [number? keyword?] :h2 tree)]
    (is (= 1 (count tree2)))
    (is (= 2 (count (second (first tree2)))))))

(deftest unit-bind-in-tree-replaces-handler-for-same-predicates
  (let [tree  (g/bind-in-tree [number?] :old [])
        tree2 (g/bind-in-tree [number?] :new tree)]
    (is (= :new (g/find-handler tree2 [1])))))

(deftest i-can-create-a-generic-operator
  (let [op (g/make-generic-operator 1)]
    (is (fn? op))
    (is (some? (-> op meta :generic-record)))))

(deftest i-cannot-call-a-generic-operator-with-wrong-arity
  (let [op (g/make-generic-operator 2)]
    (is (thrown? Exception (op 1)))))

(deftest unit-arity-compiler-calls-unary-fn-directly
  (let [op (g/make-generic-operator 1)]
    (g/defhandler op [number?] (fn [x] (* x 10)))
    (is (= 50 (op 5)))))

(deftest unit-arity-compiler-calls-binary-fn-directly
  (let [op (g/make-generic-operator 2)]
    (g/defhandler op [number? string?] (fn [n s] (str n s)))
    (is (= "7a" (op 7 "a")))))

(deftest i-cannot-call-a-generic-operator-with-no-matching-handler
  (let [op (g/make-generic-operator 1)]
    (is (thrown? clojure.lang.ExceptionInfo (op 42)))))

(deftest i-can-call-a-generic-operator-with-a-default-fn
  (let [op (g/make-generic-operator 1 (fn [x] (* x 2)))]
    (is (= 10 (op 5)))))

(deftest i-can-register-and-dispatch-a-handler
  (let [op (g/make-generic-operator 1)]
    (g/defhandler op [number?] (fn [x] (* x 3)))
    (is (= 12 (op 4)))))

(deftest i-can-register-multiple-handlers-and-dispatch-correctly
  (let [op (g/make-generic-operator 1)]
    (g/defhandler op [number?]  (fn [_] :number))
    (g/defhandler op [string?]  (fn [_] :string))
    (g/defhandler op [keyword?] (fn [_] :keyword))
    (is (= :number  (op 1)))
    (is (= :string  (op "a")))
    (is (= :keyword (op :k)))))

(deftest i-can-register-a-multi-arity-handler
  (let [op (g/make-generic-operator 2)]
    (g/defhandler op [number? string?] (fn [n s] [n s]))
    (is (= [1 "a"] (op 1 "a")))))

(deftest i-cannot-register-a-handler-with-wrong-predicate-count
  (let [op (g/make-generic-operator 2)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (g/defhandler op [number?] (fn [x] x))))))

(deftest i-cannot-call-defhandler-on-a-plain-fn
  (is (thrown? clojure.lang.ExceptionInfo
               (g/defhandler (fn [x] x) [number?] (fn [x] x)))))

(deftest it-must-be-that-last-registered-handler-wins
  (let [op (g/make-generic-operator 1)]
    (g/defhandler op [number?] (fn [_] :first))
    (g/defhandler op [number?] (fn [_] :second))
    (is (= :second (op 1)))))

(deftest unit-defgeneric-creates-a-named-generic-operator
  (g/defgeneric my-op [x])
  (g/defhandler my-op [number?] (fn [x] (* x 10)))
  (is (= 50 (my-op 5))))

(deftest unit-defgeneric-with-default-uses-default-fn
  (g/defgeneric my-op-default [x] (* x -1))
  (is (= -7 (my-op-default 7))))

(deftest i-can-create-a-hierarchy-strategy-generic-operator
  (let [op (g/make-generic-operator 1 nil nil {:strategy :hierarchy})]
    (is (fn? op))
    (is (= :hierarchy (g/strategy op)))))

(deftest it-must-be-that-tree-strategy-is-the-default
  (let [op (g/make-generic-operator 1)]
    (is (= :tree (g/strategy op)))))

(deftest it-must-be-that-hierarchy-strategy-dispatches-via-implied-predicate
  (let [animal-fn (with-meta (fn [x] (= x ::animal)) {:symbolic-name 'test-animal?})
        dog-fn    (with-meta (fn [x] (= x ::dog))    {:symbolic-name 'test-dog?})]
    (protocol/p:register-predicate! brain/*brain* {:key 'test-animal? :impl animal-fn})
    (protocol/p:register-predicate! brain/*brain* {:key 'test-dog?    :impl dog-fn})
    (protocol/h:declare-implies! brain/*brain* 'test-dog? 'test-animal?)
    (let [op (g/make-generic-operator 1 (fn [_] :unknown) nil {:strategy :hierarchy})]
      (g/defhandler op [animal-fn] (fn [_] :animal))
      (is (= :animal  (op ::animal)))
      (is (= :animal  (op ::dog)))
      (is (= :unknown (op ::cat))))))

(deftest it-must-be-that-tree-strategy-does-not-use-hierarchy
  (let [animal-fn (with-meta (fn [x] (= x ::animal)) {:symbolic-name 'test-animal?})
        dog-fn    (with-meta (fn [x] (= x ::dog))    {:symbolic-name 'test-dog?})]
    (protocol/p:register-predicate! brain/*brain* {:key 'test-animal? :impl animal-fn})
    (protocol/p:register-predicate! brain/*brain* {:key 'test-dog?    :impl dog-fn})
    (protocol/h:declare-implies! brain/*brain* 'test-dog? 'test-animal?)
    (let [op (g/make-generic-operator 1 (fn [_] :unknown))]
      (g/defhandler op [animal-fn] (fn [_] :animal))
      (is (= :animal  (op ::animal)))
      (is (= :unknown (op ::dog))))))

(deftest it-must-be-that-direct-handler-takes-priority-over-hierarchy-in-hierarchy-strategy
  (let [animal-fn (with-meta (fn [x] (= x ::animal)) {:symbolic-name 'test-animal?})
        dog-fn    (with-meta (fn [x] (= x ::dog))    {:symbolic-name 'test-dog?})]
    (protocol/p:register-predicate! brain/*brain* {:key 'test-animal? :impl animal-fn})
    (protocol/p:register-predicate! brain/*brain* {:key 'test-dog?    :impl dog-fn})
    (protocol/h:declare-implies! brain/*brain* 'test-dog? 'test-animal?)
    (let [op (g/make-generic-operator 1 (fn [_] :unknown) nil {:strategy :hierarchy})]
      (g/defhandler op [animal-fn] (fn [_] :animal))
      (g/defhandler op [dog-fn]    (fn [_] :dog))
      (is (= :dog    (op ::dog)))
      (is (= :animal (op ::animal))))))

(deftest i-can-use-defgeneric-with-strategy-option
  (g/defgeneric my-hierarchy-op [x] {:strategy :hierarchy} :unknown)
  (is (= :hierarchy (g/strategy my-hierarchy-op))))
