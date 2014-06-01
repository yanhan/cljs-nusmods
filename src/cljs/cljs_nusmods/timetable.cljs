(ns ^{:doc "Code for Timetable Builder page"}
  cljs-nusmods.timetable
  (:use [jayq.core :only [$ attr before children data hide insert-after is
                          parent prepend prevent remove-attr show text width]])
  (:require [clojure.set]
            [clojure.string]
            [cljs-nusmods.select2 :as select2]
            [cljs-nusmods.time    :as time-helper]))

; Data type Definitions
; =====================
;
; ModulesMapLesson
; ----------------
; A Lesson in the `ModulesMap` JavaScript global.
;
; {
;   :venue     -> String of the lesson venue
;   :day       -> 0-indexed integer of the day, where 0 = Monday,
;                 1 = Tuesday, etc, until 4 = Friday
;   :startTime -> 0-indexed integer of the time, where 0 = 0800, 1 = 030, and
;                 so on.
;   :endTime   -> similar to :startTime
; }
;
;
; TimetableLessonInfo (abbrev: ttLessonInfo)
; ------------------------------------------
; Describes a lesson in the `Timetable` global; this is the key used for each
; row in a day of the `Timetable`.
;
; Each instance of `TimetableLessonInfo` is a map in the following format:
;
;   {
;     :moduleCode  -> module code string of this lesson
;     :lessonType  -> lesson type string of this lesson
;     :lessonGroup -> lesson group string of this lesson
;     :startTime   -> 0-indexed start time of the lesson
;     :endTime     -> 0-indexed end time of the lesson
;   }
;
;
; ModulesSelectedLessonInfo (abbrev: modSelLessonInfo)
; ----------------------------------------------------
; Describes a lesson in the `ModulesSelected` global.
; Each instance is a map in the following format:
;
;   {
;     :day       -> 0-indexed integer of the day of the lesson
;     :rowNum    -> 0-indexed integer of the row
;     :startTime -> 0-indexed start time of the lesson
;     :endTime   -> 0-indexed end time of the lesson
;   }
;
;
; PreToUrlHashLessonGroup
; -----------------------
; Data representation of a lesson group amenable for conversion to a url hash.
; Each instance is a map in the following format:
;
;   {
;     :lessonType  -> short form of lesson type
;     :lessonGroup -> lesson group selected for the lesson type
;   }
;
;
; PreToUrlHashModule
; ------------------
; Data representation of a module amenable for conversion to a url hash.
; Each instance is a map in the following format:
;
;   {
;     :moduleCode                       -> module code string
;     :preToUrlHashLessonGroupSeqSorted -> sequence of `PreToUrlHashLessonGroup`
;                                          sorted lexicographically by
;                                          `:lessonType`
;   }

(defn- get-ModulesMap
  "Retrieves the `ModulesMap` JavaScript global variable"
  []
  (aget js/window "ModulesMap"))

(defn- get-module-name-from-module-code
  "Given a module code, returns the name of the module"
  [moduleCode]
  (let [modulesMap (get-ModulesMap)]
    (get-in modulesMap [moduleCode "name"])))

(defn- get-module-lesson-groups-map
  "Retrieves the map of lesson group strings to vector of lessons"
  [moduleCode lessonType]
  (let [modulesMap (get-ModulesMap)]
    (get-in modulesMap [moduleCode "lessons" lessonType])))

(defn- module-lesson-type-has-multiple-choices?
  "Given a module code and a long form lesson type, determines if there are
   multiple choices of lesson groups for that lesson type (eg. multiple
   tutorial choices for a module)."
  [moduleCode lessonType]
  (let [lessonGroupsMap (get-module-lesson-groups-map moduleCode lessonType)]
    (> (count lessonGroupsMap) 1)))

(defn- get-all-lesson-types-for-module
  "Returns a sequence of strings, where each string is a long form lesson type
   for a module."
  [moduleCode]
  (let [modulesMap (get-ModulesMap)]
    (keys (get-in modulesMap [moduleCode "lessons"]))))

(defn- get-all-lesson-group-strings
  "Given a module and a lesson type, returns a sequence of all its lesson
   group strings."
  [moduleCode lessonType]
  (let [lessonGroupsMap (get-module-lesson-groups-map moduleCode lessonType)]
    (keys lessonGroupsMap)))

(defn- get-first-lesson-group-for-module-lesson-type
  "Given a module and a lesson type, retrieves the 'first' lesson group string
   for that module.
   This is used for selecting a random lesson group for a lesson type of a
   module."
  [moduleCode lessonType]
  (first (get-all-lesson-group-strings moduleCode lessonType)))

(defn- module-has-lesson-group?
  "Given a module code, a lesson type, and a lesson group, determines if the
   module has such a lesson group."
  [moduleCode lessonType lessonGroup]
  (let [lessonGroupsMap (get-module-lesson-groups-map moduleCode lessonType)]
    (not (nil? (get lessonGroupsMap lessonGroup)))))

(defn- get-ModulesMapLesson-seq
  "Returns a sequence of `ModulesMapLesson` objects representing all lessons
   for a lesson group of the module."
  [moduleCode lessonType lessonGroup]
  (get (get-module-lesson-groups-map moduleCode lessonType) lessonGroup))

(def ^{:doc     "Width in pixels of a half hour timeslot"
       :private true
       }
  half-hour-pixels 35)

(def ^{:doc     "Number of available background colors for lesson divs"
       :private true}
  Nr-Available-Bg-Colors 24)

(def ^{:doc     "Sequence of background color indices"
       :private true}
  Bg-Colors-Seq [])

(defn get-next-lesson-bg-color-css-class
  "Returns the next css background color class for a lesson div"
  []
  (if (empty? Bg-Colors-Seq)
      (set! Bg-Colors-Seq (shuffle (range 0 Nr-Available-Bg-Colors))))
  (let [currentIdx (first Bg-Colors-Seq)]
    (set! Bg-Colors-Seq (rest Bg-Colors-Seq))
    (str "lesson-bg-"
         (if (< currentIdx 10)
             "0"
             "")
         currentIdx)))

(def ^{:doc     "In-memory representation of a Timetable"
       :private true}
  Timetable nil)

(def ^{
  :doc
  "Modules selected by the user. This map has the following
   format:

     Module Code (String) ->
       {
         Lesson Type String eg. 'Lecture', 'Tutorial', etc
           -> {
                :label -> Lesson label String
                :info  -> Vector of maps in the following format:
                            {:day       -> 0-indexed day of the lesson
                             :rowNum    -> 0-indexed <tr> where the lesson is
                                           stored
                             :startTime -> 0-indexed start time of lesson
                             :endTime   -> 0-indexed end time of lesson}
              }
       }"
  :private true
  }
  ModulesSelected {})

(def ^{:doc     "Vector containing module code strings in the order the modules
                 were added."
       :private true}
  ModulesSelectedOrder [])

(defn get-nr-modules-selected
  "Return the number of modules selected by the user"
  []
  (count ModulesSelected))

(defn get-selected-module-codes-as-js-array
  "Returns a JavaScript Array of Strings, where each String is the module code
   of a selected module"
  []
  (clj->js ModulesSelectedOrder))

(defn is-module-selected?
  "Determines if a module has been selected"
  [moduleCode]
  (contains? ModulesSelected moduleCode))

