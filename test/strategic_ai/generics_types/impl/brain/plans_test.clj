(ns strategic-ai.generics-types.impl.brain.plans-test
  (:require [clojure.test :refer [deftest is]]
            [strategic-ai.generics-types.impl.brain.brain :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(def ^:private nil-plan
  {:name 'nil :platform? true :predicate 'nil? :implies '[any?]
   :attributes nil :handlers [] :constructor nil})

(def ^:private contradiction-plan
  {:name 'contradiction :platform? true :predicate 'contradiction? :implies '[any?]
   :attributes nil :handlers [] :constructor nil})

(comment
  "i:register-plan! / i:lookup-plan-by-name")

(deftest i-can-register-and-lookup-a-plan-by-name
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/i:register-plan! brain/*brain* nil-plan)
    (is (= nil-plan (protocol/i:lookup-plan-by-name brain/*brain* 'nil)))))

(deftest i-cannot-lookup-a-plan-that-is-not-registered
  (binding [brain/*brain* (brain/make-brain)]
    (is (nil? (protocol/i:lookup-plan-by-name brain/*brain* 'unknown)))))

(deftest it-must-be-that-registering-the-same-plan-name-replaces-the-entry
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/i:register-plan! brain/*brain* nil-plan)
    (let [updated (assoc nil-plan :platform? false)]
      (protocol/i:register-plan! brain/*brain* updated)
      (is (false? (:platform? (protocol/i:lookup-plan-by-name brain/*brain* 'nil)))))))

(comment
  "i:lookup-plan-by-predicate")

(deftest i-can-lookup-a-plan-by-its-predicate-symbol
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/i:register-plan! brain/*brain* nil-plan)
    (is (= nil-plan (protocol/i:lookup-plan-by-predicate brain/*brain* 'nil?)))))

(deftest i-cannot-lookup-a-plan-by-an-unknown-predicate
  (binding [brain/*brain* (brain/make-brain)]
    (is (nil? (protocol/i:lookup-plan-by-predicate brain/*brain* 'unknown?)))))

(comment
  "i:all-plans")

(deftest i-can-get-all-plans
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/i:register-plan! brain/*brain* nil-plan)
    (protocol/i:register-plan! brain/*brain* contradiction-plan)
    (is (= #{nil-plan contradiction-plan} (set (protocol/i:all-plans brain/*brain*))))))

(deftest it-must-be-that-all-plans-returns-empty-when-no-plans-are-registered
  (binding [brain/*brain* (brain/make-brain)]
    (is (empty? (protocol/i:all-plans brain/*brain*)))))

(comment
  "binding isolation")

(deftest it-must-be-that-plan-bindings-are-isolated
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/i:register-plan! brain/*brain* nil-plan)
    (binding [brain/*brain* (brain/make-brain)]
      (is (nil? (protocol/i:lookup-plan-by-name brain/*brain* 'nil))))))
