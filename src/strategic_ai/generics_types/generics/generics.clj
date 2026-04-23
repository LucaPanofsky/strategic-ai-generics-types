(ns strategic-ai.generics-types.generics.generics
  (:require [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(defn find-handler
  "Walk the dispatch tree consuming one argument per level.
   Returns the matching handler or nil. Backtracks on partial matches."
  [tree args]
  (if (empty? args)
    tree
    (some (fn [[pred subtree]]
            (when (pred (first args))
              (find-handler subtree (rest args))))
          tree)))

(defn bind-in-tree
  "Insert or replace the handler for a sequence of predicates in the tree.
   Same predicate object at the same level reuses the existing subtree."
  [predicates handler tree]
  (let [pred       (first predicates)
        more-preds (rest predicates)
        entry-idx  (some (fn [[i [p _]]]
                           (when (= p pred) i))
                         (map-indexed vector tree))]
    (if (seq more-preds)
      (if entry-idx
        (assoc (vec tree) entry-idx
               [pred (bind-in-tree more-preds handler
                                   (second (nth tree entry-idx)))])
        (vec (cons [pred (bind-in-tree more-preds handler [])]
                   tree)))
      (if entry-idx
        (do (println (str "WARNING: replacing handler for predicate " pred))
            (assoc (vec tree) entry-idx [pred handler]))
        (vec (cons [pred handler] tree))))))

(defn- pred-sym [pred-fn]
  (or (:symbolic-name (meta pred-fn))
      (protocol/p:lookup-predicate-by-fn brain/*brain* pred-fn)))

(defn- matches-with-hierarchy? [pred-fn arg]
  (or (pred-fn arg)
      (when-let [sym (pred-sym pred-fn)]
        (some (fn [child-sym]
                (when-let [entry (protocol/p:lookup-predicate brain/*brain* child-sym)]
                  ((:impl entry) arg)))
              (protocol/h:children-of brain/*brain* sym)))))

(defn find-handler-hierarchy
  "Scan flat-handlers in order, matching each predicate directly or via the
   hierarchy. Returns the handler fn of the first full match, or nil."
  [flat-handlers args]
  (some (fn [{:keys [pred-fns handler]}]
          (when (every? true? (map matches-with-hierarchy? pred-fns args))
            handler))
        flat-handlers))

(defn strategy
  "Returns the dispatch strategy of op: :tree (default) or :hierarchy."
  [op]
  (get @(:generic-record (meta op)) :strategy :tree))

(defn- resolve-handler [record args]
  (let [{:keys [tree flat-handlers default strategy]} @record]
    (if (= strategy :hierarchy)
      (or (find-handler tree args)
          (find-handler-hierarchy flat-handlers args)
          default)
      (or (find-handler tree args) default))))

(defn make-dispatch-fn [arity record]
  (case arity
    1 (fn [a]
        ((resolve-handler record [a]) a))
    2 (fn [a b]
        ((resolve-handler record [a b]) a b))
    3 (fn [a b c]
        ((resolve-handler record [a b c]) a b c))
    (fn [& args]
      (apply (resolve-handler record args) args))))

(defn make-generic-operator
  "Create a generic operator.

   Arguments:
     arity      — number of arguments the operator accepts
     default-fn — called with original arguments when no handler matches;
                  defaults to throwing
     op-name    — optional symbol used in error messages

   Returns a Clojure fn that dispatches via its predicate tree.
   The backing atom is stored in the fn's metadata under :generic-record."
  ([arity]
   (make-generic-operator arity nil nil {}))
  ([arity default-fn]
   (make-generic-operator arity default-fn nil {}))
  ([arity default-fn op-name]
   (make-generic-operator arity default-fn op-name {}))
  ([arity default-fn op-name opts]
   (let [record (atom (merge {:arity         arity
                              :tree          []
                              :flat-handlers []
                              :strategy      (get opts :strategy :tree)
                              :default       (or default-fn
                                                 (fn [& args]
                                                   (throw (ex-info
                                                           (str "Generic operator "
                                                                (or op-name "<anonymous>")
                                                                " has no applicable handler")
                                                           {:args args}))))
                              :name          op-name}
                             (select-keys opts [:default-args :default-expr])))
         dispatch (make-dispatch-fn arity record)]
     (with-meta dispatch {:generic-record record}))))

(defn defhandler
  "Register a handler on a generic operator.

   Arguments:
     operator   — fn created by make-generic-operator
     predicates — vector of predicates, one per argument (must match arity)
     handler    — fn to call when all predicates match

   Last-added wins. Returns the operator."
  [operator predicates handler]
  (let [record (:generic-record (meta operator))]
    (when-not record
      (throw (ex-info "Not a generic operator" {:operator operator})))
    (when (:compiled? @record)
      (throw (ex-info "Generic is compiled; call decompile! first" {})))
    (let [{:keys [arity]} @record]
      (when (not= (count predicates) arity)
        (throw (ex-info "Wrong number of predicates"
                        {:expected arity :got (count predicates)}))))
    (swap! record update :tree #(bind-in-tree (vec predicates) handler %))
    (when (= :hierarchy (:strategy @record))
      (swap! record update :flat-handlers
             (fnil conj [])
             {:pred-fns (vec predicates) :handler handler}))
    operator))

(defmacro defgeneric
  "Define a generic operator with optional default behaviour and options.

   Example:
     (defgeneric my-op [x y])
     (defgeneric my-op [x y] (+ x y))
     (defgeneric my-op [x y] {:strategy :hierarchy} (+ x y))"
  [generic-name args-vec & clauses]
  (let [opts         (when (map? (first clauses)) (first clauses))
        default      (if opts (next clauses) (seq clauses))
        arity        (count args-vec)
        default-expr (cond
                       (nil? default)        nil
                       (= 1 (count default)) (first default)
                       :else                 (cons 'do default))]
    `(do
       (def ~generic-name
         (make-generic-operator
          ~arity
          ~(when (seq default)
             `(fn ~args-vec ~@default))
          '~generic-name
          (merge {:default-args '~args-vec
                  :default-expr '~default-expr
                  :defining-ns  *ns*}
                 ~opts)))
       (protocol/g:register-generic! brain/*brain* (var ~generic-name)))))
