(ns
  ^{:doc "Helper functions for array representation of auxiliary Module object"}
  cljs-nusmods.aux-module-array-repr)

(def
  ^{:doc "Module Type information represented as bitmasks. Retrieved from
          JavaScript globals."}
  MODULE_TYPE (aget js/window "MODULE_TYPE"))

(def
  ^{:doc "Faculty module (bitmask representation)"}
  MODULE_TYPE_FACULTY (aget MODULE_TYPE "Faculty"))

(def
  ^{:doc "Breadth / UE module (bitmask representation)"}
  MODULE_TYPE_UE (aget MODULE_TYPE "UE"))

(def
  ^{:doc "Singapore Studies module (bitmask representation)"}
  MODULE_TYPE_SS (aget MODULE_TYPE "SS"))

(def
  ^{:doc "GEM module (bitmask representation)"}
  MODULE_TYPE_GEM (aget MODULE_TYPE "GEM"))

(def
  ^{:doc "Vector of all Module types bitmasks"}
  MODULE_TYPES_VEC
  [MODULE_TYPE_FACULTY MODULE_TYPE_UE MODULE_TYPE_SS MODULE_TYPE_GEM])

(def
  ^{:doc "Map of Module type bitmask -> String"}
  MODULE_TYPES_MAP
  {MODULE_TYPE_FACULTY "Faculty"
   MODULE_TYPE_UE      "Breadth / UE"
   MODULE_TYPE_SS      "Singapore Studies"
   MODULE_TYPE_GEM     "GEM"})

(def
  ^{:doc "A module not of the above types"}
  NOT_IN_CORS
  "Not in CORS")

(defn get-module-types
  "Retrieves the types of the module (Faculty, Breadth/UE) and returns it as a
   JavaScript array"
  [auxModuleArrayRepr]
  (let [moduleType    (nth auxModuleArrayRepr 2)
        moduleTypeVec (filter (fn [mt] (= (bit-and moduleType mt) mt))
                              MODULE_TYPES_VEC)]
    (if (empty? moduleTypeVec)
      (array NOT_IN_CORS)
      (clj->js (map (fn [moduleType] (get MODULE_TYPES_MAP moduleType))
                    moduleTypeVec)))))
