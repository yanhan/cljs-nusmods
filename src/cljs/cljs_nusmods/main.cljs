(ns ^{:doc "main entry point for the cljs-nusmods project"}
  cljs-nusmods.main
  (:use [jayq.core :only [$ attr document-ready hide is one prevent show]])
  (:require [cljs-nusmods.module-array-repr     :as module-array-repr]
            [cljs-nusmods.aux-module-array-repr :as aux-module-array-repr]
            [cljs-nusmods.lesson-array-repr     :as lesson-array-repr]
            [cljs-nusmods.select2               :as select2]
            [cljs-nusmods.time                  :as time-helper]
            [cljs-nusmods.timetable             :as timetable]))

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

(defn- init-dom-clear-modules
  "Code for `(Clear Modules)` button on top of the `Select Modules for
   Timetable` input box"
  [$select2-box]
  (let [$noneSelectedDiv     ($ :.search-modules-none-selected-div)
        $someSelectedDiv     ($ :.search-modules-selected-div)
        $someSelectedDivText ($ :.search-modules-nr-selected)]

    (hide $someSelectedDiv)

    (.change
      $select2-box
      (fn [evt]
        (cond
          ; some module has just been added
          (aget evt "added")
          (do
            (if (is $noneSelectedDiv ":visible")
                (hide $noneSelectedDiv))
            ; Add the new module if it has not been added.
            ; We perform this check because the 'change' event could have
            ; been triggered by the Timetable code instead of a UI action
            (let [moduleCode (aget (nth (aget evt "added") 0) "id")]
              (if (not (timetable/is-module-selected? moduleCode))
                  (timetable/add-module moduleCode)))
            (if (is $someSelectedDiv ":visible")
                (.text $someSelectedDivText
                       (str "Selected " (timetable/nr-modules-selected)
                            " Modules"))
                ; 0 modules -> 1 module
                (do (.text $someSelectedDivText "Selected 1 Module")
                    (show $someSelectedDiv))))

          ; some module was removed
          (aget evt "removed")
          (do
            (timetable/remove-module (aget (aget evt "removed") "id"))
            (let [nrModulesSelected (timetable/nr-modules-selected)]
              (cond
                (<= nrModulesSelected 0)
                (do (hide $someSelectedDiv)
                    (show $noneSelectedDiv))

                (= nrModulesSelected 1)
                (.text $someSelectedDivText "Selected 1 Module")

                :else
                (.text $someSelectedDivText
                       (str "Selected " nrModulesSelected " Modules"))))))))
    (.click ($ :.search-modules-clear-all-modules)
            (fn [evt]
              (prevent evt)
              (if (js/confirm
                     "Are you sure you want to clear all selected modules?")
                  (do (timetable/remove-all-modules)
                      (select2/select2-box-reset-val $select2-box)
                      (hide $someSelectedDiv)
                      (show $noneSelectedDiv)))))))

(def ^{:doc "Index for the Module Finder tab"
       :private true}
  MODULEFINDER-TAB-INDEX 0)

(def ^{:doc "Index for the Timetable Builder tab"
       :private true}
  TIMETABLE-TAB-INDEX 1)

(defn- initialize-exhibit3 [MODULES AUXMODULES]
  "Initialize Exhibit3 database and UI for Module Finder page"
  (if (and (not (aget js/window "Exhibit3_Initialized"))
           (aget js/window "Exhibit3_Loaded")
           (= (aget js/window "ActiveTab") MODULEFINDER-TAB-INDEX))
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
      (aset js/window "Exhibit3_Initialized" true))))

(defn- build-lessons-map-from-module-timetable
  "Builds a Clojure Map representation of a module's lessons used internally
   by the Timetable Builder page.

   An example:

       Lecture -> Lecture Group 1 -> [vector of lessons in Lecture Group 1]
                  Lecture Group 2 -> [vector of lessons in Lecture Group 2]
                        .
                        .
                        .
                  Lecture Group L -> [vector of lessons in Lecture Group N]

       Tutorial -> Tut Group 1 -> [vector of lessons in Tut Group 1]
                   Tut Group 2 -> [vector of lessons in Tut Group 2]
                        .
                        .
                        .
                   Tut Group T -> [vector of lessons in Tut Group T]

   Lessons contain the following keys and values:

       :venue        String of the lesson venue
       :day          0-indexed Integer of the day, where 0 = Monday,
                       1 = Tuesday, etc, until 4 = Friday
       :startTime    0-indexed Integer of the time, where 0 = 0800, 1 = 0830,
                       and so on.
       :endTime      0-indexed Integer, similar meaning as :startTime
   "
  [moduleTimetable lessonTypesStringsArray venuesStringsArray
   weekTextStringsArray]
  (reduce (fn [lessonsMap lessonArrayRepr]
            (let [lessonLabel (lesson-array-repr/get-lesson-label
                                lessonArrayRepr)
                  lessonType  (lesson-array-repr/get-lesson-type-string
                                lessonArrayRepr lessonTypesStringsArray)
                  lessonDay   (lesson-array-repr/get-lesson-day lessonArrayRepr)
                  lessonVenue (lesson-array-repr/get-lesson-venue-string
                                lessonArrayRepr venuesStringsArray)
                  startTime   (lesson-array-repr/get-lesson-start-time
                                lessonArrayRepr)
                  endTime     (lesson-array-repr/get-lesson-end-time
                                lessonArrayRepr)
                  lessonRepr  {:venue lessonVenue, :day lessonDay,
                               :startTime startTime, :endTime endTime}]
              (update-in lessonsMap [lessonType lessonLabel]
                         (fn [lessonsVec]
                           (if (empty? lessonsVec)
                               [lessonRepr]
                               (conj lessonsVec lessonRepr))))))
          {}
          moduleTimetable))

(defn- build-timetable-module-map
  "Builds a Clojure Map of modules to be used for the Timetable Builder page.

   The map is of the following structure:

       modCode -> 'name'    -> module name
               -> 'lessons' -> lessons built by
                               `build-lessons-map-from-module-timetable`
                               function"
  [MODULES]
  (let [modulesArray            (aget MODULES "modules")
        lessonTypesStringsArray (aget MODULES "lessonTypesStringsArray")
        venuesStringsArray      (aget MODULES "venues")
        weekTextStringsArray    (aget MODULES "weekText")]
    (into
      {}
      (map
        (fn [moduleArrayRepr]
          (let [moduleCode       (module-array-repr/get-module-code
                                   moduleArrayRepr)
                moduleName       (module-array-repr/get-module-name
                                   moduleArrayRepr)
                moduleTimetable  (module-array-repr/get-module-timetable
                                   moduleArrayRepr)
                lessonsMap       (build-lessons-map-from-module-timetable
                                   moduleTimetable
                                   lessonTypesStringsArray
                                   venuesStringsArray
                                   weekTextStringsArray)]
               [moduleCode {"name" moduleName, "lessons" lessonsMap}]))
           modulesArray))))

; Main entry point of the program
(defn ^:export init []
  ; Globals
  (let [$document     ($ js/document)
        AUXMODULES    (aget js/window "AUXMODULES")
        MODULES       (aget js/window "MODULES")]

    (aset js/window "Exhibit3_Initialized" false)
    (aset js/window "Exhibit3_Loaded" false)
    (aset js/window "ActiveTab" TIMETABLE-TAB-INDEX)
    (aset js/window "ModulesMap" (build-timetable-module-map MODULES))

    ; Initialize timetable
    (timetable/init)

    ; Iniialize Select2
    (select2/init-select2-element select2/$Select2-Box)
    (init-dom-clear-modules select2/$Select2-Box)

    ; Code for tabs
    (hide ($ :#module-finder))
    (.click ($ :#module-finder-tab-link)
            (fn []
              (hide ($ :#timetable-builder))
              (show ($ :#module-finder))
              (aset js/window "ActiveTab" MODULEFINDER-TAB-INDEX)
              (select2/shift-select2-container-to "timetable-builder-controls"
                                                  "module-finder-sidebar")
              (initialize-exhibit3 MODULES AUXMODULES)))

    (.click ($ :#timetable-builder-tab-link)
            (fn []
              (hide ($ :#module-finder))
              (show ($ :#timetable-builder))
              (select2/shift-select2-container-to "module-finder-sidebar"
                                                  "timetable-builder-controls")
              (aset js/window "ActiveTab" TIMETABLE-TAB-INDEX)))

    ; Create modules
    (one $document "scriptsLoaded.exhibit"
         (fn []
           (aset js/window "Exhibit3_Loaded" true)
           (initialize-exhibit3 MODULES AUXMODULES)))

    ; Retrieve Exhibit 3.0 Library
    (getScript "js/vendor/exhibit3-all.min.js" (fn []))))
