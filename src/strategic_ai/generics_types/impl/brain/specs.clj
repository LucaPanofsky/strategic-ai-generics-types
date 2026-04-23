(ns strategic-ai.generics-types.impl.brain.specs
  "Specs for the brain — the unified registry backend.

   The brain holds all system state in one atom with five keys:

     {:predicates {sym → predicate-entry}
      :handlers   {sym → handler-entry}
      :hierarchy  {sym → [parent-sym ...]}
      :plans      {sym → compilation-plan}
      :generics   {sym → var}}

   Entry shapes:

     predicate-entry  — {:key sym :impl fn :expr form :arg sym :defining-ns ns}
     handler-entry    — {:key sym :impl fn :args [sym] :body [form] :defining-ns ns}
     hierarchy        — map from child predicate symbol to vector of implied parent symbols
     compilation-plan — delegated to infostructure.specs/::compilation-plan
     generic-var      — a Clojure var created by defgeneric

   The generic record map shape (content of a generic-var's :generic-record metadata)
   is specced separately as ::generic-record-content for validation and documentation purposes."
  (:require [clojure.spec.alpha :as s]
            [strategic-ai.generics-types.infostructure.specs :as infostructure-specs]))

(comment
  "Shared primitives — used across multiple entry shapes")

(s/def ::key
  symbol?)

(s/def ::impl
  fn?)

(s/def ::defining-ns
  #(instance? clojure.lang.Namespace %))

(comment
  "Predicate entry — registered by defpredicate and auto-registration in defhandler")

(s/def ::expr
  any?)

(s/def ::arg
  symbol?)

(s/def ::predicate-entry
  (s/keys :req-un [::key ::impl]
          :opt-un [::expr ::arg ::defining-ns]))

(comment
  "Handler entry — registered by defhandler")

(s/def ::args
  (s/coll-of symbol? :kind vector? :min-count 1))

(s/def ::body
  (s/coll-of any?))

(s/def ::handler-entry
  (s/keys :req-un [::key ::impl ::args ::body ::defining-ns]))

(comment
  "Hierarchy — map from child predicate symbol to vector of implied parent symbols")

(s/def ::implied-predicates
  (s/coll-of symbol? :kind vector?))

(s/def ::hierarchy
  (s/map-of symbol? ::implied-predicates))

(comment
  "Generic record content — the map held inside a generic-atom")

(s/def ::arity
  pos-int?)

(s/def ::strategy
  #{:tree :hierarchy})

(s/def ::name
  symbol?)

(s/def ::default-args
  (s/coll-of symbol? :kind vector?))

(s/def ::default-expr
  any?)

(s/def ::generic-record-content
  (s/keys :req-un [::arity ::strategy ::name ::default-args ::defining-ns]
          :opt-un [::default-expr]))

(s/def ::generic-var
  var?)

(comment
  "Brain state — the top-level map held in the single brain atom")

(s/def ::predicates
  (s/map-of symbol? ::predicate-entry))

(s/def ::handlers
  (s/map-of symbol? ::handler-entry))

(s/def ::plans
  (s/map-of symbol? ::infostructure-specs/compilation-plan))

(s/def ::generics
  (s/map-of symbol? ::generic-var))

(s/def ::state
  (s/keys :req-un [::predicates ::handlers ::hierarchy ::plans ::generics]))

(defn conform!
  "Conforms value against spec. Throws ex-info with explain-str on invalid input.

   spec  - a spec keyword
   value - the value to conform

   Returns the conformed value or throws ex-info.

   Example:
     (conform! ::predicate-entry {:key 'number? :impl number? :expr '(number? x)
                                  :arg 'x :defining-ns *ns*})"
  [spec value]
  (let [result (s/conform spec value)]
    (if (= result ::s/invalid)
      (throw (ex-info "Invalid brain value"
                      {:spec    spec
                       :value   value
                       :explain (s/explain-str spec value)}))
      result)))
