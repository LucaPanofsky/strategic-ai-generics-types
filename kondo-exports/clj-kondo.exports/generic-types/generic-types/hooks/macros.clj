(ns hooks.macros
  (:require [clj-kondo.hooks-api :as api]))

(defn defhandler [{:keys [node]}]
  (let [[_ operator _ args & body] (:children node)]
    {:node (api/list-node
            (list (api/token-node 'let)
                  (api/vector-node [(api/token-node '_op) operator])
                  (api/list-node (list* (api/token-node 'fn)
                                        args
                                        body))))}))

(defn defrel [{:keys [node]}]
  (let [[_ _pred-sym & clauses] (:children node)
        all-preds (mapcat (fn [clause] (rest (:children clause))) clauses)]
    {:node (api/list-node
            (list* (api/token-node 'do) all-preds))}))
