(ns ^{:doc "main entry point for the cljs-nusmods project"}
  cljs-nusmods.main
  (:use [jayq.core :only [$ one]])
  (:require [cljs-nusmods.module-array-repr     :as module-array-repr]
            [cljs-nusmods.aux-module-array-repr :as aux-module-array-repr]
            [cljs-nusmods.time                  :as time-helper]))

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
            moduleWorkload    (aux-module-array-repr/get-module-workload-string
                                workloadStringsArray auxModuleArrayRepr)
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
        (if (not= moduleWorkload -1)
            (aset jsModule "workload" moduleWorkload))
        (.push modulesArray jsModule)))
    ; Return the modulesArray
    modulesArray))

(defn- add-module-types-to-exhibit-items
  "Adds `ModuleType` information to a JavaScript Array used as the `items`
   value for an Exhibit database, and returns the Array."
  [itemsArray]
  (.push itemsArray (js-obj "type" "ModuleType", "label" "Faculty"))
  (.push itemsArray (js-obj "type" "ModuleType", "label" "Breadth / UE"))
  (.push itemsArray (js-obj "type" "ModuleType", "label" "Singapore Studies"))
  (.push itemsArray (js-obj "type" "ModuleType", "label" "GEM"))
  (.push itemsArray (js-obj "type" "ModuleType", "label" "Not in CORS"))
  itemsArray)

(defn- add-module-departments-to-exhibit-items
  "Adds `ModuleDepartment` information to a JavaScript Array used as the `items`
   value for an Exhibit 3 database, and returns the Array.
   This enables the HierarchicalFacet on the Module Finder page which filters
   modules by Faculty / Department"
  [AUXMODULES itemsArray]
  (let [departmentToFacultyIndexHash (aget AUXMODULES "departmentToFaculty")
        departmentStringsArray       (aget AUXMODULES "departments")]
    (doseq [[departmentIdxString facultyIdx]
              (filter (fn [kv]
                        (let [departmentIdx (int (first kv))
                              facultyIdx    (second kv)]
                          (not= departmentIdx facultyIdx)))
                      (js->clj departmentToFacultyIndexHash))]
      (let [departmentIdx    (int departmentIdxString)
            departmentString (nth departmentStringsArray departmentIdx)
            facultyString    (nth departmentStringsArray facultyIdx)]
        (.push itemsArray (js-obj "type"       "ModuleDepartment"
                                  "label"      departmentString
                                  "subtopicOf" facultyString))))
    itemsArray))

(defn- add-lesson-time-to-exhibit-items
  "Adds `LessonTime` information to a JavaScript Array used as the `items`
   value for an Exhibit 3 database, and returns the Array.
   This enables the filter by `Lecture Timings` and filter by `Tutorial Timings`
   HierarchicalFacets on the Module Finder page."
  [itemsArray]
  (let [timeOfDayVec ["Morning" "Afternoon" "Evening"]]
    (doseq [day time-helper/DAY_INTEGER_TO_STRING]
      (doseq [timeOfDay timeOfDayVec]
        (.push itemsArray (js-obj "type"       "LessonTime"
                                  "label"      (str day " " timeOfDay)
                                  "subtopicOf" day))))
    itemsArray))

(defn- add-aux-info-to-exhibit-items
  "Add `ModuleType` and `ModuleDepartment` information to a JavaScript Array
   used as the `items` value for an Exhibit 3 database, and returns the Array."
  [AUXMODULES itemsArray]
  (add-module-types-to-exhibit-items itemsArray)
  (add-module-departments-to-exhibit-items AUXMODULES itemsArray)
  (add-lesson-time-to-exhibit-items itemsArray)
  itemsArray)

(defn- select2-query-fn
  "query function for the Select2 `Select Modules for Timetable` input"
  [modulesArray]
  (fn [options]
    (let [result         (js-obj "results" (array))
          resultsPerPage 20]
      (if (nil? (aget options "context"))
          (let [nrModules    (.-length modulesArray)
                searchTerm   (.toUpperCase (aget options "term"))
                firstResults (array)
                modsSatisfyingSearchTerm
                  (filter
                    (fn [modObj]
                      (let [haystack (str (.toUpperCase (aget modObj "label"))
                                          " "
                                          (.toUpperCase (aget modObj "name")))]
                        (not= -1 (.indexOf haystack searchTerm))))
                    modulesArray)]
            (doseq [modObj modsSatisfyingSearchTerm]
              (.push firstResults (js-obj "id"   (aget modObj "label"),
                                          "text" (str (aget modObj "label")
                                                      " "
                                                      (aget modObj "name")))))
            (aset options "context"
                  (js-obj "searchResults" firstResults,
                          "nrResults"     (.-length firstResults)))))
      ; options.page starts counting from 1
      (if (< (* resultsPerPage (- (aget options "page") 1))
             (aget (aget options "context") "nrResults"))
          (let [i     (* resultsPerPage (- (aget options "page") 1))
                limit (min (+ i resultsPerPage)
                           (aget (aget options "context") "nrResults"))]
            (aset result "more" true)
            (doseq [k (range i limit)]
              (.push (aget result "results")
                     (nth (aget (aget options "context") "searchResults") k))))
          (aset result "more" false))
      (aset result "context" (aget options "context"))
      ((aget options "callback") result))))

(defn- init-select2-input-box
  "Initialize the Select2 `Select Modules for Timetable` input"
  [modulesArray]
  (let [$searchModules ($ :#search_modules)]
    (.select2 $searchModules
              (js-obj "multiple"      true
                      "width"         "100%"
                      "placeholder"   "Type code/title to add mods"
                      "initSelection" (fn [])
                      "query"         (select2-query-fn modulesArray)))))

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
        (let [modulesArray (build-modules-array MODULES AUXMODULES)
              itemsArray   (add-aux-info-to-exhibit-items
                             AUXMODULES
                             (.concat modulesArray (array)))
              Exhibit      (aget js/window "Exhibit")
              database     (aset js/window "database"
                                 (.create (.-Database Exhibit)))
              myExhibit    (aset js/window "exhibit" (.create Exhibit))]
          (.loadData
            database
            (js-obj "types"      (js-obj
                                   "Module" (js-obj "pluralLabel" "Modules"))
                    "properties" (js-obj
                                   "mc"         (js-obj "valueType" "number")
                                   "level"      (js-obj "valueType" "number")
                                   "moduleType" (js-obj "valueType" "item"))
                    "items"      itemsArray))
          (.configureFromDOM myExhibit)
          (init-select2-input-box modulesArray))))

    ; Retrieve Exhibit 3.0 Library
    (getScript "js/vendor/exhibit3-all.min.js" (fn []))))
