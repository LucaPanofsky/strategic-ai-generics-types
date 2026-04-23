(ns strategic-ai.generics-types.infostructure.compiler-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as s]
            [strategic-ai.generics-types.infostructure.specs    :as specs]
            [strategic-ai.generics-types.infostructure.compiler :as compiler]))

(deftest unit-compile-infostructure-name-only
  (let [plan (compiler/compile-infostructure
              {:name 'boolean :attributes nil :hierarchy nil :constructor nil :generics nil})]
    (is (= 'boolean  (:name plan)))
    (is (= 'boolean? (:predicate plan)))
    (is (= false     (:platform? plan)))
    (is (= []        (:implies plan)))
    (is (nil?        (:constructor plan)))
    (is (= []        (:handlers plan)))))

(deftest unit-compile-infostructure-derives-predicate-from-name
  (let [plan (compiler/compile-infostructure
              {:name 'contradiction :attributes nil :hierarchy nil :constructor nil :generics nil})]
    (is (= 'contradiction? (:predicate plan)))))

(deftest unit-compile-infostructure-derives-platform?-from-attributes
  (let [plan (compiler/compile-infostructure
              {:name 'number :attributes {:platform? true} :hierarchy nil :constructor nil :generics nil})]
    (is (true? (:platform? plan)))))

(deftest unit-compile-infostructure-platform?-defaults-to-false
  (let [plan (compiler/compile-infostructure
              {:name 'interval :attributes nil :hierarchy nil :constructor nil :generics nil})]
    (is (false? (:platform? plan)))))

(deftest unit-compile-infostructure-lifts-implies-from-hierarchy
  (let [plan (compiler/compile-infostructure
              {:name 'contradiction :attributes nil
               :hierarchy {:implies '[any? some?]}
               :constructor nil :generics nil})]
    (is (= '[any? some?] (:implies plan)))))

(deftest unit-compile-infostructure-implies-is-empty-when-no-hierarchy
  (let [plan (compiler/compile-infostructure
              {:name 'interval :attributes nil :hierarchy nil :constructor nil :generics nil})]
    (is (= [] (:implies plan)))))

(deftest unit-compile-infostructure-derives-constructor-body
  (let [plan (compiler/compile-infostructure
              {:name 'contradiction :attributes nil :hierarchy nil
               :constructor {:args '[type args]}
               :generics nil})]
    (is (= {:args '[type args]
            :body '(list :contradiction type args)}
           (:constructor plan)))))

(deftest unit-compile-infostructure-constructor-body-uses-name-as-tag
  (let [plan (compiler/compile-infostructure
              {:name 'interval :attributes nil :hierarchy nil
               :constructor {:args '[lo hi]}
               :generics nil})]
    (is (= '(list :interval lo hi) (get-in plan [:constructor :body])))))

(deftest unit-compile-infostructure-constructor-is-nil-when-absent
  (let [plan (compiler/compile-infostructure
              {:name 'number :attributes nil :hierarchy nil :constructor nil :generics nil})]
    (is (nil? (:constructor plan)))))

(deftest unit-compile-infostructure-lifts-handlers-from-generics
  (let [plan (compiler/compile-infostructure
              {:name 'contradiction :attributes nil :hierarchy nil :constructor nil
               :generics {:handlers [{:op 'bind :preds '[contradiction? fn?] :args '[this other] :body 'this}
                                     {:op 'unpack :preds '[contradiction? fn?] :args '[this other] :body 'this}]}})]
    (is (= 2 (count (:handlers plan))))
    (is (= 'bind   (:op (first (:handlers plan)))))
    (is (= 'unpack (:op (second (:handlers plan)))))))

(deftest unit-compile-infostructure-handlers-is-empty-when-no-generics
  (let [plan (compiler/compile-infostructure
              {:name 'number :attributes nil :hierarchy nil :constructor nil :generics nil})]
    (is (= [] (:handlers plan)))))

(deftest unit-compile-infostructure-preserves-attributes
  (let [attrs {:platform? true :doc "a primitive"}
        plan  (compiler/compile-infostructure
               {:name 'number :attributes attrs :hierarchy nil :constructor nil :generics nil})]
    (is (= attrs (:attributes plan)))))

(deftest it-must-be-that-compile-infostructure-produces-a-valid-plan
  (let [plan (compiler/compile-infostructure
              {:name 'contradiction :attributes nil
               :hierarchy   {:implies '[any? some?]}
               :constructor {:args '[type args]}
               :generics    {:handlers [{:op 'bind :preds '[contradiction? fn?] :args '[this other] :body 'this}]}})]
    (is (s/valid? ::specs/compilation-plan plan))))

(deftest unit-parse-infostructure-full-pipeline
  (let [plan (compiler/parse-infostructure
              '(definfostructure contradiction
                 (hierarchy (implies any?) (implies some?))
                 (constructor [type args])
                 (generics
                  (bind   [(contradiction? this) (fn? other)] this)
                  (unpack [(contradiction? this) (fn? other)] this))))]
    (is (= 'contradiction  (:name plan)))
    (is (= 'contradiction? (:predicate plan)))
    (is (= '[any? some?]   (:implies plan)))
    (is (= '[type args]    (get-in plan [:constructor :args])))
    (is (= 2               (count (:handlers plan))))
    (is (s/valid? ::specs/compilation-plan plan))))

(deftest unit-parse-infostructure-with-attributes
  (let [plan (compiler/parse-infostructure
              '(definfostructure number {:platform? true}
                 (hierarchy (implies some?))))]
    (is (= 'number         (:name plan)))
    (is (true?             (:platform? plan)))
    (is (= {:platform? true} (:attributes plan)))
    (is (= '[some?]        (:implies plan)))))

(deftest i-cannot-parse-infostructure-with-invalid-form
  (is (thrown? clojure.lang.ExceptionInfo
               (compiler/parse-infostructure '(definfostructure)))))
