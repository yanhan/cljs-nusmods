(ns ^{:doc "main entry point for the cljs-nusmods project"}
  cljs-nusmods.main
  (:use [jayq.core :only [$ $deferred $when ajax attr document-ready done
                          fade-out hide is one parent prevent resolve show
                          unbind]])
  (:require [cljs-nusmods.module-array-repr     :as module-array-repr]
            [cljs-nusmods.aux-module-array-repr :as aux-module-array-repr]
            [cljs-nusmods.lesson-array-repr     :as lesson-array-repr]
            [cljs-nusmods.select2               :as select2]
            [cljs-nusmods.time                  :as time-helper]
            [cljs-nusmods.timetable             :as timetable]))

(def ^{:doc     "Whether the Exhibit3 library has been downloaded"
       :private true}
  EXHIBIT3-LOADED? false)

(def ^{:doc     "Whether the Exhibit3 stuff on the Module Finder page has been
                 initialized"
       :private true}
  EXHIBIT3-INITIALIZED? false)

(defn- getScript
  "Wrapper for $.getScript"
  [scriptUrl]
  (.getScript js/jQuery scriptUrl))

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
            moduleExamDate    (module-array-repr/get-module-exam-date-string
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
            (let [moduleCode (aget (aget evt "added") "id")]
              (if (and moduleCode
                       (not (timetable/is-module-selected? moduleCode)))
                  (timetable/add-module! moduleCode)))
            (let [nrModulesSelected (timetable/get-nr-modules-selected)
                  moduleText        (if (> nrModulesSelected 1)
                                        "Modules"
                                        "Module")]
              (.text $someSelectedDivText (str "Selected " nrModulesSelected " "
                                               moduleText))
              (show $someSelectedDiv)))

          ; some module was removed
          (aget evt "removed")
          (do
            (timetable/remove-module! (aget (aget evt "removed") "id"))
            (let [nrModulesSelected (timetable/get-nr-modules-selected)]
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
                  (do (timetable/remove-all-modules!)
                      (select2/select2-box-reset-val $select2-box)
                      (hide $someSelectedDiv)
                      (show $noneSelectedDiv)))))))

(def ^{:doc "Index for the Module Finder tab"
       :private true}
  MODULEFINDER-TAB-INDEX 0)

(def ^{:doc "Index for the Timetable Builder tab"
       :private true}
  TIMETABLE-TAB-INDEX 1)

(def ^{:doc     "Whether we started downloading JavaScript files for
                 Module Finder page"
       :private true}
  MODULE-FINDER-SCRIPTS-STARTED-DL? false)

(def ^{:doc     "Whether downloading of JavaScript files for Module Finder page
                 is done"
       :private true}
  MODULE-FINDER-SCRIPTS-DLED? false)

(def ^{:doc     "Return value of js/setInterval on `check-initialize-exhibit3`"
       :private true}
  INITIALIZE-EXHIBIT3-INTERVAL-VAL nil)

