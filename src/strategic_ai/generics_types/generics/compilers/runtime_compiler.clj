(ns strategic-ai.generics-types.generics.compilers.runtime-compiler
  "Compiles a generic operator protocol and its infostructure extensions into a
   single self-contained Clojure source file.

   The protocol file is the source of truth for generic operators. Infostructure
   files extend it by registering handlers and predicates. The compiler loads
   both, collects all registered state, and emits one expanded, compiled file.
   Consumers of the emitted file require no dependency on the generics machinery.

   Entry points:
     build-ns-form          - builds the quoted ns form from config
     build-predicate-defn   - builds a quoted defn form for a predicate fn
     build-handler-defn     - builds a quoted defn form for a handler fn
     build-generic-defn     - builds a quoted defn form for a compiled generic
     build-constructor-defn - builds a quoted defn form for an infostructure constructor
     defn-form-from-source  - reads a defn form for a symbol from its source file
     extract-default-defn   - collects helper defn forms from generic default expressions
     defined-name           - extracts the defined symbol from a defn form
     referenced-names       - finds which names from a set appear in a form's body
     build-dependency-graph - builds a dependency map from a collection of defn forms
     topsort-forms          - returns forms in dependency order
     names-requiring-declaration - finds names referenced before their definition
     emit-runtime!          - full pipeline: load sources, compile, emit"
  (:require [cljfmt.core :as fmt]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [strategic-ai.generics-types.generics.compilers.runtime-compiler.specs :as specs]
            [strategic-ai.generics-types.generics.compilers.tree-compiler :as tree-compiler]
            [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]))

(defn- library-version []
  (some-> (io/resource "VERSION") slurp str/trim))

