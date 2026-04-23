(ns strategic-ai.generics-types.impl.brain.predicates-handlers-test
  (:require [clojure.test :refer [deftest is]]
            [strategic-ai.generics-types.impl.brain.brain :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(def ^:private pred-entry
  {:key 'number? :impl number? :expr '(number? x) :arg 'x :defining-ns *ns*})

(def ^:private handler-entry
  {:key 'merge-number? :impl (fn [x] (* x 2)) :args '[x] :body '[(* x 2)] :defining-ns *ns*})

(comment
  "p:register-predicate! / p:lookup-predicate")

(deftest i-can-register-and-lookup-a-predicate
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/p:register-predicate! brain/*brain* pred-entry)
    (is (= pred-entry (protocol/p:lookup-predicate brain/*brain* 'number?)))))

(deftest i-cannot-lookup-a-predicate-that-is-not-registered
  (binding [brain/*brain* (brain/make-brain)]
    (is (nil? (protocol/p:lookup-predicate brain/*brain* 'unknown?)))))

(deftest it-must-be-that-registering-the-same-predicate-key-replaces-the-entry
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/p:register-predicate! brain/*brain* pred-entry)
    (let [updated (assoc pred-entry :expr '(updated x))]
      (protocol/p:register-predicate! brain/*brain* updated)
      (is (= '(updated x) (:expr (protocol/p:lookup-predicate brain/*brain* 'number?)))))))

(comment
  "p:lookup-predicate-by-fn")

(deftest i-can-lookup-a-predicate-by-its-fn
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/p:register-predicate! brain/*brain* pred-entry)
    (is (= 'number? (protocol/p:lookup-predicate-by-fn brain/*brain* number?)))))

(deftest i-cannot-lookup-a-predicate-by-an-unregistered-fn
  (binding [brain/*brain* (brain/make-brain)]
    (is (nil? (protocol/p:lookup-predicate-by-fn brain/*brain* number?)))))

(deftest it-must-be-that-lookup-by-fn-uses-identity
  (binding [brain/*brain* (brain/make-brain)]
    (let [other-number? (fn [x] (number? x))]
      (protocol/p:register-predicate! brain/*brain* pred-entry)
      (is (nil? (protocol/p:lookup-predicate-by-fn brain/*brain* other-number?))))))

(comment
  "p:resolve-predicate!")

(deftest i-can-resolve-a-registered-predicate
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/p:register-predicate! brain/*brain* pred-entry)
    (is (= pred-entry (protocol/p:resolve-predicate! brain/*brain* 'number?)))))

(deftest i-cannot-resolve-a-predicate-that-is-not-registered
  (binding [brain/*brain* (brain/make-brain)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (protocol/p:resolve-predicate! brain/*brain* 'unknown?)))))

(comment
  "p:register-handler! / p:resolve-handler!")

(deftest i-can-register-and-resolve-a-handler
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/p:register-handler! brain/*brain* handler-entry)
    (is (= handler-entry (protocol/p:resolve-handler! brain/*brain* 'merge-number?)))))

(deftest i-cannot-resolve-a-handler-that-is-not-registered
  (binding [brain/*brain* (brain/make-brain)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (protocol/p:resolve-handler! brain/*brain* 'unknown?)))))

(deftest it-must-be-that-registering-the-same-handler-key-replaces-the-entry
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/p:register-handler! brain/*brain* handler-entry)
    (let [updated (assoc handler-entry :args '[a])]
      (protocol/p:register-handler! brain/*brain* updated)
      (is (= '[a] (:args (protocol/p:resolve-handler! brain/*brain* 'merge-number?)))))))

(comment
  "binding isolation")

(deftest it-must-be-that-bindings-are-isolated-from-each-other
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/p:register-predicate! brain/*brain* pred-entry)
    (binding [brain/*brain* (brain/make-brain)]
      (is (nil? (protocol/p:lookup-predicate brain/*brain* 'number?))))))
