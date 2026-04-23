(ns strategic-ai.generics-types.impl.brain.brain
  "Concrete implementation of the Brain protocol.

   The brain is the single source of truth for all registry state: predicates,
   handlers, hierarchy relationships, infostructure plans, and generic operators.
   It replaces the four independent registry atoms that previously existed across
   generics.predicates, generics.handlers, generics.hierarchy, and
   infostructure.registry.

   Entry points:
     make-brain - construct a fresh empty brain instance
     *brain*    - dynamic var holding the active brain; rebind in tests via binding
     clear!     - reset all state to empty
     snapshot   - return an immutable copy of the current state"
  (:require [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(def ^:private empty-state
  {:predicates {}
   :handlers   {}
   :hierarchy  {}
   :plans      {}
   :generics   {}})

(defrecord BrainImpl [state]
  protocol/Brain

  (p:register-predicate! [brain entry]
    (swap! state assoc-in [:predicates (:key entry)] entry)
    brain)

  (p:lookup-predicate [_ key]
    (get-in @state [:predicates key]))

  (p:lookup-predicate-by-fn [_ impl-fn]
    (some (fn [[k {:keys [impl]}]]
            (when (identical? impl impl-fn) k))
          (:predicates @state)))

  (p:resolve-predicate! [_ key]
    (or (get-in @state [:predicates key])
        (throw (ex-info "Predicate not found" {:key key}))))

  (p:register-handler! [brain entry]
    (swap! state assoc-in [:handlers (:key entry)] entry)
    brain)

  (p:resolve-handler! [_ key]
    (or (get-in @state [:handlers key])
        (throw (ex-info "Handler not found" {:key key}))))

  (h:declare-implies! [brain pred-sym parent-sym]
    (swap! state update-in [:hierarchy pred-sym]
           (fn [current]
             (let [existing (or current [])]
               (if (some #{parent-sym} existing)
                 existing
                 (conj existing parent-sym)))))
    brain)

  (h:implied-by [_ pred-sym]
    (get-in @state [:hierarchy pred-sym] []))

  (h:children-of [_ parent-sym]
    (keep (fn [[child-sym implied-syms]]
            (when (some #{parent-sym} implied-syms) child-sym))
          (:hierarchy @state)))

  (i:register-plan! [brain plan]
    (swap! state assoc-in [:plans (:name plan)] plan)
    brain)

  (i:lookup-plan-by-name [_ sym]
    (get-in @state [:plans sym]))

  (i:lookup-plan-by-predicate [_ sym]
    (some #(when (= (:predicate %) sym) %) (vals (:plans @state))))

  (i:all-plans [_]
    (vals (:plans @state)))

  (g:register-generic! [brain the-var]
    (swap! state assoc-in [:generics (-> the-var meta :name)] the-var)
    brain)

  (g:all-generics [_]
    (vals (:generics @state)))

  (clear! [brain]
    (reset! state empty-state)
    brain)

  (snapshot [_]
    @state))

(defn make-brain
  "Construct and return a fresh empty brain instance.

   Example:
     (make-brain)"
  []
  (->BrainImpl (atom empty-state)))

(def ^:dynamic *brain*
  "The active brain instance. All macros write to this var at runtime.
   Rebind with binding in tests to get full isolation without clear! calls.

   Note: dynamic bindings are thread-local. Code that writes to *brain* from
   futures or agents must use bound-fn to propagate the binding.

   Example:
     (binding [*brain* (make-brain)]
       ...)"
  (make-brain))