(def ^{:doc     "Converts a short form lesson type string to its long form"
       :private true}
  Lesson-Type-Short-To-Long-Form
  {"DL"  "DESIGN LECTURE",
   "L"   "LECTURE",
   "LAB" "LABORATORY",
   "PL"  "PACKAGED LECTURE",
   "PT"  "PACKAGED TUTORIAL",
   "R"   "RECITATION",
   "SEM" "SEMINAR-STYLE MODULE CLASS",
   "ST"  "SECTIONAL TEACHING",
   "T"   "TUTORIAL",
   "T2"  "TUTORIAL TYPE 2",
   "T3"  "TUTORIAL TYPE 3"})

(def ^{:doc     "Converts a long form lesson type string to its short form"
       :private true}
  Lesson-Type-Long-To-Short-Form
  {"DESIGN LECTURE"             "DL",
   "LECTURE"                    "L",
   "LABORATORY"                 "LAB",
   "PACKAGED LECTURE"           "PL",
   "PACKAGED TUTORIAL"          "PT",
   "RECITATION"                 "R",
   "SEMINAR-STYLE MODULE CLASS" "SEM",
   "SECTIONAL TEACHING"         "ST",
   "TUTORIAL"                   "T",
   "TUTORIAL TYPE 2"            "T2",
   "TUTORIAL TYPE 3"            "T3"
   })

(defn- preToUrlHashModule-to-url-hash-string
  "Converts a `PreToUrlHashModule` object to a url hash string."
  [preToUrlHashModule]
  (let [moduleCode (:moduleCode preToUrlHashModule)]
    (clojure.string/join
      "&"
      (map #(str moduleCode "_" (:lessonType %1) "=" (:lessonGroup %1))
           (:preToUrlHashLessonGroupSeqSorted preToUrlHashModule)))))

(defn- sort-PreToUrlHashLessonGroup-seq
  "Sorts a sequence of `PreToUrlHashLessonGroup` objects lexicographically by
   `:lessonType`."
  [preToUrlHashLessonGroupSeq]
  (sort (fn [a b] (< (:lessonType a) (:lessonType b)))
        preToUrlHashLessonGroupSeq))

(defn- get-PreToUrlHashLessonGroup-seq-for-selected-module
  "Given the module code of a module that has been selected, returns a sequence
   of `PreToUrlHashLessonGroup` objects for that module."
  [moduleCode]
  (map (fn [[lessonType lessonTypeVal]]
         {:lessonType  (Lesson-Type-Long-To-Short-Form lessonType),
          :lessonGroup (:label lessonTypeVal)})
       (get ModulesSelected moduleCode)))

(defn- get-PreToUrlHashModule-for-selected-module
  "Retrieves a `PreToUrlHashModule` for a selected module."
  [moduleCode]
  {:moduleCode moduleCode,

   :preToUrlHashLessonGroupSeqSorted
   (sort-PreToUrlHashLessonGroup-seq
     (get-PreToUrlHashLessonGroup-seq-for-selected-module moduleCode))})

(defn- get-document-location-hash
  "Retrieves the current value of `document.location.hash`"
  []
  (aget (aget js/document "location") "hash"))

(defn- set-document-location-hash!
  "Sets `document.location.hash` to a given string"
  [newDocLocationHash]
  (aset (aget js/document "location") "hash" newDocLocationHash))

(defn- set-document-location-hash-based-on-modules-order!
  "Sets document.location.hash based on the `ModulesSelectedOrder` global."
  []
  (let [preToUrlHashModuleSeq
        (map #(get-PreToUrlHashModule-for-selected-module %1)
             ModulesSelectedOrder)]
    (.log js/console (str "ModulesSelectedOrder = " (.stringify js/JSON (clj->js ModulesSelectedOrder))))
    (set-document-location-hash!
      (clojure.string/join "&"
                           (map #(preToUrlHashModule-to-url-hash-string %1)
                                preToUrlHashModuleSeq)))))

(defn- update-document-location-hash-with-new-module!
  "Updates document.location.hash with the `PreToUrlHashModule` of a
   newly added module."
  [preToUrlHashModule]
  (let [orgUrlHash    (get-document-location-hash)

        moduleUrlHash
        (preToUrlHashModule-to-url-hash-string preToUrlHashModule)]
    (.log js/console (str "orgUrlHash = \"" orgUrlHash "\""))
    (.log js/console (str "preToUrlHashModule = " (.stringify js/JSON (clj->js preToUrlHashModule))))
    (set-document-location-hash!
      (str orgUrlHash (if (empty? orgUrlHash) "" "&") moduleUrlHash))))