(defn- build-docstring
  "Builds the namespace docstring for an emitted runtime file.

   config - the compiler config map

   Returns a string identifying the compiler, the library version (read from
   the VERSION resource), the generation timestamp, and the full config as a
   formatted EDN block under a 'footprint' heading.

   Example:
     (build-docstring {:namespace 'compiled.runtime :protocol \"src/p.clj\" ...})
     => \"Compiled by @strategic-ai/generic-types 0.0.1-alpha\\nGenerated: 2026-04-21T10:30:00\\n\\n## footprint\\n\\n```edn\\n{...}\\n```\""
  [config]
  (str "Compiled by @strategic-ai/generic-types"
       (when-let [v (library-version)] (str " " v)) "\n"
       "Generated: " (java.time.Instant/now) "\n\n"
       "## footprint\n\n"
       "```edn\n"
       (with-out-str (pp/pprint config))
       "```"))

(defn- format-ns-form [{:keys [namespace requires docstring] :exclude/keys [clj]}]
  (let [escape  (fn [s] (-> s
                            (str/replace "\\" "\\\\")
                            (str/replace "\"" "\\\"")))
        clauses (keep identity
                      [(when docstring
                         (str "  \"" (escape docstring) "\""))
                       (when (seq clj)
                         (str "  (:refer-clojure :exclude [" (str/join " " clj) "])"))
                       (when (seq requires)
                         (let [req-strs (map #(str "[" (str/join " " %) "]") requires)]
                           (str "  (:require " (str/join "\n           " req-strs) ")")))])]
    (if (empty? clauses)
      (str "(ns " namespace ")")
      (str "(ns " namespace "\n"
           (str/join "\n" clauses)
           ")"))))

(defn build-ns-form
  "Builds a quoted ns form from the config map.

   config - map with :namespace (symbol), optional :requires
            (vector of [ns :as alias] triples), and optional :exclude/clj
            (vector of symbols to exclude from clojure.core)

   Returns (ns <namespace>) when no clauses are needed, otherwise builds
   (:refer-clojure :exclude [...]) before (:require ...) when both are present.

   Example:
     (build-ns-form {:namespace   'compiled.runtime
                     :exclude/clj '[merge flatten]
                     :requires    [[clojure.string :as str]]})
     => (ns compiled.runtime
          (:refer-clojure :exclude [merge flatten])
          (:require [clojure.string :as str]))"
  [{:keys [namespace requires docstring] :exclude/keys [clj]}]
  (let [refer-clojure  (when (seq clj)
                         (list :refer-clojure :exclude (vec clj)))
        require-clause (when (seq requires)
                         (apply list :require (map vec requires)))
        clauses        (keep identity [docstring refer-clojure require-clause])]
    (if (empty? clauses)
      (list 'ns namespace)
      (apply list 'ns namespace clauses))))

(defn build-predicate-defn
  "Builds a quoted (defn pred-name [arg] expr) form for a predicate fn.

   pred-fn - a predicate fn carrying :symbolic-name in metadata,
             registered via defpredicate

   Example:
     (build-predicate-defn my-pred?)
     => (defn my-pred? [x] (and (number? x) (pos? x)))"
  [pred-fn]
  (let [sym   (-> pred-fn meta :symbolic-name)
        entry (protocol/p:resolve-predicate! brain/*brain* sym)
        arg   (:arg entry)
        expr  (:expr entry)]
    (list 'defn sym [arg] expr)))

(defn build-handler-defn
  "Builds a quoted (defn handler-name [args] body...) form for a handler fn.

   handler-fn - a handler fn carrying :symbolic-name in metadata,
                registered via defhandler

   Example:
     (build-handler-defn my-handler)
     => (defn my-handler [x] (* x 3))"
  [handler-fn]
  (let [sym   (-> handler-fn meta :symbolic-name)
        entry (protocol/p:resolve-handler! brain/*brain* sym)
        args  (:args entry)
        body  (:body entry)]
    (apply list 'defn sym args body)))

(defn build-constructor-defn
  "Builds a quoted (defn name [args] body) form for an infostructure constructor.

   plan - a compilation plan map from the infostructure registry, containing
          :name and :constructor {:args [...] :body ...}

   Returns nil when the plan has no constructor.

   Example:
     (build-constructor-defn {:name 'contradiction
                              :constructor {:args '[type args]
                                           :body '(list :contradiction type args)}})
     => (defn contradiction [type args] (list :contradiction type args))"
  [{:keys [name constructor]}]
  (when constructor
    (list 'defn name (:args constructor) (:body constructor))))

(defn defined-name
  "Returns the symbol being defined by a defn form.

   form - a quoted defn form (list starting with 'defn)

   Example:
     (defined-name '(defn foo [x] x)) => foo"
  [form]
  (second form))

(defn referenced-names
  "Returns the subset of defined-set that appears as symbols in form's body,
   excluding the form's own defined name (self-references are not dependencies).

   form        - a quoted defn form
   defined-set - set of symbols defined in the same file

   Example:
     (referenced-names '(defn foo [x] (bar x)) #{'bar 'baz}) => #{bar}"
  [form defined-set]
  (let [own-name (defined-name form)
        refs     (volatile! #{})]
    (walk/postwalk
     (fn [node]
       (when (and (symbol? node)
                  (not= node own-name)
                  (contains? defined-set node))
         (vswap! refs conj node))
       node)
     form)
    @refs))

(defn build-dependency-graph
  "Builds a dependency map from a collection of defn forms.

   forms - collection of quoted defn forms

   Returns a map of {symbol → #{symbol}} where each key is a defined name
   and each value is the set of names (from the same collection) it references.

   Example:
     (build-dependency-graph ['(defn a [x] (b x)) '(defn b [x] x)])
     => {a #{b}, b #{}}"
  [forms]
  (let [defined-set (into #{} (map defined-name) forms)]
    (into {}
          (map (fn [form]
                 [(defined-name form) (referenced-names form defined-set)]))
          forms)))

(defn topsort-forms
  "Returns forms in topological order: dependencies emitted before dependents.

   forms - collection of quoted defn forms

   Uses depth-first search. Cycles (mutual recursion) are broken arbitrarily;
   the declare block emitted by the caller covers forward references at load time.

   Example:
     (topsort-forms ['(defn a [x] (b x)) '(defn b [x] x)])
     => [(defn b [x] x) (defn a [x] (b x))]"
  [forms]
  (let [graph     (build-dependency-graph forms)
        form-map  (into {} (map (fn [f] [(defined-name f) f])) forms)
        visited   (volatile! #{})
        result    (volatile! [])]
    (letfn [(visit [name]
              (when-not (contains? @visited name)
                (vswap! visited conj name)
                (doseq [dep (get graph name #{})]
                  (visit dep))
                (vswap! result conj (get form-map name))))]
      (doseq [name (keys graph)]
        (visit name))
      @result)))

(defn names-requiring-declaration
  "Returns the set of names that are referenced before their definition in the
   given sequence of defn forms.

   sorted-forms - defn forms in the order they will be emitted

   Scans forms left to right, tracking which names have been defined so far.
   Any name referenced in a form that has not yet been defined is a forward
   reference and must be covered by a declare. Returns an empty set when all
   dependencies are satisfied by earlier forms (the common case for acyclic protocols).

   Example:
     (names-requiring-declaration ['(defn a [x] (b x)) '(defn b [x] x)])
     => #{b}  ; b is referenced by a before b is defined"
  [sorted-forms]
  (let [defined-set (into #{} (map defined-name) sorted-forms)]
    (loop [remaining sorted-forms
           seen      #{}
           forwards  #{}]
      (if (empty? remaining)
        forwards
        (let [form     (first remaining)
              refs     (referenced-names form defined-set)
              unseen   (set/difference refs seen)]
          (recur (rest remaining)
                 (conj seen (defined-name form))
                 (into forwards unseen)))))))

(defn- simplify-trivial-cond [form]
  (walk/postwalk
   (fn [node]
     (if (and (seq? node)
              (= 'cond (first node))
              (= 3 (count node))
              (= :else (second node)))
       (nth node 2)
       node))
   form))

(defn- inline-default [compiled-form default-args default-expr]
  (let [compiled-args (mapv #(symbol (str "x" %)) (range (count default-args)))
        arg-map       (zipmap default-args compiled-args)
        inlined       (walk/postwalk #(get arg-map % %) default-expr)]
    (walk/postwalk
     (fn [node]
       (if (and (seq? node) (= '__dispatch_default__ (first node)))
         inlined
         node))
     compiled-form)))

(defn build-generic-defn
  "Builds a quoted (defn op-name [x0 ...] (cond ...)) form for a compiled generic.

   the-var - a var holding a compiled generic operator (compile! must have been called)

   Reads :compiled-form from the generic record and replaces fn with defn.
   Inlines the default expression, substituting the user's arg names with x0, x1, ...

   Throws if no :default-expr is stored (defgeneric was called without a default body).

   Example:
     (build-generic-defn #'my-op)
     => (defn my-op [x0] (cond (my-pred? x0) (my-handler x0) :else :default))"
  [the-var]
  (let [op-name      (-> the-var meta :name)
        record       (-> (var-get the-var) meta :generic-record)
        {:keys [compiled-form default-args default-expr]} @record]
    (when (nil? default-expr)
      (throw (ex-info "Cannot emit defn: no default expression stored. Define a default in defgeneric."
                      {:var the-var})))
    (let [resolved (-> compiled-form
                       (inline-default default-args default-expr)
                       simplify-trivial-cond)]
      (apply list 'defn op-name (rest resolved)))))

(defn defn-form-from-source
  "Finds the defn form for fn-sym by reading its source file.

   fn-sym - a symbol naming a public var in any loaded namespace

   Searches all loaded namespaces for a var named fn-sym, then reads its
   source .clj file and returns the first (defn fn-sym ...) top-level form found.
   Returns nil if the var is not found, is not backed by a file on disk, or has
   no defn form in that file."
  [fn-sym]
  (when-let [v (some #(get (ns-publics %) fn-sym) (all-ns))]
    (let [file-path (:file (meta v))
          reader    (or (when-let [res (io/resource file-path)]
                          (when (= "file" (.getProtocol res))
                            (io/reader res)))
                        (let [f (io/file file-path)]
                          (when (.exists f) (io/reader f))))]
      (when reader
        (with-open [rdr (java.io.PushbackReader. reader)]
          (loop []
            (let [form (try (read rdr false ::eof) (catch Exception _ ::eof))]
              (cond
                (= form ::eof)                              nil
                (and (list? form)
                     (= 'defn (first form))
                     (= fn-sym (second form)))              form
                :else                                       (recur)))))))))

(defn extract-default-defn
  "Collects helper defn forms referenced at the top level of each generic's default
   expression.

   generic-vars - seq of generic operator vars

   For each var whose :default-expr is a function call (list with a symbol head),
   attempts to read the defn form of that symbol from its source file.
   Returns a seq of defn forms (nils filtered out)."
  [generic-vars]
  (keep (fn [v]
          (let [record       (-> (var-get v) meta :generic-record)
                default-expr (:default-expr @record)]
            (when (and (list? default-expr) (symbol? (first default-expr)))
              (defn-form-from-source (first default-expr)))))
        generic-vars))

(defn- load-all! [{:keys [protocol sources]}]
  (load-file protocol)
  (doseq [path sources]
    (load-file path)))

(defn- extract-fns [generic-vars]
  (let [all-pairs  (mapcat (fn [v]
                             (tree-compiler/flatten-tree
                              (:tree @(-> (var-get v) meta :generic-record))))
                           generic-vars)
        predicates (distinct (mapcat first all-pairs))
        handlers   (distinct (map second all-pairs))]
    {:predicates predicates :handlers handlers}))

(defn- format-forms [forms]
  (fmt/reformat-string
   (str/join "\n" (map #(with-out-str (pp/pprint %)) forms))))

(defn- emit-vars! [config generic-vars]
  (doseq [v generic-vars] (tree-compiler/compile! v))
  (let [{:keys [predicates handlers]} (extract-fns generic-vars)
        core-ns       (the-ns 'clojure.core)
        enriched-config    (assoc config :docstring (build-docstring config))
        ns-str             (format-ns-form enriched-config)
        pred-defns         (->> predicates
                                (remove #(let [sym (-> % meta :symbolic-name)]
                                           (or (nil? sym) (ns-resolve core-ns sym))))
                                (map build-predicate-defn))
        handler-defns      (map build-handler-defn handlers)
        constructor-defns  (keep build-constructor-defn (protocol/i:all-plans brain/*brain*))
        default-defns      (extract-default-defn generic-vars)
        generic-defns      (map build-generic-defn generic-vars)
        all-defns          (topsort-forms (concat pred-defns handler-defns constructor-defns default-defns generic-defns))
        to-decl            (names-requiring-declaration all-defns)
        declare-str        (when (seq to-decl)
                             (format-forms [(apply list 'declare to-decl)]))
        source             (str/join "\n\n" (keep identity [ns-str declare-str (format-forms all-defns)]))]
    (io/make-parents (:output config))
    (spit (:output config) source)))

(defn emit-runtime!
  "Compiles all generic operators found across the protocol and infostructure
   namespaces and writes them to a single file.

   config - map conforming to specs/config; :protocol is loaded first,
            then each file in :sources in order
   vars   - optional seq of generic operator vars to compile directly;
            when provided, skips loading :protocol and :sources (for testing)

   The emitted file contains: the ns form (using config :namespace and :requires),
   predicate defns, handler defns, and compiled generic defns. No runtime
   dependency on the generics machinery is emitted.

   Side effect: writes a .clj file at config :output. Returns nil.

   Example:
     (emit-runtime! {:protocol  \"src/userspace/protocol.clj\"
                     :sources   [\"src/userspace/quine.clj\"]
                     :namespace 'compiled.runtime
                     :output    \"out/compiled/runtime.clj\"
                     :requires  [[userspace.library :as library]]})"
  ([config]
   (specs/conform! ::specs/config config)
   (load-all! config)
   (emit-vars! config (protocol/g:all-generics brain/*brain*)))
  ([config vars]
   (emit-vars! config vars)))
