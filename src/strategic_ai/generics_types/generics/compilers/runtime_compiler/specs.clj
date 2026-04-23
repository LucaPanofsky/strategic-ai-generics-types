(ns strategic-ai.generics-types.generics.compilers.runtime-compiler.specs
  "Specs for the runtime compiler configuration map.

   A runtime compiler config drives the full compilation of a generic operator
   protocol and its infostructure extensions into a single self-contained
   Clojure source file.

   The config specifies what to load, what to inline, and how to emit it:

     {:protocol  \"src/userspace/protocol.clj\"
      :sources   [\"src/userspace/quine.clj\"
                  \"src/userspace/numbers.clj\"]
      :namespace 'compiled.runtime
      :output    \"out/compiled/runtime.clj\"
      :requires  [[clojure.string :as str]
                  [userspace.library :as library]]}

   :protocol  — path to the protocol file, the source of truth for generic operators.
                Loaded first, before any :sources.
   :sources   — optional ordered vector of infostructure file paths to load and inline.
                All handlers and predicates registered by these files are compiled.
   :namespace — symbol naming the emitted namespace. Never derived from :output.
   :output    — file path where the compiled source will be written.
   :requires  — optional vector of require entries, each a triple [ns :as alias],
                declaring explicit dependencies in the emitted ns form.

   Conforming ::config produces the map as-is (no transformation).
   Conforming ::require-entry produces {:ns <sym> :alias <sym>}."
  (:require [clojure.spec.alpha :as s]))

(s/def ::protocol string?)

(s/def ::source-path string?)

(s/def ::sources
  (s/coll-of ::source-path :kind vector?))

(s/def ::namespace symbol?)

(s/def ::output string?)

(s/def ::require-entry
  (s/and (s/cat :ns    symbol?
                :as    #{:as}
                :alias symbol?)
         (s/conformer (fn [{:keys [ns alias]}] {:ns ns :alias alias}))))

(s/def ::requires
  (s/coll-of (s/spec ::require-entry) :kind vector?))

(s/def :exclude/clj
  (s/coll-of symbol? :kind vector?))

(s/def ::config
  (s/keys :req-un [::protocol ::namespace ::output]
          :opt-un [::sources ::requires]
          :opt    [:exclude/clj]))

(s/def ::defn-form
  (s/and seq? #(= 'defn (first %)) #(symbol? (second %))))

(s/def ::defn-forms
  (s/coll-of ::defn-form))

(defn conform!
  "Conforms value against spec. Throws ex-info with explain-str on invalid input.

   spec  - a spec keyword
   value - the value to conform

   Returns the conformed value or throws ex-info.

   Example:
     (conform! ::config {:protocol  \"src/protocol.clj\"
                         :namespace 'compiled.runtime
                         :output    \"out/runtime.clj\"})"
  [spec value]
  (let [result (s/conform spec value)]
    (if (= result :clojure.spec.alpha/invalid)
      (throw (ex-info "Invalid runtime compiler config"
                      {:value   value
                       :explain (s/explain-str spec value)}))
      result)))
