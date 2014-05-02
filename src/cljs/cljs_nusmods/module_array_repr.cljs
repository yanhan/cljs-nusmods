(ns ^{:doc "Helper functions for array representation of Module object"}
  cljs-nusmods.module-array-repr)

(def MODULE_CODE_REGEX #"^\D+(\d{4})\D*$")

(defn get-module-code
  "Retrieves the module code from the array representation of a module"
  [moduleArrayRepr]
  (nth moduleArrayRepr 0))

(defn get-module-level 
  "Obtains the level of a module from its array representation"
  [moduleArrayRepr]
  (let [moduleCode (get-module-code moduleArrayRepr)]
    (if (= "PH2302 / GEK2039" moduleCode)
      2000
      (let [matchArray (.match moduleCode MODULE_CODE_REGEX)
            ; TODO: Handle case where module code does not match regex
            moduleDigitsInt (js/parseInt (nth matchArray 1))]
        (- moduleDigitsInt (mod moduleDigitsInt 1000))))))
