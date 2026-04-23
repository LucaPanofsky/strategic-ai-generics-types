(ns strategic-ai.generics-types.infostructure.macros-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [strategic-ai.generics-types.generics.generics      :as g]
            [strategic-ai.generics-types.impl.brain.brain        :as brain]
            [strategic-ai.generics-types.impl.brain.protocol     :as protocol]
            [strategic-ai.generics-types.infostructure.compiler  :as compiler]
            [strategic-ai.generics-types.infostructure.macros    :refer [definfostructure]]))

(g/defgeneric test-describe [x]   :unknown)
(g/defgeneric test-combine  [x y] y)

(defn re-register-plans [test-fn]
  (protocol/i:register-plan! brain/*brain*
                             (compiler/parse-infostructure
                              '(definfostructure :nil {:platform? true}
                                 (hierarchy (implies any?)))))
  (protocol/h:declare-implies! brain/*brain* 'nil? 'any?)
  (protocol/i:register-plan! brain/*brain*
                             (compiler/parse-infostructure
                              '(definfostructure test-box
                                 (hierarchy (implies any?))
                                 (constructor [value])
                                 (generics
                                  (test-describe [(test-box? this)] :is-box)
                                  (test-combine  [(test-box? this) (any? other)] this)))))
  (protocol/h:declare-implies! brain/*brain* 'test-box? 'any?)
  (protocol/i:register-plan! brain/*brain*
                             (compiler/parse-infostructure
                              '(definfostructure test-bare
                                 (hierarchy (implies any?)))))
  (protocol/h:declare-implies! brain/*brain* 'test-bare? 'any?)
  (protocol/i:register-plan! brain/*brain*
                             (compiler/parse-infostructure
                              '(definfostructure test-platform {:platform? true}
                                 (hierarchy (implies any?)))))
  (test-fn))

(use-fixtures :once re-register-plans)

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(definfostructure nil
  (hierarchy (implies any?)))

(definfostructure test-box
  (hierarchy (implies any?))
  (constructor [value])
  (generics
   (test-describe [(test-box? _this)]               :is-box)
   (test-combine  [(test-box? this) (any? _other)]  this)))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(definfostructure test-bare
  (hierarchy (implies any?)))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(definfostructure test-platform {:platform? true}
  (hierarchy (implies any?)))

(deftest i-can-recognise-a-test-box-with-its-predicate
  (is (true?  (test-box? (test-box 42))))
  (is (false? (test-box? 42)))
  (is (false? (test-box? nil)))
  (is (false? (test-box? [:test-box 42]))))

(deftest i-can-construct-a-test-box
  (let [box (test-box 42)]
    (is (sequential? box))
    (is (= :test-box (first box)))
    (is (= 42 (second box)))))

(deftest it-must-be-that-definfostructure-registers-the-plan
  (is (some? (protocol/i:lookup-plan-by-name brain/*brain* 'test-box)))
  (is (= 'test-box? (:predicate (protocol/i:lookup-plan-by-name brain/*brain* 'test-box)))))

(deftest it-must-be-that-definfostructure-records-implies-in-registry
  (is (= '[any?] (:implies (protocol/i:lookup-plan-by-name brain/*brain* 'test-box)))))

(deftest i-can-use-generic-handlers-wired-by-definfostructure
  (is (= :is-box     (test-describe (test-box 42))))
  (is (= :unknown    (test-describe 42)))
  (is (= (test-box 1) (test-combine (test-box 1) (test-box 2)))))

(deftest it-must-be-that-no-constructor-is-generated-when-absent
  (is (nil? (ns-resolve *ns* 'test-bare))))

(deftest it-must-be-that-platform-type-is-marked-in-registry
  (is (true? (:platform? (protocol/i:lookup-plan-by-name brain/*brain* 'test-platform)))))

(deftest it-must-not-be-that-definfostructure-generates-predicate-for-platform-type
  (is (nil? (ns-resolve *ns* 'test-platform?))))

(deftest it-must-be-that-nil-type-is-registered-under-nil-with-nil-predicate
  (is (some? (protocol/i:lookup-plan-by-name brain/*brain* :nil)))
  (is (= 'nil? (:predicate (protocol/i:lookup-plan-by-name brain/*brain* :nil)))))

(deftest it-must-be-that-definfostructure-registers-implies-in-the-generics-hierarchy
  (is (= '[any?] (protocol/h:implied-by brain/*brain* 'test-box?))))
