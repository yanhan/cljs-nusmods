(ns ^{:doc "JavaScript DOM globals"}
  cljs-nusmods.dom-globals
  (:use [jayq.core :only [$]]))

(def ^{:doc "jQuery window selector"}
  $window ($ js/window))

(def ^{:doc "jQuery document selector"}
  $document ($ js/document))
