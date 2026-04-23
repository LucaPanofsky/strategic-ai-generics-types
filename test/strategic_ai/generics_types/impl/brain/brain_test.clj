(ns strategic-ai.generics-types.impl.brain.brain-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as s]
            [strategic-ai.generics-types.impl.brain.brain :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]
            [strategic-ai.generics-types.impl.brain.specs :as specs]))

(comment
  "make-brain")

(deftest i-can-create-a-brain
  (is (some? (brain/make-brain))))

(deftest it-must-be-that-a-new-brain-snapshot-satisfies-the-state-spec
  (is (s/valid? ::specs/state (protocol/snapshot (brain/make-brain)))))

(deftest it-must-be-that-a-new-brain-has-empty-predicates
  (is (empty? (:predicates (protocol/snapshot (brain/make-brain))))))

(deftest it-must-be-that-a-new-brain-has-empty-handlers
  (is (empty? (:handlers (protocol/snapshot (brain/make-brain))))))

(deftest it-must-be-that-a-new-brain-has-empty-hierarchy
  (is (empty? (:hierarchy (protocol/snapshot (brain/make-brain))))))

(deftest it-must-be-that-a-new-brain-has-empty-plans
  (is (empty? (:plans (protocol/snapshot (brain/make-brain))))))

(deftest it-must-be-that-a-new-brain-has-empty-generics
  (is (empty? (:generics (protocol/snapshot (brain/make-brain))))))

(comment
  "snapshot")

(deftest it-must-be-that-snapshot-returns-a-map
  (is (map? (protocol/snapshot (brain/make-brain)))))

(deftest it-must-be-that-snapshot-has-exactly-the-five-state-keys
  (is (= #{:predicates :handlers :hierarchy :plans :generics}
         (set (keys (protocol/snapshot (brain/make-brain)))))))

(comment
  "clear!")

(deftest i-can-clear-a-brain
  (is (some? (protocol/clear! (brain/make-brain)))))

(deftest it-must-be-that-clearing-a-brain-restores-empty-state
  (let [the-brain (brain/make-brain)]
    (protocol/clear! the-brain)
    (is (s/valid? ::specs/state (protocol/snapshot the-brain)))))

(comment
  "*brain*")

(deftest it-must-be-that-*brain*-is-bound-to-a-brain-by-default
  (is (some? brain/*brain*)))

(deftest it-must-be-that-*brain*-is-dynamic
  (let [fresh (brain/make-brain)]
    (binding [brain/*brain* fresh]
      (is (identical? fresh brain/*brain*)))))

(deftest it-must-be-that-binding-*brain*-is-isolated-from-the-root
  (let [root    brain/*brain*
        fresh   (brain/make-brain)]
    (binding [brain/*brain* fresh]
      (is (not (identical? root brain/*brain*))))
    (is (identical? root brain/*brain*))))
