(ns strategic-ai.generics-types.impl.brain.generics-test
  (:require [clojure.test :refer [deftest is]]
            [strategic-ai.generics-types.impl.brain.brain :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(def ^:private test-var-a :placeholder)
(def ^:private test-var-b :placeholder)

(comment
  "g:register-generic! / g:all-generics")

(deftest i-can-register-a-generic-and-retrieve-it
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/g:register-generic! brain/*brain* #'test-var-a)
    (is (= #{#'test-var-a} (set (protocol/g:all-generics brain/*brain*))))))

(deftest it-must-be-that-all-generics-returns-empty-when-none-are-registered
  (binding [brain/*brain* (brain/make-brain)]
    (is (empty? (protocol/g:all-generics brain/*brain*)))))

(deftest i-can-register-multiple-generics
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/g:register-generic! brain/*brain* #'test-var-a)
    (protocol/g:register-generic! brain/*brain* #'test-var-b)
    (is (= #{#'test-var-a #'test-var-b} (set (protocol/g:all-generics brain/*brain*))))))

(deftest it-must-be-that-registering-the-same-var-twice-results-in-one-entry
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/g:register-generic! brain/*brain* #'test-var-a)
    (protocol/g:register-generic! brain/*brain* #'test-var-a)
    (is (= 1 (count (protocol/g:all-generics brain/*brain*))))))

(comment
  "binding isolation")

(deftest it-must-be-that-generic-bindings-are-isolated
  (binding [brain/*brain* (brain/make-brain)]
    (protocol/g:register-generic! brain/*brain* #'test-var-a)
    (binding [brain/*brain* (brain/make-brain)]
      (is (empty? (protocol/g:all-generics brain/*brain*))))))
