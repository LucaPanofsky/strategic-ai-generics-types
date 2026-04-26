(ns strategic-ai.generics-types.generics.compilers.runtime-compiler-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [strategic-ai.generics-types.generics.compilers.runtime-compiler.specs :as specs]
            [strategic-ai.generics-types.generics.compilers.runtime-compiler :as rc]
            [strategic-ai.generics-types.generics.compilers.tree-compiler :as tree-compiler]
            [strategic-ai.generics-types.generics.generics :as g]
            [strategic-ai.generics-types.impl.brain.brain    :as brain]
            [strategic-ai.generics-types.impl.brain.protocol :as protocol]
            [strategic-ai.generics-types.impl.macros :as m]
            [strategic-ai.generics-types.infostructure.macros :refer [definfostructure]]
            [cljfmt.core]))

(defn with-fresh-brain [f]
  (binding [brain/*brain* (brain/make-brain)]
    (f)))

(use-fixtures :each with-fresh-brain)

(defn rc-classpath-helper [x] (* x 10))

(defn rc-emit-helper [x y] (if (= x y) x :mismatch))

(defn rc-source-helper [x] (* x 100))

(defn rc-inner-helper [x] (+ x 1))
(defn rc-outer-helper [x] (rc-inner-helper x))

(comment
  "Config spec — shape validation")

(deftest it-must-be-that-a-minimal-valid-config-conforms
  (let [config {:protocol  "src/protocol.clj"
                :namespace 'compiled.runtime
                :output    "out/runtime.clj"}]
    (is (s/valid? ::specs/config config))))

(deftest it-must-be-that-a-config-with-sources-and-requires-conforms
  (let [config {:protocol  "src/protocol.clj"
                :sources   ["src/quine.clj" "src/numbers.clj"]
                :namespace 'compiled.runtime
                :output    "out/runtime.clj"
                :requires  '[[clojure.string :as str]
                             [my.library :as library]]}]
    (is (s/valid? ::specs/config config))))

(deftest it-must-not-be-that-a-config-without-protocol-is-valid
  (let [config {:sources   ["src/quine.clj"]
                :namespace 'compiled.runtime
                :output    "out/runtime.clj"}]
    (is (not (s/valid? ::specs/config config)))))

(deftest it-must-not-be-that-a-config-without-namespace-is-valid
  (let [config {:protocol "src/protocol.clj"
                :output   "out/runtime.clj"}]
    (is (not (s/valid? ::specs/config config)))))

(deftest it-must-not-be-that-a-config-with-a-string-namespace-is-valid
  (let [config {:protocol  "src/protocol.clj"
                :namespace "compiled.runtime"
                :output    "out/runtime.clj"}]
    (is (not (s/valid? ::specs/config config)))))

(deftest it-must-not-be-that-a-config-without-output-is-valid
  (let [config {:protocol  "src/protocol.clj"
                :namespace 'compiled.runtime}]
    (is (not (s/valid? ::specs/config config)))))

(deftest it-must-not-be-that-a-require-entry-without-as-is-valid
  (let [config {:protocol  "src/protocol.clj"
                :namespace 'compiled.runtime
                :output    "out/runtime.clj"
                :requires  '[[clojure.string str]]}]
    (is (not (s/valid? ::specs/config config)))))

(comment
  "require-entry conforming")

(deftest unit-require-entry-conforms-to-ns-and-alias
  (let [result (s/conform ::specs/require-entry '[clojure.string :as str])]
    (is (= {:ns 'clojure.string :alias 'str} result))))

(comment
  "build-ns-form — pure function, builds the emitted ns declaration")

(deftest unit-build-ns-form-with-no-requires
  (let [form (rc/build-ns-form {:namespace 'compiled.runtime :requires []})]
    (is (= '(ns compiled.runtime) form))))

(deftest unit-build-ns-form-with-exclude-clj
  (let [form (rc/build-ns-form {:namespace    'compiled.runtime
                                :exclude/clj  '[merge flatten]})]
    (is (= 'ns (first form)))
    (is (= 'compiled.runtime (second form)))
    (is (some #(and (seq? %) (= :refer-clojure (first %))
                    (= :exclude (second %))
                    (= '[merge flatten] (nth % 2)))
              (rest form)))))

(deftest unit-build-ns-form-with-exclude-clj-and-requires
  (let [form    (rc/build-ns-form {:namespace    'compiled.runtime
                                   :requires     '[[clojure.string :as str]]
                                   :exclude/clj  '[merge flatten]})
        clauses (drop 2 form)]
    (is (= :refer-clojure (first (first clauses)))
        "refer-clojure clause must come before require")
    (is (= :require (first (second clauses))))))

(deftest unit-build-ns-form-with-requires-and-aliases
  (let [form (rc/build-ns-form {:namespace 'compiled.runtime
                                :requires  '[[clojure.string :as str]
                                             [my.library :as lib]]})]
    (is (= 'ns (first form)))
    (is (= 'compiled.runtime (second form)))
    (is (= :require (first (nth form 2))))
    (is (some #(= '[clojure.string :as str] %) (rest (nth form 2))))
    (is (some #(= '[my.library :as lib] %) (rest (nth form 2))))))

(comment
  "defn-form-from-source — reads a defn form from source file by symbol name")

(deftest unit-defn-form-from-source-finds-fn-in-classpath-namespace
  (let [result (rc/defn-form-from-source 'rc-classpath-helper)]
    (is (some? result))
    (is (= 'defn (first result)))
    (is (= 'rc-classpath-helper (second result)))))

(deftest it-must-not-be-that-defn-form-from-source-finds-a-clojure-core-fn
  (is (nil? (rc/defn-form-from-source 'number?))))

(deftest it-must-not-be-that-defn-form-from-source-finds-an-undefined-symbol
  (is (nil? (rc/defn-form-from-source 'definitely-does-not-exist-anywhere))))

(deftest unit-defn-form-from-source-finds-fn-in-load-file-loaded-namespace
  (let [tmp (java.io.File/createTempFile "rc_loadfile_" ".clj")]
    (try
      (spit tmp "(ns rc.loadfile.test)\n(defn rc-loadfile-fn [x] (* x 99))")
      (load-file (.getPath tmp))
      (let [result (rc/defn-form-from-source 'rc-loadfile-fn)]
        (is (some? result) "should find fn from a load-file-loaded namespace")
        (is (= 'rc-loadfile-fn (second result))))
      (finally (.delete tmp)))))

(comment
  "extract-default-defn — collects helper defn forms from generic default expressions")

(deftest unit-extract-default-defn-finds-helper-for-fn-call-default
  (g/defgeneric rc-def-op [x] (rc-classpath-helper x))
  (let [results (rc/extract-default-defn [#'rc-def-op])]
    (is (= 1 (count results)))
    (is (= 'defn (first (first results))))
    (is (= 'rc-classpath-helper (second (first results))))))

(deftest it-must-not-be-that-extract-default-defn-returns-anything-for-literal-default
  (g/defgeneric rc-literal-op [x] :unknown)
  (is (empty? (rc/extract-default-defn [#'rc-literal-op]))))

(deftest it-must-not-be-that-extract-default-defn-returns-anything-for-no-default
  (g/defgeneric rc-no-default-op [x])
  (is (empty? (rc/extract-default-defn [#'rc-no-default-op]))))

(comment
  "emit-runtime! — end-to-end, writes a compiled namespace to a file.
   Tests bypass :protocol and :sources loading by passing vars directly.")

(deftest i-can-emit-a-runtime-from-a-config
  (m/defpredicate rc-emit-number? [x] (number? x))
  (g/defgeneric rc-emit-op [x] :default)
  (m/defhandler rc-emit-op [rc-emit-number?] [x] (* x 3))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-emit-test
                 :output    "test-outputs/compiled/rc_emit_test.clj"}
        _       (rc/emit-runtime! config [#'rc-emit-op])
        content (slurp "test-outputs/compiled/rc_emit_test.clj")]
    (is (.exists (io/file "test-outputs/compiled/rc_emit_test.clj")))
    (is (str/includes? content "compiled.rc-emit-test"))
    (is (str/includes? content "rc-emit-number?"))
    (is (str/includes? content "rc-emit-op"))))

(deftest i-can-emit-a-runtime-with-explicit-requires
  (m/defpredicate rc-req-string? [x] (string? x))
  (g/defgeneric rc-req-op [x] :default)
  (m/defhandler rc-req-op [rc-req-string?] [x] (clojure.string/upper-case x))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-req-test
                 :output    "test-outputs/compiled/rc_req_test.clj"
                 :requires  '[[clojure.string :as str]]}
        _       (rc/emit-runtime! config [#'rc-req-op])
        content (slurp "test-outputs/compiled/rc_req_test.clj")]
    (is (str/includes? content ":require"))
    (is (str/includes? content "clojure.string"))
    (is (str/includes? content ":as str"))))

(deftest i-can-emit-a-runtime-that-includes-a-helper-defn
  (g/defgeneric rc-helper-op [x y] (rc-emit-helper x y))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-helper-test
                 :output    "test-outputs/compiled/rc_helper_test.clj"}
        _       (rc/emit-runtime! config [#'rc-helper-op])
        content (slurp "test-outputs/compiled/rc_helper_test.clj")]
    (is (str/includes? content "rc-emit-helper")
        "helper defn should be emitted")
    (is (str/includes? content "rc-helper-op")
        "compiled generic should be emitted")))

(deftest it-must-be-that-plain-helpers-called-from-handler-body-are-emitted
  (m/defpredicate rc-sh-number? [x] (number? x))
  (g/defgeneric rc-sh-op [x] :default)
  (m/defhandler rc-sh-op [rc-sh-number?] [x] (rc-source-helper x))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-source-helper-test
                 :output    "test-outputs/compiled/rc_source_helper_test.clj"}
        _       (rc/emit-runtime! config [#'rc-sh-op])
        content (slurp "test-outputs/compiled/rc_source_helper_test.clj")]
    (is (str/includes? content "(defn rc-source-helper")
        "helper called from handler body must be emitted")))

(deftest it-must-be-that-chained-helpers-are-emitted-transitively
  (m/defpredicate rc-ch-number? [x] (number? x))
  (g/defgeneric rc-ch-op [x] :default)
  (m/defhandler rc-ch-op [rc-ch-number?] [x] (rc-outer-helper x))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-chain-helper-test
                 :output    "test-outputs/compiled/rc_chain_helper_test.clj"}
        _       (rc/emit-runtime! config [#'rc-ch-op])
        content (slurp "test-outputs/compiled/rc_chain_helper_test.clj")]
    (is (str/includes? content "(defn rc-outer-helper")
        "outer helper called from handler body must be emitted")
    (is (str/includes? content "(defn rc-inner-helper")
        "inner helper transitively called must also be emitted")))

(comment
  "build-constructor-defn — pure function, builds constructor defn form from a plan")

(deftest unit-build-constructor-defn-builds-a-defn-form
  (let [plan   {:name        'mytype
                :constructor {:args '[a b]
                              :body '(list :mytype a b)}}
        result (rc/build-constructor-defn plan)]
    (is (= 'defn (first result)))
    (is (= 'mytype (second result)))
    (is (= '[a b] (nth result 2)))
    (is (= '(list :mytype a b) (nth result 3)))))

(deftest it-must-not-be-that-build-constructor-defn-returns-form-for-plan-without-constructor
  (let [plan {:name 'mytype :constructor nil}]
    (is (nil? (rc/build-constructor-defn plan)))))

(comment
  "emit-runtime! — constructor defns emitted from infostructure registry")

(definfostructure rc-mytype
  (constructor [value]))

(deftest i-can-emit-a-runtime-that-includes-a-constructor-defn
  (protocol/i:register-plan! brain/*brain* {:name 'rc-mytype :constructor {:args '[value] :body '(list :rc-mytype value)}})
  (g/defgeneric rc-ctor-op [x] :default)
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-ctor-test
                 :output    "test-outputs/compiled/rc_ctor_test.clj"}
        _       (rc/emit-runtime! config [#'rc-ctor-op])
        content (slurp "test-outputs/compiled/rc_ctor_test.clj")]
    (is (str/includes? content "rc-mytype")
        "constructor defn should be emitted")))

(comment
  "build-generic-defn — trivial cond simplification")

(deftest it-must-not-be-that-emitted-defn-contains-trivial-cond
  (g/defgeneric rc-trivial-op [x] x)
  (tree-compiler/compile! #'rc-trivial-op)
  (let [form (rc/build-generic-defn #'rc-trivial-op)
        body (nth form 3)]
    (is (not (and (seq? body) (= 'cond (first body))))
        "body of a handler-less generic should not be a cond form")))

(comment
  "defined-name — extracts the symbol from a defn form")

(deftest unit-defined-name-extracts-symbol-from-defn-form
  (is (= 'foo (rc/defined-name '(defn foo [x] x)))))

(comment
  "referenced-names — finds which names from a set appear in a form's body")

(deftest unit-referenced-names-finds-symbol-used-in-body
  (is (= #{'bar} (rc/referenced-names '(defn foo [x] (bar x)) #{'bar 'baz}))))

(deftest unit-referenced-names-excludes-own-name
  (is (= #{} (rc/referenced-names '(defn foo [x] (foo x)) #{'foo}))))

(deftest unit-referenced-names-returns-empty-when-no-overlap
  (is (= #{} (rc/referenced-names '(defn foo [x] (+ x 1)) #{'bar}))))

(comment
  "build-dependency-graph — maps each defined name to its set of dependencies")

(deftest unit-build-dependency-graph-maps-names-to-deps
  (let [forms ['(defn a [x] (b x)) '(defn b [x] x)]
        graph (rc/build-dependency-graph forms)]
    (is (= #{'b} (get graph 'a)))
    (is (= #{} (get graph 'b)))))

(comment
  "topsort-forms — returns forms in dependency order")

(deftest it-must-be-that-topsort-places-dependency-before-dependent
  (let [forms  ['(defn a [x] (b x)) '(defn b [x] x)]
        sorted (rc/topsort-forms forms)
        names  (mapv rc/defined-name sorted)]
    (is (< (.indexOf names 'b) (.indexOf names 'a)))))

(deftest it-must-be-that-topsort-handles-a-linear-chain
  (let [forms  ['(defn a [x] (b x)) '(defn b [x] (c x)) '(defn c [x] x)]
        sorted (rc/topsort-forms forms)
        names  (mapv rc/defined-name sorted)]
    (is (< (.indexOf names 'c) (.indexOf names 'b)))
    (is (< (.indexOf names 'b) (.indexOf names 'a)))))

(deftest it-must-be-that-topsort-returns-all-forms
  (let [forms  ['(defn a [x] (b x)) '(defn b [x] x) '(defn c [x] x)]
        sorted (rc/topsort-forms forms)]
    (is (= (count forms) (count sorted)))))

(deftest it-must-be-that-topsort-handles-a-single-form
  (let [sorted (rc/topsort-forms ['(defn a [x] x)])]
    (is (= 1 (count sorted)))
    (is (= 'a (rc/defined-name (first sorted))))))

(comment
  "Integration — K1-like dependency structure.
   K1 is a protocol from the consumer project that exercises cross-category dependencies:
   a helper defn (base-merge) calling both a generic (equality?) and a constructor (contradiction),
   where equality? itself calls another generic (content). This test verifies topsort resolves
   that multi-level, cross-category dependency chain correctly.")

(deftest i-can-topsort-forms-resembling-k1-dependency-structure
  (let [forms  ['(defn equality? [x y] (= (content x) (content y)))
                '(defn base-merge [x y] (if (equality? x y) x (contradiction :err #{x y})))
                '(defn contradiction [type args] (list :contradiction type args))
                '(defn content [x] x)]
        sorted (rc/topsort-forms forms)
        names  (mapv rc/defined-name sorted)]
    (is (< (.indexOf names 'content) (.indexOf names 'equality?)))
    (is (< (.indexOf names 'equality?) (.indexOf names 'base-merge)))
    (is (< (.indexOf names 'contradiction) (.indexOf names 'base-merge)))))

(comment
  "names-requiring-declaration — finds names referenced before definition in sorted output")

(deftest unit-names-requiring-declaration-returns-empty-for-correctly-sorted-forms
  (let [forms ['(defn b [x] x) '(defn a [x] (b x))]]
    (is (empty? (rc/names-requiring-declaration forms)))))

(deftest unit-names-requiring-declaration-finds-forward-reference
  (let [forms ['(defn a [x] (b x)) '(defn b [x] x)]]
    (is (= #{'b} (rc/names-requiring-declaration forms)))))

(deftest unit-names-requiring-declaration-finds-forward-reference-in-mutual-recursion
  (let [forms ['(defn a [x] (b x)) '(defn b [x] (a x))]]
    (is (= #{'b} (rc/names-requiring-declaration forms)))))

(comment
  "Integration — no declare block emitted for acyclic protocol")

(deftest it-must-not-be-that-emitted-file-contains-declare-when-no-cycles
  (m/defpredicate rc-nodecl-number? [x] (number? x))
  (g/defgeneric rc-nodecl-op [x] :default)
  (m/defhandler rc-nodecl-op [rc-nodecl-number?] [x] (* x 2))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-nodecl-test
                 :output    "test-outputs/compiled/rc_nodecl_test.clj"}
        _       (rc/emit-runtime! config [#'rc-nodecl-op])
        content (slurp "test-outputs/compiled/rc_nodecl_test.clj")]
    (is (not (str/includes? content "declare")))))

(comment
  "Integration — declare block emitted only for mutually recursive names")

(comment
  "emit-runtime! — emitted file contains config footprint in ns docstring")

(deftest it-must-be-that-emitted-file-contains-config-footprint-in-docstring
  (m/defpredicate rc-footprint-number? [x] (number? x))
  (g/defgeneric rc-footprint-op [x] :default)
  (m/defhandler rc-footprint-op [rc-footprint-number?] [x] (* x 2))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-footprint-test
                 :output    "test-outputs/compiled/rc_footprint_test.clj"}
        _       (rc/emit-runtime! config [#'rc-footprint-op])
        content (slurp "test-outputs/compiled/rc_footprint_test.clj")]
    (is (str/includes? content "Compiled by @strategic-ai/generic-types"))
    (is (str/includes? content "## footprint"))
    (is (str/includes? content "```edn"))))

(comment
  "it-must-be-that-emitted-file-is-formatted — output is idempotent under cljfmt")

(deftest it-must-be-that-emitted-file-is-formatted
  (m/defpredicate rc-fmt-number? [x] (number? x))
  (g/defgeneric rc-fmt-op [x] :default)
  (m/defhandler rc-fmt-op [rc-fmt-number?] [x] (* x 2))
  (let [config  {:protocol  "src/protocol.clj"
                 :namespace 'compiled.rc-fmt-test
                 :output    "test-outputs/compiled/rc_fmt_test.clj"}
        _       (rc/emit-runtime! config [#'rc-fmt-op])
        content (slurp "test-outputs/compiled/rc_fmt_test.clj")]
    (is (= content (cljfmt.core/reformat-string content))
        "emitted file must be idempotent under cljfmt")))

(deftest it-must-be-that-emitted-file-declares-only-mutually-recursive-names
  (let [forms   ['(defn rc-ping [x] (rc-pong x)) '(defn rc-pong [x] (rc-ping x))]
        sorted  (rc/topsort-forms forms)
        to-decl (rc/names-requiring-declaration sorted)]
    (is (= 1 (count to-decl))
        "exactly one name needs declaring in a two-way cycle")
    (is (set/subset? to-decl #{'rc-ping 'rc-pong})
        "the declared name must be one of the two mutually recursive fns")))
