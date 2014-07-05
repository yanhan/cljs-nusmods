(ns ^{:doc "localStorage wrapper functions"}
  cljs-nusmods.localStorage)

(def ^{:doc     "HTML LocalStorage"
       :private true}
  LOCALSTORAGE (aget js/window "localStorage"))

(defn get-item
  "Gets the value from a key in localStorage; returns nil if the browser does
   not support localStorage"
  [k]
  (if LOCALSTORAGE
      (.getItem LOCALSTORAGE k))
      nil)

(defn set-item
  "Sets a key value pair in the localStorage"
  [k v]
  (and LOCALSTORAGE
       (.setItem LOCALSTORAGE k v)))
