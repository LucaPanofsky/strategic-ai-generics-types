(ns userspace.quine
  (:refer-clojure :exclude [merge])
  (:require
   [strategic-ai.generics-types.infostructure.macros
    :refer [definfostructure]]
   [userspace.library :as library]
   [userspace.protocol :as protocol
    :refer [merge]]))

(definfostructure quine
  (hierarchy
   (implies any?)
   (implies some?))
  (constructor [args])
  (generics
   (merge [(quine? this) (number? other)] (quine (library/transform [:quine/number this other])))
   (merge [(quine? this) (string? other)] (quine (library/transform [:quine/string this other])))))

(comment
  (merge (quine 1) "zen")
  "Returns"
  '(:quine [:transform ([:quine/string (:quine 1) "zen"])]))