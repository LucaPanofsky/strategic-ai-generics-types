(ns strategic-ai.generics-types.impl.brain.protocol
  "The Brain protocol — the single source of truth for all registry state.

   Prior to this abstraction, predicates, handlers, hierarchy relationships,
   infostructure plans, and generic operators each lived in independent atoms
   with no shared coordination point. This made test isolation expensive
   (four separate clear! calls), composition impossible without manual merging,
   and generic discovery dependent on scanning every loaded namespace.

   Brain solves this by owning all state in one place. Macros (defpredicate,
   defhandler, defrel, defgeneric, definfostructure) write into a Brain
   instance. The compiler reads from it. Tests reset a single Brain.
   Composition is snapshot merge.

   Method naming convention:
     p: — predicate and handler operations (predicate-dispatch layer)
     h: — hierarchy operations
     i: — infostructure plan operations
     g: — generic operator operations
     (no prefix) — lifecycle operations")

(defprotocol Brain

  (p:register-predicate!
    [brain entry]
    "Register a predicate. Replaces any existing entry with the same key.

     entry — map with keys:
       :key         symbol   name of the predicate (e.g. 'number?)
       :impl        fn       the predicate function
       :expr        form     source expression used to reconstruct the defn at compile time
       :arg         symbol   argument name (e.g. 'x)
       :defining-ns ns       namespace in which defpredicate was called

     Returns the brain.

     Example:
       (p:register-predicate! brain {:key 'even? :impl even? :expr '(even? x) :arg 'x :defining-ns *ns*})")

  (p:lookup-predicate
    [brain key]
    "Return the predicate entry for key, or nil if not registered.

     key — symbol naming the predicate (e.g. 'number?)

     Example:
       (p:lookup-predicate brain 'number?)")

  (p:lookup-predicate-by-fn
    [brain impl-fn]
    "Return the key symbol for a registered predicate fn, or nil.
     Uses identical? for comparison.

     impl-fn — the predicate function object

     Example:
       (p:lookup-predicate-by-fn brain number?) ;=> 'number?")

  (p:resolve-predicate!
    [brain key]
    "Return the predicate entry for key, or throw if not registered.

     key — symbol naming the predicate (e.g. 'number?)

     Example:
       (p:resolve-predicate! brain 'number?)")

  (p:register-handler!
    [brain entry]
    "Register a handler. Replaces any existing entry with the same key.

     entry — map with keys:
       :key         symbol         derived name (e.g. 'merge-number?-interval?)
       :impl        fn             the handler function
       :args        [symbol]       argument vector
       :body        [form]         body forms (seq from & body)
       :defining-ns ns             namespace in which defhandler was called

     Returns the brain.

     Example:
       (p:register-handler! brain {:key    'merge-number?-interval?
                                   :impl   handler-fn
                                   :args   '[a b]
                                   :body   '[(+ a b)]
                                   :defining-ns *ns*})")

  (p:resolve-handler!
    [brain key]
    "Return the handler entry for key, or throw if not registered.

     key — symbol naming the handler (e.g. 'merge-number?-interval?)

     Example:
       (p:resolve-handler! brain 'merge-number?-interval?)")

  (h:declare-implies!
    [brain pred-sym parent-sym]
    "Register that pred-sym implies parent-sym.
     Idempotent: declaring the same relationship twice has no effect.

     pred-sym   — symbol naming the child predicate (e.g. 'integer?)
     parent-sym — symbol naming the implied predicate (e.g. 'number?)

     Example:
       (h:declare-implies! brain 'integer? 'number?)")

  (h:implied-by
    [brain pred-sym]
    "Return the vector of predicate symbols directly implied by pred-sym,
     or [] if none are registered.

     pred-sym — symbol naming the predicate

     Example:
       (h:implied-by brain 'integer?) ;=> '[number?]")

  (h:children-of
    [brain parent-sym]
    "Return a sequence of predicate symbols that directly imply parent-sym.

     parent-sym — symbol naming the parent predicate

     Example:
       (h:children-of brain 'number?) ;=> ('integer? 'float?)")

  (i:register-plan!
    [brain plan]
    "Register an infostructure compilation plan. Replaces any existing plan with the same name.

     plan — map satisfying ::infostructure.specs/compilation-plan, with keys:
       :name        symbol         name of the infostructure (e.g. 'nil)
       :platform?   boolean        true for built-in platform types
       :predicate   symbol         predicate symbol (e.g. 'nil?)
       :implies     [symbol]       parent predicates in the hierarchy
       :attributes  map or nil     arbitrary attributes from the DSL
       :handlers    [handler-plan] compiled handler plans
       :constructor map or nil     {:args [sym] :body form}

     Example:
       (i:register-plan! brain {:name 'nil :platform? true :predicate 'nil? :implies '[any?]
                                :attributes nil :handlers [] :constructor nil})")

  (i:lookup-plan-by-name
    [brain sym]
    "Return the plan registered under sym, or nil.

     sym — symbol naming the infostructure (e.g. 'nil)

     Example:
       (i:lookup-plan-by-name brain 'nil)")

  (i:lookup-plan-by-predicate
    [brain sym]
    "Return the plan whose :predicate matches sym, or nil.

     sym — predicate symbol (e.g. 'nil?)

     Example:
       (i:lookup-plan-by-predicate brain 'nil?)")

  (i:all-plans
    [brain]
    "Return a sequence of all registered infostructure plans.

     Example:
       (i:all-plans brain)")

  (g:register-generic!
    [brain the-var]
    "Enroll a generic operator var in the brain at defgeneric time.
     Eliminates the need for namespace scanning at compile time.

     the-var — a Clojure var created by defgeneric; its metadata must carry
               :name (symbol) so the brain can key it correctly

     Example:
       (g:register-generic! brain #'merge)")

  (g:all-generics
    [brain]
    "Return a sequence of all registered generic operator records.
     Replaces collect-generic-vars and namespace scanning.

     Example:
       (g:all-generics brain)")

  (clear!
    [brain]
    "Reset all state. Leaves the brain empty.
     Intended for test isolation — not for production use.

     Example:
       (clear! brain)")

  (snapshot
    [brain]
    "Return an immutable map of all current brain state.
     The snapshot is the unit of composition: two snapshots can be merged
     to produce a combined runtime without reloading source files.

     Returns a map with keys :predicates, :handlers, :hierarchy, :plans, :generics.

     Returns a map of the form:
       {:predicates {'number? {...}}
        :handlers   {'merge-number?-interval? {...}}
        :hierarchy  {'integer? '[number?]}
        :plans      {'nil {...}}
        :generics   [...]}

     Example:
       (snapshot brain)"))
