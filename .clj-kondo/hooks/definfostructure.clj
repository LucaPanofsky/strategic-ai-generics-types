(ns hooks.definfostructure
  (:require [clj-kondo.hooks-api :as api]))

(defn- map-node? [node]
  (= :map (api/tag node)))

(defn- list-head [node]
  (when (= :list (api/tag node))
    (some-> node :children first :value)))

(defn- find-clause [tag children]
  (some #(when (= tag (list-head %)) %) children))

(defn- handler->let [handler-node]
  (let [[op-node pairs-node body-node] (:children handler-node)
        pair-nodes    (:children pairs-node)
        pred-nodes    (map #(first  (:children %)) pair-nodes)
        binding-nodes (map #(second (:children %)) pair-nodes)
        bindings      (vec (concat [(api/token-node '_op) op-node]
                                   (mapcat #(vector % (api/token-node nil)) binding-nodes)))]
    (api/list-node
     [(api/token-node 'let)
      (api/vector-node bindings)
      (api/list-node (list* (api/token-node 'do) (concat pred-nodes [body-node])))])))

(defn definfostructure [{:keys [node]}]
  (let [[_ name-node & rest-nodes] (:children node)
        name-sym    (:value name-node)
        pred-sym    (symbol (str name-sym "?"))
        clauses     (drop-while map-node? rest-nodes)
        constructor (find-clause 'constructor clauses)
        generics    (find-clause 'generics clauses)
        ctor-args   (when constructor (second (:children constructor)))
        handlers    (when generics (rest (:children generics)))
        forms       (concat
                     [(api/list-node [(api/token-node 'def) (api/token-node pred-sym) (api/token-node nil)])]
                     (when ctor-args
                       (let [tag  (api/token-node (keyword name-sym))
                             body (api/list-node (list* (api/token-node 'list) tag (:children ctor-args)))]
                         [(api/list-node [(api/token-node 'defn) name-node ctor-args body])]))
                     (map handler->let handlers))]
    {:node (api/list-node (list* (api/token-node 'do) forms))}))