(defn- timetable-builder-tab-click-handler
  "Click event handler for the `Timetable Builder` tab"
  []
  ; Clear the interval when user switches to `Timetable Builder` page
  (if (and (not EXHIBIT3-INITIALIZED?)
           (not (nil? INITIALIZE-EXHIBIT3-INTERVAL-VAL)))
      (do
        (js/clearInterval INITIALIZE-EXHIBIT3-INTERVAL-VAL)
        (set! INITIALIZE-EXHIBIT3-INTERVAL-VAL nil)))
  (hide ($ :#module-finder))
  (.removeClass (parent ($ :#module-finder-tab-link)) "active")
  (.addClass (parent ($ :#timetable-builder-tab-link)) "active")
  (show ($ :#timetable-builder))
  (select2/shift-select2-container-to "module-finder-sidebar"
                                      "timetable-builder-controls")
  (aset js/window "ActiveTab" TIMETABLE-TAB-INDEX))

(defn- initialize-exhibit3 [MODULES AUXMODULES]
  "Initialize Exhibit3 database and UI for Module Finder page"
  (if (and (not EXHIBIT3-INITIALIZED?)
           EXHIBIT3-LOADED?
           MODULE-FINDER-SCRIPTS-DLED?
           (= (aget js/window "ActiveTab") MODULEFINDER-TAB-INDEX))
    (let [$timetable-builder-tab-link ($ :#timetable-builder-tab-link)]
      (set! EXHIBIT3-INITIALIZED? true)
      ; Unbind the click event handler for `Timetable Builder` tab link so
      ; the user cannot switch tabs when we are initializing Exhibit3 here and
      ; screw things up
      (unbind $timetable-builder-tab-link "click")
      (aux-module-array-repr/init!)
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
        ; rebind click event handler for `Timetable Builder` tab link
        (js/setTimeout (fn []
                         (.click $timetable-builder-tab-link
                                 timetable-builder-tab-click-handler))
                       3000)))))

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
       :weekTextIdx  0-indexed Integer into an Array of WeekText strings
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
                  weekTextIdx (lesson-array-repr/get-lesson-weektext
                                lessonArrayRepr)
                  lessonRepr  {:venue lessonVenue, :day lessonDay,
                               :startTime startTime, :endTime endTime
                               :weekTextIdx weekTextIdx}]
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

       modCode -> 'name'     -> module name
               -> 'examDate' -> index to an array of Exam Date strings
               -> 'lessons'  -> lessons built by
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
                examDateIdx      (module-array-repr/get-module-exam-date
                                   moduleArrayRepr)
                moduleTimetable  (module-array-repr/get-module-timetable
                                   moduleArrayRepr)
                lessonsMap       (build-lessons-map-from-module-timetable
                                   moduleTimetable
                                   lessonTypesStringsArray
                                   venuesStringsArray
                                   weekTextStringsArray)]
               [moduleCode {"name" moduleName, "lessons" lessonsMap,
                            "examDateIdx" examDateIdx}]))
           modulesArray))))

(defn- check-and-initialize-exhibit3
  "Used by `js/setInterval` to repeatedly check for initialization of
   the module finder page"
  []
  (.log js/console "In `check-and-initialize-exhibit3`")
  (.log js/console (str "INITIALIZE-EXHIBIT3-INTERVAL-VAL = "
                        INITIALIZE-EXHIBIT3-INTERVAL-VAL))
  (if EXHIBIT3-INITIALIZED?
      (do
        (.log js/console "Exhibit3 initialized, clearing interval")
        (js/clearInterval INITIALIZE-EXHIBIT3-INTERVAL-VAL)
        (set! INITIALIZE-EXHIBIT3-INTERVAL-VAL nil))
      (do
        (.log js/console "Going to initialize Exhibit3")
        (initialize-exhibit3 (aget js/window "MODULES")
                             (aget js/window "AUXMODULES")))))

(defn- short-url-setup
  "Setup url shortening"
  []
  (let [ZeroClipboard      (aget js/window "ZeroClipboard")
        localStorage       (aget js/window "localStorage")
        zcbClient          (ZeroClipboard. ($ :#copy-to-clipboard))
        $urlShortenerInput ($ :#url-shortener)]
    (.click $urlShortenerInput
            (fn []
              (let [currentUrl      (aget js/document "URL")
                    localStorageKey (str "shortUrl-" currentUrl)

                    mbShortUrl
                    (and localStorage
                         (.getItem localStorage localStorageKey))]
                (if mbShortUrl
                    (.val $urlShortenerInput mbShortUrl)
                    (ajax "/shorten"
                          {:data     {:url (js/encodeURI
                                             (aget js/document "URL"))}
                           :dataType "json"

                           :success
                           (fn [data]
                             (if (= (aget data "status") 200)
                                 (let [shortUrl (aget data "shortUrl")]
                                   (.val $urlShortenerInput shortUrl)
                                   (.setItem localStorage localStorageKey
                                             shortUrl))
                                 (.val $urlShortenerInput
                                       (aget data "message"))))

                           :error    (fn [jqXHR textStatus errorThrown]
                                       (.val $urlShortenerInput
                                             "an error occurred"))})))))))

; Main entry point of the program
(defn ^:export init [acad-year sem]
  ; Globals
  (document-ready
    (fn []
      (let [$document     ($ js/document)
            $window       ($ js/window)
            MODULES       (aget js/window "MODULES")
            $timetable    ($ :#timetable)
            ZeroClipboard (aget js/window "ZeroClipboard")]

        ; hides the overlay once the `Timetable Builder` page is done loading
        (.on js/Pace "done" (fn []
                              (fade-out ($ ".overlay"))))

        (aset js/window "ActiveTab" TIMETABLE-TAB-INDEX)
        (aset js/window "ModulesMap" (build-timetable-module-map MODULES))

        ; initialize `WEEK-TEXT-ARRAY`
        (timetable/set-WEEK-TEXT-ARRAY! (aget MODULES "weekText"))
        ; initialize `EXAM-DATE-ARRAY`
        (timetable/set-EXAM-DATE-ARRAY! (aget MODULES "examDates"))

        ; Initialize timetable
        (timetable/timetable-create!)

        ; Iniialize Select2
        (select2/init-select2-element select2/$Select2-Box)
        (init-dom-clear-modules select2/$Select2-Box)

        ; Button group for Show / Hide controls
        ; Toggles the addition/removal of the 'active' class on the buttons on
        ; click
        (doseq [[btn cssClass]
                (map vector ($ ".btn-group > .btn")
                     ["hide-module-code" "hide-lesson-group" "hide-venue"
                      "hide-module-name" "hide-frequency"])]
          (let [$btn ($ btn)]
            (.click ($ btn)
                    (fn [evt]
                      (prevent evt)
                      (.toggleClass $btn "active")
                      (.toggleClass $timetable cssClass)))))

        (one $document "scriptsLoaded.exhibit"
             #(set! EXHIBIT3-LOADED? true))

        ; Code for tabs
        (hide ($ :#module-finder))
        (.click ($ :#module-finder-tab-link)
                (fn []
                  (if (not MODULE-FINDER-SCRIPTS-STARTED-DL?)
                      (do
                        (set! MODULE-FINDER-SCRIPTS-STARTED-DL? true)
                        (done ($when (getScript (str "/js/mods/" acad-year "/"
                                                     sem "/auxmodinfo.js"))
                                     (getScript "/js/vendor/exhibit3-all.min.js")
                                     ($deferred (fn [deferred]
                                                  ($ (resolve deferred nil)))))
                              (fn []
                                (set! MODULE-FINDER-SCRIPTS-DLED? true)))))
                  (if (and (not EXHIBIT3-INITIALIZED?)
                           (nil? INITIALIZE-EXHIBIT3-INTERVAL-VAL))
                    (set! INITIALIZE-EXHIBIT3-INTERVAL-VAL
                          (js/setInterval check-and-initialize-exhibit3 1000)))
                  (hide ($ :#timetable-builder))
                  (show ($ :#module-finder))
                  (.removeClass (parent ($ :#timetable-builder-tab-link)) "active")
                  (.addClass (parent ($ :#module-finder-tab-link)) "active")
                  (aset js/window "ActiveTab" MODULEFINDER-TAB-INDEX)
                  (select2/shift-select2-container-to "timetable-builder-controls"
                                                      "module-finder-sidebar")))

        (.click ($ :#timetable-builder-tab-link)
                timetable-builder-tab-click-handler)

        (timetable/add-module-lesson-groups-from-url-hash-or-local-storage!)

        ; Add click event handler for `Add` module buttons on Module Finder page
        (.on ($ "body")
             "click"
             ".add-module-btn"
             (fn [evt]
               (prevent evt)
               (this-as this
                        (let [$this        ($ this)
                              moduleCode   (.data $this "module-code")
                              qtipContent  (timetable/add-module! moduleCode)

                              tooltip
                              (.qtip $this
                                     (js-obj "content"   qtipContent
                                             "overwrite" true

                                             ; show qtip immediately
                                             "show"      (js-obj "ready" true)

                                             ; dont hide the qtip
                                             "hide"      (js-obj "event" false)

                                             "position"
                                             (js-obj "my" "bottom center"
                                                     "at" "top center"
                                                     "viewport" $window)))

                              api          (.qtip tooltip "api")]
                          ; destroy qTip after 2s.
                          ; For some reason this works even if we set a new qTip
                          (js/setTimeout (fn [] (.destroy api)) 2000)))))

       ; Copy short url button
       (.config ZeroClipboard
                (js-obj "swfPath"
                        (str "http://cdnjs.cloudflare.com/ajax/libs"
                             "/zeroclipboard/2.1.1/ZeroClipboard.swf")))
       (short-url-setup)))))
