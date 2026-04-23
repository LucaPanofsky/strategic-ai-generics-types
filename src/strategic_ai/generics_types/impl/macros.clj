(ns strategic-ai.generics-types.impl.macros
  (:require [clojure.string :as str]
            [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]
            [strategic-ai.generics-types.generics.generics   :as generics]))

(defmacro defpredicate
  "Define a named unary predicate and register it with its symbolic form.

   Example:
     (defpredicate even? [x] (even? x))
     (defpredicate composite? [x] (and (number? x) (not (prime? x))))

   Stores the original s-expression in the predicate registry under :expr
   so the symbolic form is available at runtime."
  [pred-name [arg] expr]
  `(let [f# (with-meta (fn [~arg] ~expr) {:symbolic-name '~pred-name})]
     (protocol/p:register-predicate! brain/*brain*
                                     {:key '~pred-name :impl f# :expr '~expr :arg '~arg :defining-ns *ns*})
     (def ~pred-name f#)))

(defmacro defhandler
  "Define a handler, register it, and wire it to a generic operator.
   The handler name is derived automatically as operator-pred1-pred2-...

   Arguments:
     operator   - a generic operator created by make-generic-operator
     predicates - vector of predicate fns, one per argument
     args       - argument vector
     body       - handler body forms

   Example:
     (defhandler my-op [number?] [x] (* x 2))
     => defines and registers my-op-number?"
  [operator predicates args & body]
  (let [handler-name (symbol (str (name operator) "-" (str/join "-" (map name predicates))))]
    `(let [f# (with-meta (fn ~args ~@body) {:symbolic-name '~handler-name})]
       ~@(map (fn [pred-sym]
                `(when (nil? (protocol/p:lookup-predicate brain/*brain* '~pred-sym))
                   (protocol/p:register-predicate! brain/*brain* {:key '~pred-sym :impl ~pred-sym})))
              predicates)
       (protocol/p:register-handler! brain/*brain*
                                     {:key '~handler-name :impl f# :args '~args :body '~body :defining-ns *ns*})
       (def ~handler-name f#)
       (generics/defhandler ~operator ~predicates f#))))

(defmacro defrel
  "Declare hierarchy relationships for a predicate.

   pred-sym - symbol naming the predicate
   clauses  - any of:
                (implied-by pred1 pred2 ...) — pred1, pred2 imply pred-sym
                (implies    pred1 pred2 ...) — pred-sym implies pred1, pred2

   Example:
     (defrel simple?
       (implied-by number? string? boolean?)
       (implies any?))"
  [pred-sym & clauses]
  `(do
     ~@(mapcat (fn [[tag & preds]]
                 (case tag
                   implied-by (map (fn [p] `(protocol/h:declare-implies! brain/*brain* '~p '~pred-sym)) preds)
                   implies    (map (fn [p] `(protocol/h:declare-implies! brain/*brain* '~pred-sym '~p)) preds)))
               clauses)))

