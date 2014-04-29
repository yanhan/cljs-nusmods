(ns cljs-nusmods.main
  (:use [jayq.core :only [$ one]]))

; Initialize window.MODULES_SELECTED
(aset js/window "MODULES_SELECTED" (js-obj))

; $(document)
(def $document ($ js/document))

; initialize Zurb Foundation
(.foundation $document)

; Create modules
(one $document "scriptsLoaded.exhibit" (fn []
  (.log js/console "Exhibit 3.0 Loaded!")))

; Retrieve Exhibit 3.0 Library
(defn getScript [scriptUrl cb]
  (.getScript js/jQuery scriptUrl cb))

(getScript "js/vendor/exhibit3-all.min.js" (fn []))
