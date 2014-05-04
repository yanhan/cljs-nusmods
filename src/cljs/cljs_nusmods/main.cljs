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
        lessonTypesHash              (aget AUXMODULES "lessonTypes")
        lessonTypesStringsArray      (aget MODULES "lessonTypesStringsArray")
        ; return value, a JavaScript array of modules for the
        ; Exhibit 3.0 library
        modulesArray                 (array)
       ]
    (doseq [[idx moduleArrayRepr auxModuleArrayRepr]
              (map vector (range) (aget MODULES "modules") auxModulesArray)]
      (let [moduleCode        (module-array-repr/get-module-code
                                moduleArrayRepr)
            moduleName        (module-array-repr/get-module-name
                                moduleArrayRepr)
            moduleMc          (module-array-repr/get-module-mc moduleArrayRepr)
            moduleLevel       (module-array-repr/get-module-level
                                moduleArrayRepr)
            moduleTypeArray   (aux-module-array-repr/get-module-types
                               auxModuleArrayRepr)
            moduleExamDate    (module-array-repr/get-module-exam-date
                                examDateStringsArray moduleArrayRepr)
            lectureTimings    (module-array-repr/get-module-lecture-timings
                                lessonTypesHash lessonTypesStringsArray
                                moduleArrayRepr)
            tutorialTimings   (module-array-repr/get-module-tutorial-timings
                                lessonTypesHash lessonTypesStringsArray
                                moduleArrayRepr)
            moduleDepartment  (aux-module-array-repr/get-module-department
                                departmentToFacultyIndexHash
                                departmentStringsArray
                                auxModuleArrayRepr)
            moduleDescription (aux-module-array-repr/get-module-description
                                auxModuleArrayRepr)
            moduleLecturers   (aux-module-array-repr/get-module-lecturers
                                lecturersStringsArray auxModuleArrayRepr)
            modulePrereqs     (aux-module-array-repr/get-module-prereqs-string
                                prereqsStringsArray auxModuleArrayRepr)
            modulePreclusions
              (aux-module-array-repr/get-module-preclusions-string
                preclusionsStringsArray auxModuleArrayRepr)
            ; This will be pushed onto the `modulesArray` to be returned by
            ; the function
            jsModule          (js-obj "type" "Module", "label" moduleCode,
                                      "name" moduleName, "mc" moduleMc,
                                      "level" moduleLevel,
                                      "moduleType" moduleTypeArray,
                                      "exam" moduleExamDate,
                                      "lectureTimings" lectureTimings,
                                      "tutorialTimings" tutorialTimings,
                                      "department" moduleDepartment)]
        (if (not= moduleDescription -1)
            (aset jsModule "description" moduleDescription))
        (if (not (empty? moduleLecturers))
            (aset jsModule "lecturers" moduleLecturers))
        (if (not= modulePrereqs -1)
            (aset jsModule "prereqs" modulePrereqs))
        (if (not= modulePreclusions -1)
            (aset jsModule "preclusions" modulePreclusions))
        (if (< idx 10)
            (.log js/console (.stringify js/JSON jsModule)))))
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
