(ns strategic-ai.generics-types.infostructure.specs
  "Specs for the definfostructure DSL.

   A definfostructure form declares a named information structure with up to
   four optional clauses. An optional attribute map may follow the name:

     (definfostructure <name>)
     (definfostructure <name> {<attr-key> <attr-val> ...})
     (definfostructure <name>
       (hierarchy  (implies <pred>) ...)
       (constructor [<arg> ...])
       (generics   (<op> [(<pred> <binding>) ...] <body>) ...))

   Clauses must appear in this order when present: hierarchy, constructor, generics.
   Each clause is optional; any subset is valid.

   Conforming ::infostructure produces:
     {:name        <symbol>
      :attributes  <map or nil>
      :hierarchy   {:implies [<pred-sym> ...]} or nil
      :constructor {:args [<sym> ...]} or nil
      :generics    {:handlers [{:op <sym> :preds [<sym> ...] :args [<sym> ...] :body <form>} ...]} or nil}

   Conforming ::handler produces:
     {:op <sym> :preds [<pred-sym> ...] :args [<binding-sym> ...] :body <form>}

   Example:
     (conform! ::infostructure
       '(definfostructure contradiction
          {:platform? false}
          (hierarchy (implies any?) (implies some?))
          (constructor [type args])
          (generics
            (bind [(contradiction? this) (fn? other)] this))))"
  (:require [clojure.spec.alpha :as s]))

(defn- tag-of [kind]
  (s/and symbol? #(= kind (name %))))

(s/def ::definfostructure-tag (tag-of "definfostructure"))
(s/def ::implies-tag          (tag-of "implies"))
(s/def ::hierarchy-tag        (tag-of "hierarchy"))
(s/def ::constructor-tag      (tag-of "constructor"))
(s/def ::generics-tag         (tag-of "generics"))

(s/def ::implies-clause
  (s/and (s/cat :tag  ::implies-tag
                :pred symbol?)
         (s/conformer (fn [{:keys [pred]}] {:pred pred}))))

(s/def ::hierarchy-clause
  (s/and (s/cat :tag     ::hierarchy-tag
                :implies (s/+ (s/spec ::implies-clause)))
         (s/conformer (fn [{:keys [implies]}]
                        {:implies (mapv :pred implies)}))))

(s/def ::constructor-clause
  (s/and (s/cat :tag  ::constructor-tag
                :args (s/and vector? (s/coll-of symbol? :min-count 1)))
         (s/conformer (fn [{:keys [args]}] {:args (vec args)}))))

(s/def ::pred-binding
  (s/and (s/cat :pred    symbol?
                :binding symbol?)
         (s/conformer (fn [{:keys [pred binding]}]
                        {:pred pred :binding binding}))))

(s/def ::pred-bindings
  (s/coll-of (s/spec ::pred-binding) :kind vector? :min-count 1))

(s/def ::handler
  (s/and (s/cat :op    symbol?
                :pairs ::pred-bindings
                :body  any?)
         (s/conformer (fn [{:keys [op pairs body]}]
                        {:op    op
                         :preds (mapv :pred pairs)
                         :args  (mapv :binding pairs)
                         :body  body}))))

(s/def ::generics-clause
  (s/and (s/cat :tag      ::generics-tag
                :handlers (s/+ (s/spec ::handler)))
         (s/conformer (fn [{:keys [handlers]}] {:handlers handlers}))))

(s/def ::infostructure
  (s/and (s/cat :tag         ::definfostructure-tag
                :name        #(or (symbol? %) (keyword? %))
                :attributes  (s/? map?)
                :hierarchy   (s/? (s/spec ::hierarchy-clause))
                :constructor (s/? (s/spec ::constructor-clause))
                :generics    (s/? (s/spec ::generics-clause)))
         (s/conformer (fn [{:keys [name attributes hierarchy constructor generics]}]
                        {:name        name
                         :attributes  attributes
                         :hierarchy   hierarchy
                         :constructor constructor
                         :generics    generics}))))

(s/def ::name       #(or (symbol? %) (keyword? %)))
(s/def ::platform?  boolean?)
(s/def ::predicate  symbol?)
(s/def ::implies    (s/coll-of symbol? :kind vector?))
(s/def ::attributes (s/nilable map?))
(s/def ::op         symbol?)
(s/def ::preds      (s/coll-of symbol? :kind vector? :min-count 1))
(s/def ::args       (s/coll-of symbol? :kind vector? :min-count 1))
(s/def ::body       any?)

(s/def ::plan-handler
  (s/keys :req-un [::op ::preds ::args ::body]))

(s/def ::handlers
  (s/coll-of ::plan-handler :kind vector?))

(s/def ::constructor
  (s/nilable (s/keys :req-un [::args ::body])))

(s/def ::compilation-plan
  (s/keys :req-un [::name ::platform? ::predicate ::implies ::attributes ::handlers ::constructor]))

(defn conform!
  "Conforms value against spec. Throws ex-info with explain-str on invalid input.

   spec  - a spec keyword
   value - the form to conform

   Returns the conformed value or throws ex-info.

   Example:
     (conform! ::infostructure '(definfostructure boolean))"
  [spec value]
  (let [result (s/conform spec value)]
    (if (= result ::s/invalid)
      (throw (ex-info "Invalid definfostructure form"
                      {:value   value
                       :explain (s/explain-str spec value)}))
      result)))
