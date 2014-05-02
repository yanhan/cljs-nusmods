(ns ^{:doc "main entry point for the cljs-nusmods project"}
  cljs-nusmods.main
  (:use [jayq.core :only [$ one]])
  (:require [cljs-nusmods.module-array-repr :as module-array-repr]))

; Initialize window.MODULES_SELECTED
(aset js/window "MODULES_SELECTED" (js-obj))

; $(document)
(def $document ($ js/document))

; Globals
(def AUXMODULES (aget js/window "AUXMODULES"))
(def MODULES    (aget js/window "MODULES"))

; initialize Zurb Foundation
(.foundation $document)

(defn build-modules-array []
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
    (doseq [[idx moduleArrayRepr] (map vector (range) (aget MODULES "modules"))]
      (let [moduleLevel (module-array-repr/get-module-level moduleArrayRepr)]
        (if (< idx 10)
          (.log js/console (str "Level of module " (nth moduleArrayRepr 0) " is " moduleLevel)))))
    ; Return the modulesArray
    modulesArray))

; Create modules
(one $document "scriptsLoaded.exhibit"
  (fn []
    (let [modulesArray (build-modules-array)]
      (.log js/console modulesArray))))

; Retrieve Exhibit 3.0 Library
(defn getScript [scriptUrl cb]
  (.getScript js/jQuery scriptUrl cb))

(getScript "js/vendor/exhibit3-all.min.js" (fn []))
