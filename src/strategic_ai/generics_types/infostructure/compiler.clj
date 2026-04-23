(ns strategic-ai.generics-types.infostructure.compiler
  "Pure compiler from definfostructure DSL forms to compilation plans.

   The two entry points are:

     compile-infostructure - takes an already-conformed DSL map and returns a plan
     parse-infostructure   - takes a raw definfostructure form and returns a plan

   A compilation plan satisfies ::specs/compilation-plan and contains:
     :name        - the info-structure name symbol
     :predicate   - derived predicate symbol (name + \"?\")
     :platform?   - true when {:platform? true} appears in :attributes
     :attributes  - the raw attributes map or nil
     :implies     - vector of parent predicate symbols (empty if no hierarchy)
     :constructor - {:args [...] :body (list :name ...)} or nil
     :handlers    - vector of {:op :preds :args :body} maps (empty if no generics)"
  (:require [strategic-ai.generics-types.infostructure.specs :as specs]))

(defn- derive-predicate [name]
  (symbol (str (clojure.core/name name) "?")))

(defn- derive-platform? [attributes]
  (boolean (:platform? attributes)))

(defn- derive-constructor [name constructor-clause]
  (when constructor-clause
    (let [args (:args constructor-clause)
          tag  (keyword (clojure.core/name name))
          body (apply list 'list tag args)]
      {:args args :body body})))

(defn compile-infostructure
  "Compiles a conformed infostructure DSL map into a compilation plan.

   conformed - output of (specs/conform! ::specs/infostructure form)

   Returns a map satisfying ::specs/compilation-plan.

   Example:
     (compile-infostructure {:name 'contradiction :attributes nil
                             :hierarchy {:implies '[any? some?]}
                             :constructor {:args '[type args]}
                             :generics nil})"
  [{:keys [name attributes hierarchy constructor generics]}]
  {:name        name
   :predicate   (derive-predicate name)
   :platform?   (derive-platform? attributes)
   :attributes  attributes
   :implies     (or (:implies hierarchy) [])
   :constructor (derive-constructor name constructor)
   :handlers    (or (:handlers generics) [])})

(defn parse-infostructure
  "Parses and compiles a raw definfostructure form into a compilation plan.

   form - a quoted definfostructure list

   Returns a map satisfying ::specs/compilation-plan.
   Throws ex-info if the form is invalid.

   Example:
     (parse-infostructure
       '(definfostructure contradiction
          (hierarchy (implies any?))
          (constructor [type args])
          (generics (bind [(contradiction? this) (fn? other)] this))))"
  [form]
  (-> (specs/conform! ::specs/infostructure form)
      compile-infostructure))
