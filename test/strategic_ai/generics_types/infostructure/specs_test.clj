(ns strategic-ai.generics-types.infostructure.specs-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as s]
            [strategic-ai.generics-types.infostructure.specs :as specs]))

(deftest unit-implies-clause-conforms
  (let [result (s/conform ::specs/implies-clause '(implies some?))]
    (is (not= ::s/invalid result))
    (is (= 'some? (:pred result)))))

(deftest i-cannot-conform-implies-without-predicate
  (is (= ::s/invalid (s/conform ::specs/implies-clause '(implies)))))

(deftest i-cannot-conform-implies-with-multiple-predicates
  (is (= ::s/invalid (s/conform ::specs/implies-clause '(implies some? numeric?)))))

(deftest i-cannot-conform-implies-with-wrong-tag
  (is (= ::s/invalid (s/conform ::specs/implies-clause '(extends some?)))))

(deftest unit-hierarchy-clause-conforms-single-parent
  (let [result (s/conform ::specs/hierarchy-clause '(hierarchy (implies some?)))]
    (is (not= ::s/invalid result))
    (is (= '[some?] (:implies result)))))

(deftest unit-hierarchy-clause-conforms-multiple-parents
  (let [result (s/conform ::specs/hierarchy-clause
                          '(hierarchy (implies some?) (implies numeric?)))]
    (is (= '[some? numeric?] (:implies result)))))

(deftest i-cannot-conform-hierarchy-without-any-implies
  (is (= ::s/invalid (s/conform ::specs/hierarchy-clause '(hierarchy)))))

(deftest i-cannot-conform-hierarchy-with-wrong-tag
  (is (= ::s/invalid (s/conform ::specs/hierarchy-clause '(parents (implies some?))))))

(deftest unit-constructor-clause-conforms
  (let [result (s/conform ::specs/constructor-clause '(constructor [type args]))]
    (is (not= ::s/invalid result))
    (is (= '[type args] (:args result)))))

(deftest unit-constructor-clause-conforms-single-arg
  (is (= '[value] (:args (s/conform ::specs/constructor-clause '(constructor [value]))))))

(deftest i-cannot-conform-constructor-with-empty-args
  (is (= ::s/invalid (s/conform ::specs/constructor-clause '(constructor [])))))

(deftest i-cannot-conform-constructor-without-vector
  (is (= ::s/invalid (s/conform ::specs/constructor-clause '(constructor type args)))))

(deftest unit-pred-binding-conforms
  (let [result (s/conform ::specs/pred-binding '(contradiction? x))]
    (is (not= ::s/invalid result))
    (is (= 'contradiction? (:pred result)))
    (is (= 'x (:binding result)))))

(deftest unit-pred-binding-accepts-arbitrary-binding-names
  (is (= 'this  (:binding (s/conform ::specs/pred-binding '(nil? this)))))
  (is (= 'other (:binding (s/conform ::specs/pred-binding '(any? other)))))
  (is (= 'j     (:binding (s/conform ::specs/pred-binding '(contradiction? j))))))

(deftest i-cannot-conform-pred-binding-with-keyword-as-pred
  (is (= ::s/invalid (s/conform ::specs/pred-binding '(:contradiction? x)))))

(deftest i-cannot-conform-pred-binding-with-keyword-as-binding
  (is (= ::s/invalid (s/conform ::specs/pred-binding '(contradiction? :x)))))

(deftest unit-handler-conforms
  (let [result (s/conform ::specs/handler '(merge [(contradiction? x) (any? y)] x))]
    (is (not= ::s/invalid result))
    (is (= 'merge                 (:op result)))
    (is (= '[contradiction? any?] (:preds result)))
    (is (= '[x y]                 (:args result)))
    (is (= 'x                     (:body result)))))

(deftest unit-handler-conforms-with-single-predicate
  (let [result (s/conform ::specs/handler '(content [(number? x)] (* x 2)))]
    (is (= 'content   (:op result)))
    (is (= '[number?] (:preds result)))
    (is (= '[x]       (:args result)))
    (is (= '(* x 2)   (:body result)))))

(deftest unit-handler-conforms-with-complex-body
  (let [result (s/conform ::specs/handler
                          '(merge [(interval? this) (interval? other)]
                                  (merge-intervals this other)))]
    (is (= 'merge                        (:op result)))
    (is (= '[interval? interval?]        (:preds result)))
    (is (= '[this other]                 (:args result)))
    (is (= '(merge-intervals this other) (:body result)))))

(deftest it-must-be-that-handler-preds-and-args-have-equal-count
  (let [result (s/conform ::specs/handler '(merge [(contradiction? x) (any? y)] x))]
    (is (= (count (:preds result)) (count (:args result))))))

(deftest i-cannot-conform-handler-with-empty-predicates-vector
  (is (= ::s/invalid (s/conform ::specs/handler '(merge [] body)))))

(deftest i-cannot-conform-handler-with-bare-predicate-symbols
  (is (= ::s/invalid (s/conform ::specs/handler '(merge [contradiction? any?] body)))))

(deftest unit-generics-clause-conforms-single-handler
  (let [result (s/conform ::specs/generics-clause
                          '(generics (bind [(nil? this) (fn? other)] this)))]
    (is (not= ::s/invalid result))
    (is (= 1 (count (:handlers result))))))

(deftest unit-generics-clause-conforms-multiple-handlers
  (let [result (s/conform ::specs/generics-clause
                          '(generics
                            (bind   [(contradiction? this) (fn? other)] this)
                            (unpack [(contradiction? this) (fn? other)] this)))]
    (is (= 2 (count (:handlers result))))))

(deftest unit-generics-clause-allows-multiple-handlers-for-same-op
  (let [result (s/conform ::specs/generics-clause
                          '(generics
                            (merge [(contradiction? this) (any? other)] this)
                            (merge [(any? this) (contradiction? other)] other)))]
    (is (= 2 (count (:handlers result))))
    (is (every? #(= 'merge (:op %)) (:handlers result)))))

(deftest i-cannot-conform-generics-without-handlers
  (is (= ::s/invalid (s/conform ::specs/generics-clause '(generics)))))

(deftest i-cannot-conform-generics-with-wrong-tag
  (is (= ::s/invalid (s/conform ::specs/generics-clause
                                '(methods (bind [(nil? this) (fn? other)] this))))))

(deftest unit-infostructure-conforms-full-form
  (let [result (s/conform ::specs/infostructure
                          '(definfostructure contradiction
                             (hierarchy
                              (implies any?)
                              (implies some?))
                             (constructor [type args])
                             (generics
                              (bind   [(contradiction? this) (fn? other)] this)
                              (unpack [(contradiction? this) (fn? other)] this))))]
    (is (not= ::s/invalid result))
    (is (= 'contradiction (:name result)))
    (is (= '[any? some?]  (get-in result [:hierarchy :implies])))
    (is (= '[type args]   (get-in result [:constructor :args])))
    (is (= 2              (count (get-in result [:generics :handlers]))))))

(deftest unit-infostructure-conforms-hierarchy-only
  (let [result (s/conform ::specs/infostructure
                          '(definfostructure number
                             (hierarchy (implies some?))))]
    (is (not= ::s/invalid result))
    (is (= 'number  (:name result)))
    (is (= '[some?] (get-in result [:hierarchy :implies])))
    (is (nil? (:constructor result)))
    (is (nil? (:generics result)))))

(deftest unit-infostructure-conforms-generics-only
  (let [result (s/conform ::specs/infostructure
                          '(definfostructure interval
                             (generics
                              (merge [(interval? this) (interval? other)]
                                     (merge-intervals this other)))))]
    (is (not= ::s/invalid result))
    (is (= 'interval (:name result)))
    (is (nil? (:hierarchy result)))
    (is (nil? (:constructor result)))
    (is (= 1 (count (get-in result [:generics :handlers]))))))

(deftest unit-infostructure-conforms-name-only
  (let [result (s/conform ::specs/infostructure '(definfostructure boolean))]
    (is (not= ::s/invalid result))
    (is (= 'boolean (:name result)))
    (is (nil? (:attributes result)))
    (is (nil? (:hierarchy result)))
    (is (nil? (:constructor result)))
    (is (nil? (:generics result)))))

(deftest unit-infostructure-conforms-with-attributes
  (let [result (s/conform ::specs/infostructure
                          '(definfostructure foo {:platform? true :doc "a primitive"}))]
    (is (not= ::s/invalid result))
    (is (= 'foo (:name result)))
    (is (= {:platform? true :doc "a primitive"} (:attributes result)))
    (is (nil? (:hierarchy result)))))

(deftest unit-infostructure-conforms-with-attributes-and-clauses
  (let [result (s/conform ::specs/infostructure
                          '(definfostructure number {:platform? true}
                             (hierarchy (implies some?))))]
    (is (not= ::s/invalid result))
    (is (= 'number (:name result)))
    (is (= {:platform? true} (:attributes result)))
    (is (= '[some?] (get-in result [:hierarchy :implies])))))

(deftest it-must-be-that-absent-attributes-map-does-not-interfere-with-clauses
  (let [result (s/conform ::specs/infostructure
                          '(definfostructure number
                             (hierarchy (implies some?))))]
    (is (nil? (:attributes result)))
    (is (= '[some?] (get-in result [:hierarchy :implies])))))

(deftest i-cannot-conform-infostructure-without-name
  (is (= ::s/invalid (s/conform ::specs/infostructure
                                '(definfostructure (hierarchy (implies some?)))))))

(deftest i-cannot-conform-infostructure-without-tag
  (is (= ::s/invalid (s/conform ::specs/infostructure
                                '(infostructure number (hierarchy (implies some?)))))))

(deftest unit-plan-handler-is-valid
  (is (s/valid? ::specs/plan-handler
                {:op 'merge :preds '[contradiction? any?] :args '[x y] :body 'x})))

(deftest it-must-not-be-that-plan-handler-is-valid-without-op
  (is (not (s/valid? ::specs/plan-handler
                     {:preds '[contradiction? any?] :args '[x y] :body 'x}))))

(deftest it-must-not-be-that-plan-handler-is-valid-with-empty-preds
  (is (not (s/valid? ::specs/plan-handler
                     {:op 'merge :preds '[] :args '[] :body 'x}))))

(deftest unit-compilation-plan-conforms-full-plan
  (is (s/valid? ::specs/compilation-plan
                {:name        'contradiction
                 :platform?   false
                 :predicate   'contradiction?
                 :attributes  nil
                 :implies     '[any? some?]
                 :constructor {:args '[type args]
                               :body '(list :contradiction type args)}
                 :handlers    [{:op 'bind :preds '[contradiction? fn?] :args '[this other] :body 'this}]})))

(deftest unit-compilation-plan-conforms-platform-type
  (is (s/valid? ::specs/compilation-plan
                {:name        'boolean
                 :platform?   true
                 :predicate   'boolean?
                 :attributes  nil
                 :implies     []
                 :constructor nil
                 :handlers    []})))

(deftest unit-compilation-plan-conforms-with-attributes
  (is (s/valid? ::specs/compilation-plan
                {:name        'number
                 :platform?   true
                 :predicate   'number?
                 :attributes  {:doc "primitive number type"}
                 :implies     '[some?]
                 :constructor nil
                 :handlers    []})))

(deftest it-must-not-be-that-compilation-plan-is-valid-without-name
  (is (not (s/valid? ::specs/compilation-plan
                     {:platform?   false
                      :predicate   'contradiction?
                      :attributes  nil
                      :implies     []
                      :constructor nil
                      :handlers    []}))))

(deftest it-must-not-be-that-compilation-plan-is-valid-without-predicate
  (is (not (s/valid? ::specs/compilation-plan
                     {:name        'contradiction
                      :platform?   false
                      :attributes  nil
                      :implies     []
                      :constructor nil
                      :handlers    []}))))

(deftest it-must-not-be-that-compilation-plan-is-valid-without-platform?
  (is (not (s/valid? ::specs/compilation-plan
                     {:name        'contradiction
                      :predicate   'contradiction?
                      :attributes  nil
                      :implies     []
                      :constructor nil
                      :handlers    []}))))

(deftest unit-conform!-returns-conformed-value-on-valid-form
  (is (map? (specs/conform! ::specs/infostructure
                            '(definfostructure number (hierarchy (implies some?)))))))

(deftest i-cannot-conform!-invalid-form
  (is (thrown? clojure.lang.ExceptionInfo
               (specs/conform! ::specs/infostructure '(definfostructure)))))