(defn- remove-module-from-document-location-hash!
  "Removes a module from `document.location.hash`"
  [moduleCode]
  (let [orgUrlHash (get-document-location-hash)

        urlHashWithoutModule
        (clojure.string/replace
          orgUrlHash
          (re-pattern (str moduleCode "_[A-Z]{1,3}=[^&]+&?")) "")]
    (set-document-location-hash!
      (clojure.string/replace urlHashWithoutModule #"&$" ""))))

(defn- update-document-location-hash-with-changed-lesson-group!
  "Updates a module's lesson type in `document.location.hash` with a changed
   changed lesson group."
  [moduleCode lessonTypeLongForm newLessonGroup]
  (let [orgUrlHash (get-document-location-hash)
        lessonType (Lesson-Type-Long-To-Short-Form lessonTypeLongForm)
        newUrlHash (clojure.string/replace
                     orgUrlHash
                     (re-pattern (str moduleCode "_" lessonType "=[^&]+"))
                     (str moduleCode "_" lessonType "=" newLessonGroup))]
    (set-document-location-hash! newUrlHash)))

(def ^{:doc "Vector of <tBody> objects representing the days of the timetable
             in the Timetable Builder page"
       :private true
      }
  HTML-Timetable (vec ($ :.day-container)))

(defn- get-css-hour-minute-classes-for-time
  "Returns the .hXX and .mXX classes for a 0-indexed time"
  [timeIdx]
  (let [time50 (time-helper/convert-time-index-to-mult-of-50-int timeIdx)
        hour   (quot time50 100)
        minute (rem time50 100)]
    [(str "h" (if (< hour 10) "0" "") hour)
     (str "m" (if (not= minute 0) "30" "00"))]))

(defn- $html-timetable-get-td-from-day-row-startTimeIdx
  "Given a day, row and start time index of a lesson, retrieves the
   jQuery object for the <td> element in the HTML representation of the
   Timetable."
  [day rowNum timeIdx]
  (let [$day (nth HTML-Timetable day)
        $row (nth (children $day "tr") rowNum)

        [hourClass minuteClass]
        (get-css-hour-minute-classes-for-time timeIdx)]
    (nth (children $row (str "td" "." hourClass "." minuteClass)) 0)))

(defn- create-empty-timetable-row
  "Creates an in-memory representation of an empty timetable row.

   Each row corresponds to a <tr> in the timetable. Each row is a map
   with each key is a `TimetableLessonInfo` object (see the top of this file for
   the definition) and each value is the jQuery object of the lesson <div>."
  []
  {})

(defn- create-day-repr
  "Creates in-memory representation of a single day in the timetable.

   Each day is a vector of rows, and there is a minimum of 2 rows in each day.
   Multiple rows are needed when there are lessons with overlapping start and
   end time."
  []
  [(create-empty-timetable-row) (create-empty-timetable-row)])

(defn timetable-create!
  "Initializes the in-memory representation of the timetable.

   The timetable is a vector of 5 rows, with each row representing a day from
   Monday to Friday."
  []
  (set! Timetable (vec (map (fn [x] (create-day-repr)) (range 0 5)))))

(defn- get-timetable-day
  "Retrieves the in-memory representation of the given 0-indexed day in the
   Timetable, where 0 = Monday, 1 = Tuesday, until 4 = Friday."
  [day]
  (nth Timetable day))

(defn- get-nr-rows-in-timetable-day
  "Returns the total number of rows in the given 0-indexed day in the Timetable,
   where 0 = Monday, 1 = Tuesday, until 4 = Friday."
  [day]
  (count (get-timetable-day day)))

(def ^{:doc     "HTML string for the <td> elements in a <tr> on the Timetable
                 Builder page"
       :private true}
  Timetable-Row-TD-HTML-String
  "<td class='h08 m00'></td>
   <td class='h08 m30'></td>
   <td class='h09 m00'></td>
   <td class='h09 m30'></td>
   <td class='h10 m00'></td>
   <td class='h10 m30'></td>
   <td class='h11 m00'></td>
   <td class='h11 m30'></td>
   <td class='h12 m00'></td>
   <td class='h12 m30'></td>
   <td class='h13 m00'></td>
   <td class='h13 m30'></td>
   <td class='h14 m00'></td>
   <td class='h14 m30'></td>
   <td class='h15 m00'></td>
   <td class='h15 m30'></td>
   <td class='h16 m00'></td>
   <td class='h16 m30'></td>
   <td class='h17 m00'></td>
   <td class='h17 m30'></td>
   <td class='h18 m00'></td>
   <td class='h18 m30'></td>
   <td class='h19 m00'></td>
   <td class='h19 m30'></td>
   <td class='h20 m00'></td>
   <td class='h20 m30'></td>
   <td class='h21 m00'></td>
   <td class='h21 m30'></td>
   <td class='h22 m00'></td>
   <td class='h22 m30'></td>
   <td class='h23 m00'></td>
   <td class='h23 m30'></td>")

(defn- add-new-row-to-timetable-day!
  "Adds a new row to the 0-indexed day in the Timetable, where 0 = Monday,
   1 = Tuesday, until 4 = Friday."
  [day]
  (set! Timetable
        (update-in Timetable [day]
                   (fn [ttDay]
                     (conj ttDay (create-empty-timetable-row)))))
  (.append (nth HTML-Timetable day)
           ($ (str "<tr class='day-row'>" Timetable-Row-TD-HTML-String "</tr>")))
  ; Modify the rowspan of the <th> in the 0th row in the timetable
  (let [nrRows (get-nr-rows-in-timetable-day day)
        thElem (.find (nth HTML-Timetable day) "tr > th")]
    (attr thElem "rowspan" nrRows)))

(defn- timetable-add-lesson!
  "Adds a lesson to the `Timetable` global, effectively 'marking' the
   time interval for that lesson as being occupied."
  [day rowNum ttLessonInfo $lessonDiv]
  (set! Timetable
        (update-in Timetable
                   [day rowNum]
                   (fn [ttRow]
                     (assoc ttRow ttLessonInfo $lessonDiv)))))

(defn- timetable-remove-lesson!
  "Removes a lesson from the `Timetable` global, effectively 'marking' the
   time interval occupied by that lesson as free."
  [day rowNum ttLessonInfo]
  (set! Timetable
        (update-in Timetable [day rowNum]
                   (fn [ttRow] (dissoc ttRow ttLessonInfo)))))

(defn- find-free-row-for-lesson
  "Returns the 0-indexed row which can accomodate the given lesson (timeslots
   for the lesson are not occupied). If no existing row can accomodate the
   lesson, then the total number of existing rows is returned to indicate that
   a new row must be created.

   The `moduleMapLesson` parameter should be a lesson from the `ModulesMap`
   global"
  [moduleMapLesson]
  (let [ttDay     (get-timetable-day (:day moduleMapLesson))
        startTime (:startTime moduleMapLesson)
        endTime   (:endTime moduleMapLesson)]
    (:rowIndex
      (reduce
        (fn [result ttRow]
          (cond
            (:foundFreeRow result) result
            (zero? (count ttRow))  (assoc result :foundFreeRow true)

            (empty?
              ; Find any existing lessons which overlap with our new lesson.
              ; The row is free if there are no existing overlapping lessons.
              ; 4 types of overlap:
              ;
              ; ONE:
              ;        |-- exist --|
              ; |-- new --|
              ;
              ; TWO:
              ; |-- exist --|
              ;       |-- new --|
              ;
              ; THREE:
              ;     |-- exist --|
              ; |------- new -------|
              ;
              ; FOUR:
              ; |------- exist -------|
              ;      |-- new --|
              (filter (fn [[ttLessonInfo _]]
                        (and (>= (dec endTime) (:startTime ttLessonInfo))
                             (< startTime (:endTime ttLessonInfo))))
                      ttRow))
            (assoc result :foundFreeRow true)

            :else (update-in result [:rowIndex] inc)))
        {:foundFreeRow false, :rowIndex 0}
        ttDay))))

(defn- create-lesson-div
  "Creates a <div> element for a new lesson using jQuery"
  [& {:keys [moduleCode moduleName lessonType lessonGroup venue slotsOcc
             bgColorCssClass isActuallySelected?]}]
  (let [$divElem  ($ "<div />" (js-obj "class" "lesson"))]
    (.addClass $divElem bgColorCssClass)
    ; add background color css class
    (.append $divElem (text ($ "<p />") (str moduleCode " " moduleName)))
    (.append $divElem (text ($ "<p />") (str lessonType " [" lessonGroup "]")))
    (.append $divElem (text ($ "<p />") venue))
    (width $divElem (str (* half-hour-pixels slotsOcc) "px"))
    ; make the <div> less opaque for a lesson added by jQuery UI draggable
    (if (not isActuallySelected?)
        (.addClass $divElem "lesson-droppable-not-hover"))
    $divElem))

(defn- add-module-lesson!
  "Adds a single lesson of a module the timetable.
   Returns a `ModulesSelectedLessonInfo` object augmented with the `:divElem`,
   `:moduleCode`, `:lessonType` and `:lessonGroup` keys."
  [moduleCode moduleName lessonType lessonLabel modulesMapLesson bgColorCssClass
   isActuallySelected?]
  (let [rowNum    (find-free-row-for-lesson modulesMapLesson)
        day       (:day modulesMapLesson)
        startTime (:startTime modulesMapLesson)
        endTime   (:endTime modulesMapLesson)
        slotsOcc  (- endTime startTime)
        venue     (:venue modulesMapLesson)

        [hourClass minuteClass]
        (get-css-hour-minute-classes-for-time startTime)

        $divElem
        (create-lesson-div :moduleCode moduleCode, :moduleName moduleName,
                           :lessonType lessonType, :lessonGroup lessonLabel,
                           :venue venue, :slotsOcc slotsOcc,
                           :bgColorCssClass bgColorCssClass,
                           :isActuallySelected? isActuallySelected?)]
    ; Create new row if necessary
    (if (= rowNum (get-nr-rows-in-timetable-day day))
        (add-new-row-to-timetable-day! day))

    ; Update in-memory representation of timetable
    (timetable-add-lesson! day
                           rowNum
                           {:moduleCode moduleCode,
                            :lessonType lessonType,
                            :lessonGroup lessonLabel,
                            :startTime startTime,
                            :endTime endTime}
                           $divElem)

    (let [$td ($html-timetable-get-td-from-day-row-startTimeIdx
                day rowNum startTime)]
      (.append $td $divElem)
      ; Increase colspan and delete the <td> after it that occupy the timeslot
      ; of the lesson
      (attr $td "colspan" slotsOcc)
      (doseq [$siblingTd (take (- slotsOcc 1) (.nextAll $td))]
        (.remove $siblingTd))

      {:day day, :rowNum rowNum, :startTime startTime, :endTime endTime,
       :moduleCode moduleCode, :lessonType lessonType, :lessonGroup lessonLabel,
       :divElem $divElem})))

(def ^{:doc     "Sequence of `TimetableLessonInfo` objects augmented with the
                 `:divElem`, `:moduleCode`, `:lessonType`, `:lessonGroup` keys.
                 These are 'fake' lessons created when a selected lesson is
                 being dragged by the user, in order for him/her to switch to
                 a different lesson group for that lesson type."
       :private true}
  Lessons-Created-By-Draggable nil)

; Forward declaration
(declare add-module-lesson-group!)
(declare timetable-prune-empty-rows)
(declare add-missing-td-elements-replacing-lesson)
(declare remove-lesson-group-html)
(declare shift-lessons-upwards-to-replace-empty-slots!)
(declare update-ModulesSelected-for-affected-days)

(def ^{:doc     "Key for a data attribute added to a draggable <div> helper
                 when the helper has been dropped onto a droppable <div>.
                 This is used to detect if a lesson group switch has occurred."
       :private true}
  LESSON-GROUP-CHANGE-KEY "lessonGroupChangeTo")

(defn- lesson-draggable-start-evt-handler-maker
  "Returns a function that can be used as the `start` event handler for a
   draggable lesson.

   Details of the returned function can be found here:

       http://api.jqueryui.com/draggable/#event-start"
  [moduleCode lessonType selectedLessonGroup bgColorCssClass]
  (fn [evt ui]
    (.addClass (aget ui "helper") "lesson-draggable-helper")
    (.css (aget ui "helper") "cursor" "grabbing")
    (let [allLessonGroups
          (get-all-lesson-group-strings moduleCode lessonType)

          unselectedLessonGroups
          (remove #{selectedLessonGroup} allLessonGroups)

          ; Add all the <div> elements
          augmentedTTLessonInfoSeq
          (doall
            (flatten
              (map (fn [unselectedLessonGroup]
                     (add-module-lesson-group! moduleCode lessonType
                                               unselectedLessonGroup
                                               bgColorCssClass false))
                   unselectedLessonGroups)))]
      (set! Lessons-Created-By-Draggable augmentedTTLessonInfoSeq))))

(defn- lesson-draggable-stop-evt-handler-maker
  "Returns a function used as the `stop` event handler for a draggable lesson."
  [moduleCode lessonType lessonGroup bgColorCssClass]
  (fn [evt ui]
    (let [$helper         (aget ui "helper")
          destLessonGroup (data $helper LESSON-GROUP-CHANGE-KEY)

          affectedDaysSet
          (reduce (fn [daySet ttLessonInfo]
                    (conj daySet (:day ttLessonInfo)))
                  #{}
                  Lessons-Created-By-Draggable)]

      (doseq [augTTLessonInfo Lessons-Created-By-Draggable]
        (timetable-remove-lesson! (:day augTTLessonInfo)
                                  (:rowNum augTTLessonInfo)
                                  (dissoc augTTLessonInfo :divElem :day
                                          :rowNum)))

      (doseq [augTTLessonInfo Lessons-Created-By-Draggable]
        (let [$divElem  (:divElem augTTLessonInfo)
              $parentTd (parent $divElem)]
          ; TODO: This part of the code is the same as in the
          ;       `remove-lesson-group-html` function. Refactor it.
          (remove-attr $parentTd "colspan")
          (.remove $divElem)
          (add-missing-td-elements-replacing-lesson $parentTd augTTLessonInfo)))

      (timetable-prune-empty-rows affectedDaysSet)
      (set! Lessons-Created-By-Draggable nil)
      (.css $helper "cursor" "grab")

      ; User has dropped the draggable helper <div> onto a droppable
      (if (not (nil? destLessonGroup))
          (let [augTTLessonInfoSeq (remove-lesson-group-html moduleCode
                                                             lessonType)
                affectedDaysSet    (reduce (fn [daySet augTTLessonInfo]
                                             (conj daySet
                                                   (:day augTTLessonInfo)))
                                           #{}
                                           augTTLessonInfoSeq)]
            ; remove the current lesson group
            (doseq [augTTLessonInfo augTTLessonInfoSeq]
              (let [day          (:day augTTLessonInfo)
                    rowNum       (:rowNum augTTLessonInfo)
                    ttLessonInfo (dissoc augTTLessonInfo :day :rowNum)]
                (timetable-remove-lesson! day rowNum ttLessonInfo)))

            (shift-lessons-upwards-to-replace-empty-slots! augTTLessonInfoSeq)
            (timetable-prune-empty-rows affectedDaysSet)
            (update-ModulesSelected-for-affected-days affectedDaysSet)

            ; add the newly selected lesson group
            (add-module-lesson-group! moduleCode lessonType destLessonGroup
                                      bgColorCssClass true)

            (update-document-location-hash-with-changed-lesson-group!
              moduleCode lessonType destLessonGroup))))))

(defn- make-added-lessons-draggable
  "Makes the <div> elements of selected lessons draggable."
  [$divElemSeq moduleCode lessonType lessonLabel bgColorCssClass]
  ; And Drag event handler
  (doseq [$divElem $divElemSeq]
    (.css $divElem "cursor" "grab")
    (.draggable $divElem
                (js-obj "zIndex" 100
                        "helper" "clone"
                        "revert" "invalid"
                        "start"  (lesson-draggable-start-evt-handler-maker
                                   moduleCode lessonType lessonLabel
                                   bgColorCssClass)
                        "stop"   (lesson-draggable-stop-evt-handler-maker
                                   moduleCode lessonType lessonLabel
                                   bgColorCssClass)))))

(defn- make-fake-lessons-droppable
  "For 'fake' lessons created due to a lesson <div> being dragged, we make
   them droppable in order for the user to switch lesson groups."
  [augModuleSelectedLessonInfoSeq]
  (doseq [augModuleSelectedLessonInfo augModuleSelectedLessonInfoSeq]
    (let [$divElem (:divElem augModuleSelectedLessonInfo)]
      (.droppable
        $divElem
        (js-obj "tolerance" "intersect"

                "over"
                (fn [evt ui]
                  (.removeClass $divElem "lesson-droppable-not-hover")
                  (.addClass $divElem "lesson-droppable-hover"))

                "out"
                (fn [evt ui]
                  (.removeClass $divElem "lesson-droppable-hover")
                  (.addClass $divElem "lesson-droppable-not-hover"))

                "drop"
                (fn [evt ui]
                  (let [$helper (aget ui "helper")]
                    ; Set a data attribute on the draggable helper to signal
                    ; that the user has dropped the <div> on a droppable to
                    ; change the lesson group
                    (data $helper
                          LESSON-GROUP-CHANGE-KEY
                          (:lessonGroup augModuleSelectedLessonInfo)))))))))

(defn- add-module-lesson-group!
  "Adds a lesson group of a module to the timetable.

   The `isActuallySelected?` param is used to indicate if the current lesson
   group is selected by the user.
   A value of `true` indicates that the user actually selected the lesson group.
   A value of `false` indicates that the current lesson group is added by the
   `lesson-draggable-start-evt-handler-maker` function and is not selected by
   the user.

   Returns a sequence of `ModulesSelectedLessonInfo` objects each augmented
   with the `:divElem`, `:moduleCode`, `:lessonType`, `:lessonGroup` keys."
  [moduleCode lessonType lessonLabel bgColorCssClass isActuallySelected?]
  (if (module-has-lesson-group? moduleCode lessonType lessonLabel)
      (let [moduleName          (get-module-name-from-module-code moduleCode)
            modulesMapLessonSeq (get-ModulesMapLesson-seq moduleCode lessonType
                                                          lessonLabel)

            augLessonInfoSeq
            (doall (map (fn [modulesMapLesson]
                          (add-module-lesson! moduleCode moduleName
                                                         lessonType
                                                         lessonLabel
                                                         modulesMapLesson
                                                         bgColorCssClass
                                                         isActuallySelected?))
                        modulesMapLessonSeq))

            lessonInfoSeq       (map #(dissoc %1 :divElem :moduleCode
                                              :lessonType :lessonGroup)
                                     augLessonInfoSeq)
            $divElemSeq         (map #(:divElem %1) augLessonInfoSeq)]
        (if isActuallySelected?
            (do
              ; Update ModulesSelected with the lesson group
              (set! ModulesSelected
                    (assoc-in ModulesSelected [moduleCode lessonType]
                              {:label lessonLabel, :info lessonInfoSeq}))

              ; Add to ModulesSelectedOrder
              (if (not-any? #{moduleCode} ModulesSelectedOrder)
                  (do
                    (set! ModulesSelectedOrder
                          (conj ModulesSelectedOrder moduleCode))
                    ; Update select2 box.
                    ; NOTE: This does a quadratic amount of work but I do not
                    ;       have a workaround.
                    (select2/select2-box-set-val
                      select2/$Select2-Box
                      (get-selected-module-codes-as-js-array))))

              ; Only lesson types with more than 1 option of lesson group
              ; will be draggable
              (if (module-lesson-type-has-multiple-choices? moduleCode
                                                            lessonType)
                  (make-added-lessons-draggable $divElemSeq moduleCode
                                                lessonType lessonLabel
                                                bgColorCssClass)))

            ; lesson was added due to draggable <div>
            ; Make it droppable
            (make-fake-lessons-droppable augLessonInfoSeq))
        augLessonInfoSeq)))

(defn add-module!
  "Adds a module to the timetable.

   A random lesson group of each type of lesson (Lecture, Tutorial, etc) will
   be added."
  [moduleCode]
  (if (not (contains? ModulesSelected moduleCode))
      (let [; TODO: Refactor
            ModulesMap      (get-ModulesMap)
            module          (get ModulesMap moduleCode)
            lessonsMap      (get module "lessons")
            bgColorCssClass (get-next-lesson-bg-color-css-class)

            newModInfoSeq
            (reduce (fn [moduleInfoSeq [lessonType lessonGroupsMap]]
                      (conj moduleInfoSeq
                            {:moduleCode  moduleCode
                             :lessonType  lessonType
                             :lessonGroup (first (keys lessonGroupsMap))}))
                    []
                    lessonsMap)]

        (doseq [moduleInfo newModInfoSeq]
          (add-module-lesson-group! (:moduleCode moduleInfo)
                                    (:lessonType moduleInfo)
                                    (:lessonGroup moduleInfo)
                                    bgColorCssClass
                                    true))

        ; Update URL hash with newly added module
        (update-document-location-hash-with-new-module!
          {:moduleCode moduleCode,

           :preToUrlHashLessonGroupSeqSorted
           (sort-PreToUrlHashLessonGroup-seq
             (map #(dissoc
                     (assoc %1 :lessonType
                            (Lesson-Type-Long-To-Short-Form (:lessonType %1)))
                     :moduleCode)
                  newModInfoSeq))}))))

(defn- get-module-info-from-url-hash-module-info
  "Computes the final module info sequence for modules added via the url hash
   on page initialization.

   NOTE: This function should only be called by
         `add-module-lesson-groups-from-url-hash!`"
  [moduleInfoSeq]
  (let [moduleLessonGroupsMap
        (reduce (fn [lgMap modInfo]
                  ; Replaces earlier lesson groups with later ones if we
                  ; encounter a duplicate lesson type
                  (assoc-in lgMap [(:moduleCode modInfo) (:lessonType modInfo)]
                            (:lessonGroup modInfo)))
                {}
                moduleInfoSeq)

        moduleCodesSeq (distinct (map #(:moduleCode %1) moduleInfoSeq))

        ; Map of Module Code -> Sequence of lesson type strings
        moduleLessonTypes
        (reduce (fn [lessonTypesMap moduleCode]
                  (let [ltMap (get moduleLessonGroupsMap moduleCode)]
                    (assoc lessonTypesMap moduleCode (keys ltMap))))
                {}
                moduleCodesSeq)

        ; Similar to `moduleLessonTypes`, but contains the full sequence of
        ; lesson type strings
        moduleLessonTypesFull
        (reduce (fn [lessonTypesMap moduleCode]
                  (assoc lessonTypesMap moduleCode
                         (get-all-lesson-types-for-module moduleCode)))
                {}
                moduleCodesSeq)

        ; Obtained from the difference between `moduleLessonTypesFull` and
        ; `moduleLessonTypes`
        missingModuleLessonTypes
        (reduce (fn [missingLessonTypesMap moduleCode]
                  (let [missingLessonTypes
                        (clojure.set/difference
                          (set (get moduleLessonTypesFull moduleCode))
                          (set (get moduleLessonTypes moduleCode)))]
                    (if (empty? missingLessonTypes)
                        missingLessonTypesMap
                        (assoc missingLessonTypesMap moduleCode
                               (seq missingLessonTypes)))))
                {}
                moduleCodesSeq)

        ; similar to `moduleLessonGroupsMap`, but includes missing lesson types
        moduleLessonGroupsMapFinal
        (reduce (fn [lgMapFinal moduleCode]
                  (let [missingLessonTypes
                        (get missingModuleLessonTypes moduleCode)]
                    (reduce
                      (fn [lgMap lessonType]
                        (assoc-in lgMap [moduleCode lessonType]
                                  (get-first-lesson-group-for-module-lesson-type
                                    moduleCode lessonType)))
                      lgMapFinal
                      missingLessonTypes)))
                moduleLessonGroupsMap
                (keys missingModuleLessonTypes))]

    ; Produce the final module info sequence
    (flatten
      (map (fn [moduleCode]
             (let [lessonTypesMap
                   (get moduleLessonGroupsMapFinal moduleCode)]
               (map (fn [lessonType]
                      (let [lessonGroup (get lessonTypesMap lessonType)]
                        {:moduleCode  moduleCode
                         :lessonType  lessonType
                         :lessonGroup lessonGroup}))
                    (keys lessonTypesMap))))
           moduleCodesSeq))))

(defn add-module-lesson-groups-from-url-hash!
  "Adds the module lesson groups available in the url hash. Erroneous lesson
   groups are ignored. If there are missing lesson groups for any module after
   going through the url hash, a random lesson group is chosen.

   NOTE: This function should only be called once."
  [urlHash]
  (let [ModulesMap         (get-ModulesMap)
        moduleUrlHashArray (.split urlHash "&")

        modUrlHashRegex
        #"^([A-Z]+\d{4}[A-Z]*)_(DL|L|LAB|PL|PT|R|SEM|ST|T|T2|T3)=([A-Z0-9]+)$"

        get-module-code-from-match-array
        (fn [matchArray] (nth matchArray 1))

        get-lesson-type-from-match-array
        (fn [matchArray] (nth matchArray 2))

        get-lesson-group-from-match-array
        (fn [matchArray] (nth matchArray 3))

        ; Sequence of non-nil match arrays
        matchArraySeq
        (filter (fn [matchArray] (not (nil? matchArray)))
          (map (fn [modUrlHash] (.exec modUrlHashRegex modUrlHash))
               moduleUrlHashArray))

        ; Sequence of existing lesson groups
        moduleInfoExistent
        (filter
          #(module-has-lesson-group? (:moduleCode %1) (:lessonType %1)
                                     (:lessonGroup %1))
          ; convert matchArrays to maps
          (map (fn [matchArray]
                 {:moduleCode  (get-module-code-from-match-array
                                 matchArray)
                  :lessonType  (Lesson-Type-Short-To-Long-Form
                                 (get-lesson-type-from-match-array matchArray))
                  :lessonGroup (get-lesson-group-from-match-array
                                 matchArray)})
               matchArraySeq))

        ; Module code to CSS class for lesson div background color
        moduleToColorsMap
        (reduce
          (fn [m2cMap moduleCode]
            (assoc m2cMap moduleCode (get-next-lesson-bg-color-css-class)))
          {}
          (distinct (map (fn [modInfo] (:moduleCode modInfo))
                         moduleInfoExistent)))

        moduleInfoFinal
        (get-module-info-from-url-hash-module-info moduleInfoExistent)]

    (.log js/console (str "moduleInfoExistent = " (.stringify js/JSON (clj->js moduleInfoExistent))))
    (.log js/console (str "moduleInfoFinal = " (.stringify js/JSON (clj->js moduleInfoFinal))))
    ; Add the module lesson groups
    (doseq [modInfo moduleInfoFinal]
      (let [moduleCode      (:moduleCode modInfo)
            bgColorCssClass (get moduleToColorsMap moduleCode)]
        (add-module-lesson-group! moduleCode
                                  (:lessonType modInfo)
                                  (:lessonGroup modInfo)
                                  bgColorCssClass
                                  true)))

    ; Update url hash
    (set-document-location-hash-based-on-modules-order!)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; Functions for Removing Modules
;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- add-missing-td-elements-replacing-lesson
  "Adds <td> elements that were removed by the `add-module-lesson!` function
   to make way for the lesson."
  [$parentTd ttLessonInfo]
  (let [startTime (:startTime ttLessonInfo)
        endTime   (:endTime ttLessonInfo)]
    (loop [$currentTd $parentTd
           timeIdx    (inc startTime)]
      (if (>= timeIdx endTime)
          nil
          (let [[hourClass minuteClass]
                (get-css-hour-minute-classes-for-time timeIdx)

                $newTdElem
                ($ "<td />" (js-obj "class" (str hourClass " " minuteClass)))]
            ; Insert after new <td> after the current <td>
            (insert-after $newTdElem $currentTd)
            ; Then use the new <td> as the current <td> in the next iteration
            (recur $newTdElem (inc timeIdx)))))))

(defn- remove-lesson-group-html
  "Removes a lesson group for a module from the HTML timetable.
   This does NOT remove any empty rows / perform module shifting.

   Returns a sequence of `ttLessonInfo` objects which contain
   additional `:day` and `:rowNum` keys."
  [moduleCode lessonType]
  (let [lessonGroupDetails (get-in ModulesSelected [moduleCode lessonType])
        lessonGroup        (:label lessonGroupDetails)
        lessonInfoSeq      (:info lessonGroupDetails)]
    (map (fn [modSelLessonInfo]
           (let [day          (:day modSelLessonInfo)
                 rowNum       (:rowNum modSelLessonInfo)
                 startTime    (:startTime modSelLessonInfo)
                 endTime      (:endTime modSelLessonInfo)
                 ttRow        (get-in Timetable [day rowNum])
                 ttLessonInfo {:moduleCode moduleCode, :lessonType lessonType,
                               :lessonGroup lessonGroup, :startTime startTime,
                               :endTime endTime}
                 $lessonDiv   (get ttRow ttLessonInfo)
                 $parentTd    (parent $lessonDiv)]
             ; Remove the `colspan` attribute of the parent <td> so it has
             ; colspan=1
             (remove-attr $parentTd "colspan")
             ; Remove the HTML div elem
             (.remove $lessonDiv)
             ; Add the missing <td> elements
             (add-missing-td-elements-replacing-lesson $parentTd ttLessonInfo)
             (assoc ttLessonInfo :day day, :rowNum rowNum)))
         lessonInfoSeq)))

(defn- get-largest-consecutive-time-slot-freed-up-by-lesson
  "Given a removed lesson, find the largest interval on the same row which is
   freed up due to the its removal.

   For instance, given this timetable row:

   x | x | x |  |  |  | y | y | y | y |  |  | z | z

   And we remove the lesson occupying the slots labelled 'y'. The 4 slots
   currently occupied by 'y', along with the 3 empty slots to its left and the
   2 empty slots to its right will be freed up.

   Given that the first slot occupied by the label 'x' is the index 0, this
   function will return the vector [3 11].

   The parameter `removedAugTTLessonInfo` is a `TimetableLessonInfo` object
   augmented with the `:day` and `:rowNum` keys."
  [removedAugTTLessonInfo]
  (let [day        (:day removedAugTTLessonInfo)
        row        (:rowNum removedAugTTLessonInfo)
        startTime  (:startTime removedAugTTLessonInfo)
        endTime    (:endTime removedAugTTLessonInfo)
        ttRow      (get-in Timetable [day row])
        minTimeIdx time-helper/TIME-INDEX-MIN
        maxTimeIdx time-helper/TIME-INDEX-MAX]

    (.log js/console "get-largest-consecutive-time-slot-freed-up-by-lesson")
    [
      ; Find the lesson right before the removed lesson
      (let [immediateTTLessonInfoBefore
            (reduce (fn [ttLessonInfoBefore [ttLessonInfo _]]
                      (let [stime (:startTime ttLessonInfo)
                            etime (:endTime ttLessonInfo)]
                        (cond (and (nil? ttLessonInfoBefore)
                                   (<= etime startTime))
                              ttLessonInfo

                              (and (not (nil? ttLessonInfoBefore))
                                   (<= etime startTime)
                                   (> etime (:endTime ttLessonInfoBefore)))
                              ttLessonInfo

                              :else ttLessonInfoBefore)))
                    nil
                    ttRow)]
        (if (nil? immediateTTLessonInfoBefore)
            minTimeIdx
            (:endTime immediateTTLessonInfoBefore)))

      ; Find the lesson right after the removed lesson
      (let [immediateTTLessonInfoAfter
            (reduce (fn [ttLessonInfoAfter [ttLessonInfo _]]
                      (let [stime (:startTime ttLessonInfo)
                            etime (:endTime ttLessonInfo)]
                        (cond (and (nil? ttLessonInfoAfter)
                                   (>= stime endTime))
                              ttLessonInfo

                              (and (not (nil? ttLessonInfoAfter))
                                   (>= stime endTime)
                                   (< stime (:endTime ttLessonInfoAfter)))
                              ttLessonInfo

                              :else ttLessonInfoAfter)))
                    nil
                    ttRow)]
        (if (nil? immediateTTLessonInfoAfter)
            maxTimeIdx
            (dec (:startTime immediateTTLessonInfoAfter))))
    ]))

(defn- find-replacement-lessons-within-time
  "For a removed lesson, find other lessons on the same day but in a row below
   the removed lesson which could be shifted upwards and take the timeslots
   freed up by the removed lesson.

   The `removedAugTTLessonInfo` parameter is an `TimetableLessonInfo` object
   augmented with the `:day` and `:rowNum` keys.

   Returns a map, where keys are `TimetableLessonInfo` objects augmented with
   `:day` and `:rowNum` keys, and values are jQuery <div> objects for the
   shifted lesson."
  [removedAugTTLessonInfo]
  (let [day        (:day removedAugTTLessonInfo)
        rowNum     (:rowNum removedAugTTLessonInfo)
        startTime  (:startTime removedAugTTLessonInfo)
        endTime    (:endTime removedAugTTLessonInfo)
        ModulesMap (get-ModulesMap)
        ttDay      (get-timetable-day day)

        ; This is the time range freed up by removal of the lesson
        [lowTimeIdx highTimeIdx]
        (get-largest-consecutive-time-slot-freed-up-by-lesson
          removedAugTTLessonInfo)

        not-occupied?
        (fn [occupiedVec startTime endTime]
          (.log js/console (str "inside not-occupied?, occupiedVec = " (.stringify js/JSON (clj->js occupiedVec))))
          (every? #(nil? %1)
                  (map #(nth occupiedVec %1)
                       (range startTime endTime))))]

    (.log js/console "find-replacement-lessons-within-time")
    (.log js/console (str "lowTimeIdx = " lowTimeIdx ", highTimeIdx = " highTimeIdx))
    (:augTTLessonInfoMapToShift
      (reduce
        (fn [reduceResult [rowIdx ttRow]]
          (if (empty? ttRow)
              ; Skip empty row
              reduceResult

              ; Non-empty row
              (let [occupiedVec (:occupiedVec reduceResult)

                    ttLessonInfoToShift
                    (filter (fn [[ttLessonInfo _]]
                              (let [stime (:startTime ttLessonInfo)
                                    etime (:endTime ttLessonInfo)]
                                (.log js/console (str "Executing, startTime = " stime ", endTime = " etime))
                                (and (>= stime lowTimeIdx)
                                     (<= (dec etime) highTimeIdx)
                                     (not-occupied? occupiedVec stime etime))))
                            ttRow)]
                (.log js/console "suspect above")
                (.log js/console (str "rowIdx = " rowIdx))
                (.log js/console (str "occupiedVec = " (.stringify js/JSON (clj->js occupiedVec))))
                {:augTTLessonInfoMapToShift
                 (reduce (fn [augTTLessonInfoMap [ttLessonInfo $divElem]]
                           (.log js/console ":augTTLessonInfoMapToShift")
                           (assoc augTTLessonInfoMap
                                  (assoc ttLessonInfo :day day :rowNum rowIdx)
                                  $divElem))
                         (:augTTLessonInfoMapToShift reduceResult)
                         ttLessonInfoToShift)

                 :occupiedVec
                 (reduce (fn [occVec [ttLessonInfo _]]
                           (.log js/console ":occupiedVec")
                           (reduce (fn [ov timeIdx] (assoc ov timeIdx true))
                                   occVec
                                   (range (:startTime ttLessonInfo)
                                          (:endTime ttLessonInfo))))
                         occupiedVec
                         ttLessonInfoToShift)})))
        {:augTTLessonInfoMapToShift {},
         ; nil = free slot, true = occupied slot
         :occupiedVec      (vec (map (fn [t] nil) (range 800 2400 50)))}
        ; go through each row in the day, below the current row
        (drop (inc rowNum) (map vector (range (count ttDay)) ttDay))))))

(defn- shift-lesson-to-row!
  "Shifts a lesson from its original row up to the given row.

   The parameter `augTTLessonInfo` is a `TimetableLessonInfo` object augmented
   with the `:day` and `:rowNum` keys."
  [destinationRowNum augTTLessonInfo $divElem]
  (let [day                     (:day augTTLessonInfo)
        sourceRowNum            (:rowNum augTTLessonInfo)
        startTime               (:startTime augTTLessonInfo)
        endTime                 (:endTime augTTLessonInfo)
        slotsOccupied           (- endTime startTime)

        $sourceTd
        ($html-timetable-get-td-from-day-row-startTimeIdx day sourceRowNum
                                                          startTime)

        $destTd
        ($html-timetable-get-td-from-day-row-startTimeIdx day destinationRowNum
                                                          startTime)

        ttLessonInfo            (dissoc augTTLessonInfo :day :rowNum)]
    (.log js/console (str "day = " day ", sourceRowNum = " sourceRowNum ", destinationRowNum = " destinationRowNum))
    ; Transfer lesson <div> to the destination <td>
    (.append $destTd $divElem)
    ; restore the colspan of the source <td>
    (remove-attr $sourceTd "colspan")
    ; fill in removed <td> for the source <td>
    (add-missing-td-elements-replacing-lesson $sourceTd augTTLessonInfo)
    ; remove sibling <td> for the destination <td> to make way for columns
    ; occupied by $divElem
    (doseq [$siblingTd (take (dec slotsOccupied) (.nextAll $destTd))]
      (.remove $siblingTd))
    ; update colspan of destination <td>
    (attr $destTd "colspan" slotsOccupied)
    ; update `Timetable` global
    (timetable-remove-lesson! day sourceRowNum ttLessonInfo)
    (timetable-add-lesson! day destinationRowNum ttLessonInfo $divElem)))

(defn- shift-lessons-upwards-to-replace-empty-slots!
  "For each removed lesson, see if there are any lessons in rows below it that
   can be shifted upwards to occupy the empty slots resulting from its removal.

   The parameter `removedAugmentedTTLessonInfoSeq` is a sequence of
   `TimetableLessonInfo` objects augmented with the `:day` and `:rowNum` keys."
  [removedAugmentedTTLessonInfoSeq]
  (let [; we do not consider any lessons on empty rows for ease of
        ; implementation
        exclude-lessons-on-empty-rows
        (fn [lessonInfoSeq]
          (filter #(let [day   (:day %1)
                         row   (:rowNum %1)
                         ttRow (get-in Timetable [day row])]
                     (not (empty? ttRow)))
                  lessonInfoSeq))

        sort-lesson-info-seq
        (fn [lessonInfoSeq]
          (sort #(let [dayA (:day %1)
                       dayB (:day %2)]
                   (if (= dayA dayB)
                       (< (:rowNum %1) (:rowNum %2))
                       (< dayA dayB)))
                lessonInfoSeq))]

    (.log js/console "Hoo hoo")
    (loop [sortedAugTTLessonInfoSeq
           (sort-lesson-info-seq (exclude-lessons-on-empty-rows
                                   removedAugmentedTTLessonInfoSeq))

           nextLessonInfoVec    []]
      (.log js/console "Inside the loop, the loop")
      (cond (and (empty? sortedAugTTLessonInfoSeq) (empty? nextLessonInfoVec))
            nil

            ; we're done for the current set of lessons. Move on to the
            ; set of lessons that were shifted upwards.
            (empty? sortedAugTTLessonInfoSeq)
            (recur (sort-lesson-info-seq (exclude-lessons-on-empty-rows
                                           nextLessonInfoVec))
                   [])

            ; find timeslot to shift up to replace gap
            :else
            (let [currentAugTTLessonInfo (first sortedAugTTLessonInfoSeq)
                  rowNum                 (:rowNum currentAugTTLessonInfo)

                  ; map of augmented `TimeTableLessonInfo` objects to the
                  ; jQuery <div> element
                  augTTLessonInfoTo$DivElem
                  (find-replacement-lessons-within-time currentAugTTLessonInfo)]

              (doseq [[augTTLessonInfo $divElem] augTTLessonInfoTo$DivElem]
                (.log js/console "shift-lesson-to-row")
                (shift-lesson-to-row! rowNum augTTLessonInfo $divElem))
              (.log js/console "gonna recur...")
              (recur (rest sortedAugTTLessonInfoSeq)
                     ; append newly shifted lessons to nextLessonInfoVec
                     (reduce (fn [augTTLessonInfoVec [augTTLessonInfo _]]
                               (conj augTTLessonInfoVec augTTLessonInfo))
                             nextLessonInfoVec
                             augTTLessonInfoTo$DivElem)))))))

(defn- timetable-prune-empty-rows
  "Removes empty rows resulting from the removal of lessons on given days."
  [affectedDaysSet]
  (doseq [day affectedDaysSet]
    (let [nrRows          (get-nr-rows-in-timetable-day day)
          ttDay           (get-timetable-day day)

          rowsPartition
          (reduce (fn [rPart [rowIdx ttRow]]
                    (if (empty? ttRow)
                      (update-in rPart [:emptyRows]
                                 (fn [emptyRowsVec]
                                   (conj emptyRowsVec rowIdx)))
                      (update-in rPart [:nonEmptyRows]
                                 (fn [nonEmptyRowsVec]
                                   (conj nonEmptyRowsVec rowIdx)))))
                  {:emptyRows [], :nonEmptyRows []}
                  (map vector (range (count ttDay)) ttDay))

          emptyRowsVec    (:emptyRows rowsPartition)
          nonEmptyRowsVec (:nonEmptyRows rowsPartition)
          nrEmptyRows     (count emptyRowsVec)
          nrNonEmptyRows  (- nrRows nrEmptyRows)
          $day            (nth HTML-Timetable day)
          $thElem         (.find $day "tr > th")]
      (.log js/console (str "day = " day ", emptyRowsVec = "
                            (.stringify js/JSON (clj->js emptyRowsVec))
                            ", nonEmptyRowsVec = "
                            (.stringify js/JSON (clj->js nonEmptyRowsVec))))
      (set! Timetable
            (update-in Timetable [day]
                       (fn [ttDay]
                         (vec (map (fn [rowIdx] (nth ttDay rowIdx))
                                   (cond (>= nrNonEmptyRows 2)
                                         nonEmptyRowsVec

                                         (= nrNonEmptyRows 1)
                                         [(first nonEmptyRowsVec)
                                          (first emptyRowsVec)]

                                         :else
                                         (take 2 emptyRowsVec)))))))
      ; shift <th> element to the new 0th row
      (if (and (zero? (first emptyRowsVec)) (> nrNonEmptyRows 0))
          (prepend (nth (children $day "tr") (first nonEmptyRowsVec)) $thElem))

      ; remove empty <tr> elements
      (let [rowsToRemove
            (cond (>= nrNonEmptyRows 2) emptyRowsVec
                  (=  nrNonEmptyRows 1) (rest emptyRowsVec)
                  :else                 (drop 2 emptyRowsVec))]
        (doseq [[idx rowIdx] (map vector (range (count rowsToRemove))
                                  rowsToRemove)]
          (.remove (nth (children $day "tr") (- rowIdx idx)))))

      (if (and (= nrNonEmptyRows 1)
               (zero? (first emptyRowsVec)))
          (let [$trArray (children $day "tr")]
            (.log js/console "Am gonna swap this shit man")
            (.log js/console (str "$trArray = " $trArray ", 0th = " (nth $trArray 0) ", 1st = " (nth $trArray 1)))
            (before (nth $trArray 0) (nth $trArray 1))))

      ; adjust rowspan of <th>
      (attr $thElem "rowspan" (max nrNonEmptyRows 2)))))

(defn- update-ModulesSelected-for-affected-days
  "Given a set of days that may possibly be affected by removal and shifting of
   lessons, update the individual entries in the `ModulesSelected` global."
  [affectedDaysSet]
  (doseq [day affectedDaysSet]
    (let [ttDay (get-timetable-day day)]
      (doseq [[rowIdx ttRow] (map vector (range (count ttDay)) ttDay)]
        (doseq [[ttLessonInfo _] ttRow]
          (let [moduleCode  (:moduleCode ttLessonInfo)
                lessonType  (:lessonType ttLessonInfo)
                lessonGroup (:lessonGroup ttLessonInfo)
                startTime   (:startTime ttLessonInfo)
                endTime     (:endTime ttLessonInfo)]
            (set! ModulesSelected
                  (update-in
                    ModulesSelected
                    [moduleCode lessonType :info]
                    (fn [modSelLessonInfoSeq]
                      (conj
                        (filter (fn [modSelLessonInfo]
                                  (or (not= (:day modSelLessonInfo) day)
                                      (not= (:startTime modSelLessonInfo)
                                            startTime)
                                      (not= (:endTime modSelLessonInfo)
                                            endTime)))
                                modSelLessonInfoSeq)
                        {:day day,
                         :rowNum rowIdx,
                         :startTime startTime,
                         :endTime endTime}))))))))))

(defn remove-module!
  "Removes a module from the timetable.

   This deletes every lesson of the module from the timetable."
  [moduleCode]
  (if (contains? ModulesSelected moduleCode)
      (let [lessonTypes
            (keys (get ModulesSelected moduleCode))

            augTTLessonInfoSeq
            (flatten (map (fn [lessonType]
                            (remove-lesson-group-html moduleCode lessonType))
                          lessonTypes))

            affectedDaysSet
            (reduce (fn [daySet augTTLessonInfo]
                      (conj daySet (:day augTTLessonInfo)))
                    #{}
                    augTTLessonInfoSeq)]

        ; Remove module from `ModulesSelected`
        (set! ModulesSelected (dissoc ModulesSelected moduleCode))

        ; remove the lesson <div>s
        (doseq [augTTLessonInfo augTTLessonInfoSeq]
          (let [day          (:day augTTLessonInfo)
                rowNum       (:rowNum augTTLessonInfo)
                ttLessonInfo (dissoc augTTLessonInfo :day :rowNum)]
          (timetable-remove-lesson! day rowNum ttLessonInfo)))

        (.log js/console "Reached here man")

        ; Perform shifting due to removal of lesson <div>s
        (shift-lessons-upwards-to-replace-empty-slots! augTTLessonInfoSeq)
        (.log js/console "Here too bro")
        (timetable-prune-empty-rows affectedDaysSet)
        (.log js/console "Whats up")

        ; Update `ModulesSelected`
        (update-ModulesSelected-for-affected-days affectedDaysSet)
        (.log js/console "Boo ya!")
        (.log js/console (str "ModulesSelected:" (.stringify js/JSON (clj->js ModulesSelected))))

        (remove-module-from-document-location-hash! moduleCode)

        ; Remove from `ModulesSelectedOrder`
        (set! ModulesSelectedOrder (remove #{moduleCode} ModulesSelectedOrder))
        (.log js/console "I hear and obey!")
        ; Update Exhibit3 box
        (select2/select2-box-set-val select2/$Select2-Box
                                     (get-selected-module-codes-as-js-array)))))

(defn remove-all-modules
  "Removes all modules from the timetable"
  []
  (.remove ($ ".lesson"))
  (doseq [day (range 5)]
    (let [nrRows   (get-nr-rows-in-timetable-day day)
          $dayElem (nth HTML-Timetable day)]
      ; Remove rows >= 2
      (doseq [rowIdx (reverse (range 2 nrRows))]
        (.remove (nth (children $dayElem "tr") rowIdx)))
      ; Remove all <td>
      (.remove (.find $dayElem "td"))
      ; Add clean <td>
      (doseq [rowIdx (range 2)]
        (.append (nth (children $dayElem "tr") rowIdx)
                 ($ Timetable-Row-TD-HTML-String)))
      (attr (.find $dayElem "tr > th") "rowspan" 2)))
  (timetable-create!)
  (set! ModulesSelected {})
  (set! ModulesSelectedOrder [])
  (select2/select2-box-set-val select2/$Select2-Box (array))
  (set-document-location-hash! ""))
