(ns strategic-ai.generics-types.infostructure.macros
  "The definfostructure macro — the main user-facing entry point for declaring
   information structures.

   Example:
     (definfostructure contradiction
       (hierarchy (implies any?) (implies some?))
       (constructor [type args])
       (generics
         (bind   [(contradiction? this) (fn? other)] this)
         (unpack [(contradiction? this) (fn? other)] this)))"
  (:require [strategic-ai.generics-types.impl.brain.brain        :as brain]
            [strategic-ai.generics-types.impl.brain.protocol     :as protocol]
            [strategic-ai.generics-types.impl.macros             :as impl-macros]
            [strategic-ai.generics-types.infostructure.compiler  :as compiler]))

(defmacro definfostructure
  "Declares an information structure by name.

   name    - symbol naming the type
   clauses - optional attributes map followed by any of:
               (hierarchy (implies <pred>) ...)
               (constructor [<arg> ...])
               (generics (<op> [(<pred> <binding>) ...] <body>) ...)

   Expands to:
     - defpredicate for the derived predicate (skipped when :platform? true)
     - defn for the constructor (skipped when absent)
     - one defhandler per generic clause entry
     - brain registration of the compiled plan

   Example:
     (definfostructure interval
       (hierarchy (implies some?))
       (constructor [lo hi])
       (generics
         (merge [(interval? this) (interval? other)]
                (merge-intervals this other))))"
  [name & clauses]
  (let [nil-type? (nil? name)
        name      (if nil-type? :nil name)
        clauses   (if nil-type?
                    (if (map? (first clauses))
                      (cons (merge (first clauses) {:platform? true}) (rest clauses))
                      (cons {:platform? true} clauses))
                    clauses)
        form (list* 'definfostructure name clauses)
        plan (compiler/parse-infostructure form)
        pred (:predicate plan)
        tag  (keyword name)]
    `(do
       ~@(when-not (:platform? plan)
           [`(impl-macros/defpredicate ~pred [~'x]
               (and (list? ~'x) (= (first ~'x) ~tag)))])
       ~@(when-let [{:keys [args body]} (:constructor plan)]
           [`(defn ~name ~args ~body)])
       ~@(map (fn [{:keys [op preds args body]}]
                `(impl-macros/defhandler ~op ~(vec preds) ~args ~body))
              (:handlers plan))
       ~@(map (fn [implied] `(protocol/h:declare-implies! brain/*brain* '~pred '~implied))
              (:implies plan))
       (protocol/i:register-plan! brain/*brain* (compiler/parse-infostructure (quote ~form))))))
