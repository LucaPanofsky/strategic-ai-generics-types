(ns strategic-ai.generics-types.generics.compilers.tree-compiler
  "Experimental: compiles a generic dispatch tree directly to a nested cond form,
   without the intermediate network representation.

   The dispatch tree produced by bind-in-tree already encodes shared-prefix
   structure — each level corresponds to one argument position. Walking it
   directly yields the same nested cond that the network compiler produces,
   with less machinery.

   Entry points:
     build-fn-form  - returns the quoted (fn [x0 ...] (cond ...)) form
     eval-dispatch  - builds and evals the form into a concrete fn
     compile!       - installs the compiled fn on a generic var"
  (:require [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(def ^:private default-sym '__dispatch_default__)

(defn- flatten-tree* [tree path]
  (mapcat (fn [[pred subtree]]
            (let [extended (conj path pred)]
              (if (fn? subtree)
                [[extended subtree]]
                (flatten-tree* subtree extended))))
          tree))

(defn flatten-tree
  "Converts a generic dispatch tree into a flat sequence of [pred-vec handler] pairs.

   tree - a dispatch tree as stored in the generic operator record

   Example:
     (flatten-tree [[number? [[string? my-handler]]]])
     => [[[number? string?] my-handler]]"
  [tree]
  (flatten-tree* tree []))

(defn- sym-of [f]
  (or (-> f meta :symbolic-name)
      (protocol/p:lookup-predicate-by-fn brain/*brain* f)))

(defn- build-pred-hierarchy [pred-syms]
  (reduce (fn [h pred-sym]
            (reduce (fn [h' implied-sym]
                      (try (derive h' pred-sym implied-sym)
                           (catch Throwable _ h')))
                    h
                    (protocol/h:implied-by brain/*brain* pred-sym)))
          (make-hierarchy)
          pred-syms))

(defn- sort-by-specificity [pairs]
  (let [syms (mapv (comp sym-of first) pairs)
        h    (build-pred-hierarchy syms)
        anc  (into {} (map (fn [s] [s (count (ancestors h s))]) syms))
        cmp  (fn [a b]
               (cond
                 (isa? h a b) -1
                 (isa? h b a)  1
                 :else         (compare (get anc b 0) (get anc a 0))))]
    (sort-by (comp sym-of first) cmp pairs)))

(defn- expand-tree [tree]
  (let [syms (mapv (comp sym-of first) tree)
        h    (build-pred-hierarchy syms)]
    (mapv (fn [[pred subtree]]
            (if (fn? subtree)
              [pred subtree]
              (let [pred-sym     (sym-of pred)
                    parent-entries
                    (mapcat (fn [[parent-pred parent-sub]]
                              (let [parent-sym (sym-of parent-pred)]
                                (when (and (not= parent-sym pred-sym)
                                           (isa? h pred-sym parent-sym)
                                           (not (fn? parent-sub)))
                                  parent-sub)))
                            tree)
                    merged (reduce (fn [acc entry]
                                     (if (some #(= (first %) (first entry)) acc)
                                       acc
                                       (conj acc entry)))
                                   subtree
                                   parent-entries)]
                [pred (expand-tree merged)])))
          tree)))

(defn- tree->cond-form [tree current-args all-args dsym]
  (let [[arg & rest-args] current-args
        expanded (expand-tree tree)
        sorted   (sort-by-specificity expanded)]
    (list* 'cond
           (concat
            (mapcat (fn [[pred subtree]]
                      [(list (sym-of pred) arg)
                       (if (fn? subtree)
                         (list* (sym-of subtree) all-args)
                         (tree->cond-form subtree rest-args all-args dsym))])
                    sorted)
            [:else (list* dsym all-args)]))))

(defn build-fn-form
  "Returns a quoted (fn [x0 ...] (cond ...)) form compiled from the dispatch tree.

   tree  - dispatch tree as stored in the generic operator record
   arity - number of arguments

   Example:
     (build-fn-form [[number? [[number? h1] [string? h2]]]] 2)"
  ([tree arity]
   (build-fn-form tree arity default-sym))
  ([tree arity dsym]
   (let [args (mapv #(symbol (str "x" %)) (range arity))]
     (list 'fn args (tree->cond-form tree args args dsym)))))

(def ^:dynamic *eval-env* {})

(defn- build-eval-env [tree default-fn]
  (letfn [(collect [subtree acc]
            (if (fn? subtree)
              (assoc acc (sym-of subtree) subtree)
              (reduce (fn [a [pred sub]]
                        (collect sub (assoc a (sym-of pred) pred)))
                      acc
                      subtree)))]
    (-> (collect tree {})
        (assoc default-sym default-fn))))

(defn eval-dispatch
  "Compiles and evals a dispatch tree into a concrete fn.

   tree       - dispatch tree as stored in the generic operator record
   arity      - number of arguments
   default-fn - fn called when no handler matches

   Example:
     (eval-dispatch [[number? double-it]] 1 (fn [x] :default))"
  [tree arity default-fn]
  (let [fn-form  (build-fn-form tree arity)
        env      (build-eval-env tree default-fn)
        bindings (vec (mapcat (fn [sym]
                                [sym `(get strategic-ai.generics-types.generics.compilers.tree-compiler/*eval-env* '~sym)])
                              (keys env)))]
    (binding [*eval-env* env]
      (eval `(let ~bindings ~fn-form)))))

(defn compile!
  "Compiles a generic operator var in place via alter-var-root.

   the-var - a var created by defgeneric

   Example:
     (compile! #'my-op)"
  [the-var]
  (let [old-fn   (var-get the-var)
        record   (-> old-fn meta :generic-record)
        {:keys [tree arity default]} @record
        fn-form  (build-fn-form tree arity)
        compiled (eval-dispatch tree arity default)]
    (swap! record assoc
           :compiled-form fn-form
           :tree-hash     (hash tree)
           :compiled?     true)
    (alter-var-root the-var (fn [old] (with-meta compiled (meta old))))))

(comment
  (require '[strategic-ai.generics-types.generics.generics :as g]
           '[strategic-ai.generics-types.impl.macros :as m])

  (g/defgeneric combine [x y] :unknown)
  (m/defhandler combine [number? number?] [x y] (+ x y))
  (m/defhandler combine [number? string?] [x y] (str x ": " y))
  (m/defhandler combine [string? string?] [x y] (str x " " y))

  (build-fn-form (:tree @(-> combine meta :generic-record)) 2)

  (compile! #'combine)
  (combine 1 2)
  (combine 1 "answer")
  (combine "hello" "world")
  (combine :x :y))

(comment
  (require '[userspace.protocol :refer [merge]])
  (build-fn-form (:tree @(-> merge meta :generic-record)) 2))

(comment
  (m/defpredicate simple? [x] (or (number? x) (string? x) (boolean? x)))
  (m/defrel simple?
    (implied-by number? string? boolean?)
    (implies any?))

  (g/defgeneric describe [x] {:strategy :hierarchy} :unknown)
  (m/defhandler describe [simple?] [x] [:simple x])

  (build-fn-form (:tree @(-> describe meta :generic-record)) 1)

  '(clojure.core/fn [x0] (clojure.core/cond (simple? x0) (describe-simple? x0) :else (__dispatch_default__ x0)))

  (comment
    "Expected: number?, string?, boolean? sorted before simple? (more specific),
     simple? before any? — all thanks to defrel declarations"))
