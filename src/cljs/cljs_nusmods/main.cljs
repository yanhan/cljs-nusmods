(ns ^{:doc "main entry point for the cljs-nusmods project"}
  cljs-nusmods.main
  (:use [jayq.core :only [$ one]])
  (:require [cljs-nusmods.module-array-repr     :as module-array-repr]
            [cljs-nusmods.aux-module-array-repr :as aux-module-array-repr]))

(defn ^{:doc "Wrapper for $.getScript"
        :private true}
  getScript [scriptUrl cb]
  (.getScript js/jQuery scriptUrl cb))

(defn ^{:doc "Builds a JavaScript Array of Modules for the Exhibit 3.0 library"
        :private true}
  build-modules-array [MODULES AUXMODULES]
  (let [auxModulesArray              (aget AUXMODULES "auxModules")
        examDateStringsArray         (aget MODULES "examDates")
        departmentStringsArray       (aget AUXMODULES "departments")
        lecturersStringsArray        (aget AUXMODULES "lecturers")
        prereqsStringsArray          (aget AUXMODULES "prereqs")
        preclusionsStringsArray      (aget AUXMODULES "preclusions")
        workloadStringsArray         (aget AUXMODULES "workload")
        departmentToFacultyIndexHash (aget AUXMODULES "departmentToFaculty")
        lessonTypesHash              (aget MODULES "lessonTypes")
        lessonTypesStringsArray      (aget MODULES "lessonType")
        ; return value, a JavaScript array of modules for the
        ; Exhibit 3.0 library
        modulesArray                 (array)
       ]
    (doseq [[idx moduleArrayRepr auxModuleArrayRepr]
              (map vector (range) (aget MODULES "modules") auxModulesArray)]
      (let [moduleCode      (module-array-repr/get-module-code moduleArrayRepr)
            moduleName      (module-array-repr/get-module-name moduleArrayRepr)
            moduleMc        (module-array-repr/get-module-mc moduleArrayRepr)
            moduleLevel     (module-array-repr/get-module-level moduleArrayRepr)
            moduleTypeArray (aux-module-array-repr/get-module-types
                             auxModuleArrayRepr)]
        (if (< idx 10)
          (do
            (.log js/console
              (str "Level of module " moduleCode " " moduleName " (" moduleMc
                   "Mcs) is " moduleLevel))
            (.log js/console
              (str "Types of module: " moduleTypeArray))))))
    ; Return the modulesArray
    modulesArray))

; Main entry point of the program
(defn ^:export init []
  ; Globals
  (let [$document  ($ js/document)
        AUXMODULES (aget js/window "AUXMODULES")
        MODULES    (aget js/window "MODULES")]

    ; Initialize window.MODULES_SELECTED
    (aset js/window "MODULES_SELECTED" (js-obj))

    ; initialize Zurb Foundation
    (.foundation $document)

    ; Create modules
    (one $document "scriptsLoaded.exhibit"
      (fn []
        (let [modulesArray (build-modules-array MODULES AUXMODULES)]
          (.log js/console modulesArray))))

    ; Retrieve Exhibit 3.0 Library
    (getScript "js/vendor/exhibit3-all.min.js" (fn []))))
