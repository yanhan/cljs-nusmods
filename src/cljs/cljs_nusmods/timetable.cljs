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
;                 1 = Tuesday, etc, until 5 = Saturday
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

(def ^{:doc     "Minimum number of rows for a day in the Timetable"
       :private true}
  TIMETABLE-MIN-ROWS-FOR-DAY 2)

(defn- get-ModulesMap
  "Retrieves the `ModulesMap` JavaScript global variable"
  []
  (aget js/window "ModulesMap"))

(defn- is-module-code?
  "Determines if a module code exists in our list of modules."
  [moduleCode]
  (let [modulesMap (get-ModulesMap)]
    (contains? modulesMap moduleCode)))

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

(defn- get-set-from-seq-of-maps-with-day-key
  "Returns a set of days from a sequence of maps, where each map has the `:day`
   key."
  [s]
  (reduce (fn [daySet m]
            (conj daySet (:day m)))
          #{}
          s))

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

(defn is-module-selected?
  "Determines if a module has been selected"
  [moduleCode]
  (contains? ModulesSelected moduleCode))

(defn get-nr-modules-selected
  "Return the number of modules selected by the user"
  []
  (count ModulesSelected))

(defn- update-ModulesSelected-with-lesson-group!
  [moduleCode lessonType lessonGroup lessonInfoSeq]
  (set! ModulesSelected
        (assoc-in ModulesSelected [moduleCode lessonType]
                  {:label lessonGroup, :info lessonInfoSeq})))

(declare timetable-enumerate-rows-in-day)

(defn- update-ModulesSelected-for-affected-days!
  "Given a set of days that may possibly be affected by removal and shifting of
   lessons, update the individual entries in the `ModulesSelected` global."
  [affectedDaysSet]
  (doseq [day affectedDaysSet]
    (doseq [[rowIdx ttRow] (timetable-enumerate-rows-in-day day)]
      (doseq [[{:keys [moduleCode lessonType lessonGroup
                       startTime endTime]} _] ttRow]
        (set! ModulesSelected
              (update-in
                ModulesSelected
                [moduleCode lessonType :info]
                (fn [modSelLessonInfoSeq]
                  (conj (filter (fn [modSelLessonInfo]
                                  (or (not= (:day modSelLessonInfo) day)
                                      (not= (:startTime modSelLessonInfo)
                                            startTime)
                                      (not= (:endTime modSelLessonInfo)
                                            endTime)))
                                modSelLessonInfoSeq)
                        {:day day,
                         :rowNum rowIdx,
                         :startTime startTime,
                         :endTime endTime}))))))))

(defn- remove-module-from-ModulesSelected!
  "Removes a module from `ModulesSelected`, and updates all lessons with new
   row indices for days where lesson removal has occurred and row shifting /
   removal has occurred."
  [moduleCode affectedDaysSet]
  (set! ModulesSelected (dissoc ModulesSelected moduleCode))
  (update-ModulesSelected-for-affected-days! affectedDaysSet))

(defn- reset-ModulesSelected!
  "Resets `ModulesSelected`."
  []
  (set! ModulesSelected {}))

(def ^{:doc     "Vector containing module code strings in the order the modules
                 were added."
       :private true}
  ModulesSelectedOrder [])

(defn get-selected-module-codes-as-js-array
  "Returns a JavaScript Array of Strings, where each String is the module code
   of a selected module"
  []
  (clj->js ModulesSelectedOrder))

(defn- module-not-in-ModulesSelectedOrder?
  "Determines if a module is in `ModulesSelectedOrder`"
  [moduleCode]
  (not-any? #{moduleCode} ModulesSelectedOrder))

(defn- add-to-ModulesSelectedOrder!
  "Appends a module to `ModulesSelectedOrder`"
  [moduleCode]
  (set! ModulesSelectedOrder (conj ModulesSelectedOrder moduleCode)))

(defn- remove-from-ModulesSelectedOrder!
  "Removes a module from `ModulesSelectedOrder`"
  [moduleCode]
  (set! ModulesSelectedOrder (remove #{moduleCode} ModulesSelectedOrder)))

(defn- reset-ModulesSelectedOrder!
  "Resets `ModuleSelectedOrder`"
  []
  (set! ModulesSelectedOrder []))

(defn- select2-box-update-modules!
  "Update the select2 box with the currently selected modules"
  []
  ; NOTE: Internally, this does a quadratic amount of work but I do not
  ;       have a workaround.
  (select2/select2-box-set-val select2/$Select2-Box
                               (get-selected-module-codes-as-js-array)))

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

(def ^{:doc     "Converts a long form lesson type to a nicer looking string"
       :private true}
  Lesson-Type-Long-To-Nice
  {"DESIGN LECTURE"             "Design Lecture",
   "LECTURE"                    "Lecture",
   "LABORATORY"                 "Laboratory",
   "PACKAGED LECTURE"           "Packaged Lecture",
   "PACKAGED TUTORIAL"          "Packaged Tutorial",
   "RECITATION"                 "Recitation",
   "SEMINAR-STYLE MODULE CLASS" "Seminar-Style Module Class",
   "SECTIONAL TEACHING"         "Sectional Teaching",
   "TUTORIAL"                   "Tutorial",
   "TUTORIAL TYPE 2"            "Tutorial Type 2",
   "TUTORIAL TYPE 3"            "Tutorial Type 3"})

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

(defn- document-location-hash-reset!
  "Resets `document.location.hash` to the empty string"
  []
  (set-document-location-hash! ""))

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

(defn- html-timetable-get-td
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

   Each day is a vector of rows, and there is a minimum of
   `TIMETABLE-MIN-ROWS-FOR-DAY` rows in each day.
   Multiple rows are needed when there are lessons with overlapping start and
   end time."
  []
  (loop [ttDay           []
         nrRowsRemaining TIMETABLE-MIN-ROWS-FOR-DAY]
    (if (zero? nrRowsRemaining)
        ttDay
        (recur (conj ttDay (create-empty-timetable-row))
               (dec nrRowsRemaining)))))

(defn timetable-create!
  "Initializes the in-memory representation of the timetable.

   The timetable is a vector of 6 rows, with each row representing a day from
   Monday to Saturday"
  []
  (set! Timetable (vec (map (fn [x] (create-day-repr))
                            (range 0 time-helper/NR-DAYS)))))

(defn- timetable-get-day
  "Retrieves the in-memory representation of the given 0-indexed day in the
   Timetable, where 0 = Monday, 1 = Tuesday, until 5 = Saturday"
  [day]
  (nth Timetable day))

; Retrieves a row for a given day in `Timetable`
(defmulti timetable-get-day-row (fn [a1 _] (number? a1)) :default true)

; First argument is a 0-indexed integer of the day
(defmethod timetable-get-day-row true
  [day rowNum]
  (nth (timetable-get-day day) rowNum))

; First argument is a day in `Timetable - the return value of
; `timetable-get-day` function
(defmethod timetable-get-day-row false
  [ttDay rowNum]
  (nth ttDay rowNum))

; Returns the total number of rows for a given day in the Timetable.
(defmulti timetable-day-get-nr-rows number? :default true)

; Argument argument is a a 0-indexed integer representing the day, where
; 0 = Monday, 1 = Tuesday, until 5 = Saturday
(defmethod timetable-day-get-nr-rows true
  [day]
  (count (timetable-get-day day)))

; Argument is a Timetable row returned by the `tiemtable-get-day-row` function
(defmethod timetable-day-get-nr-rows false
  [ttDay]
  (count ttDay))

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
   1 = Tuesday, until 5 = Saturday."
  [day]
  (set! Timetable
        (update-in Timetable [day]
                   (fn [ttDay]
                     (conj ttDay (create-empty-timetable-row)))))
  (.append (nth HTML-Timetable day)
           ($ (str "<tr class='day-row'>" Timetable-Row-TD-HTML-String "</tr>")))
  ; Modify the rowspan of the <th> in the 0th row in the timetable
  (let [nrRows (timetable-day-get-nr-rows day)
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

(defn- timetable-get-lesson-div
  "Retrieves the jQuery object for a lesson <div>"
  [day rowNum ttLessonInfo]
  (get-in Timetable [day rowNum ttLessonInfo]))

(defn- timetable-remove-lesson!
  "Removes a lesson from the `Timetable` global, effectively 'marking' the
   time interval occupied by that lesson as free."
  [day rowNum ttLessonInfo]
  (let [$lessonDiv (timetable-get-lesson-div day rowNum ttLessonInfo)]
    (set! Timetable
          (update-in Timetable [day rowNum]
                     (fn [ttRow] (dissoc ttRow ttLessonInfo))))
    $lessonDiv))

(defn- find-free-row-for-lesson
  "Returns the 0-indexed row which can accomodate the given lesson (timeslots
   for the lesson are not occupied). If no existing row can accomodate the
   lesson, then the total number of existing rows is returned to indicate that
   a new row must be created.

   The `moduleMapLesson` parameter should be a lesson from the `ModulesMap`
   global"
  [day startTime endTime]
  (let [ttDay (timetable-get-day day)]
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

(defn- timetable-get-closest-TimetableLessonInfo-before-time
  "Retrieves the `TimetableLessonInfo` which ends before a given time and is
   the one on the row closest to that time.
   If such a `TimetableLessonInfo` object does not exist, returns nil."
  [day rowNum timeIdx]
  (let [ttRow (get-in Timetable [day rowNum])]
    (reduce (fn [ttLessonInfoBefore [ttLessonInfo _]]
              (let [endTime   (:endTime ttLessonInfo)]
                (cond (and (nil? ttLessonInfoBefore)
                           (<= endTime timeIdx))
                      ttLessonInfo

                      (and (not (nil? ttLessonInfoBefore))
                           (<= endTime timeIdx)
                           (> endTime (:endTime ttLessonInfoBefore)))
                      ttLessonInfo

                      :else ttLessonInfoBefore)))
            nil
            ttRow)))

(defn- timetable-get-closest-TimetableLessonInfo-on-or-after-time
  "Retrieves the `TimetableLessonInfo` which begins on or after a given time and
   is the one on the row closest to that time.
   If such a `TimetableLessonInfo` object does not exist, returns nil."
  [day rowNum timeIdx]
  (let [ttRow (get-in Timetable [day rowNum])]
    (reduce (fn [ttLessonInfoAfter [ttLessonInfo _]]
              (let [startTime (:startTime ttLessonInfo)]
                (cond (and (nil? ttLessonInfoAfter)
                           (>= startTime timeIdx))
                      ttLessonInfo

                      (and (not (nil? ttLessonInfoAfter))
                           (>= startTime timeIdx)
                           (< startTime (:startTime ttLessonInfoAfter)))
                      ttLessonInfo

                      :else ttLessonInfoAfter)))
            nil
            ttRow)))

(defn- timetable-row-empty?
  "Returns true if there is no lesson for a given day and row in `Timetable`.
   Returns false otherwise."
  ([ttRow] (empty? ttRow))

  ([day rowNum] (empty? (timetable-get-day-row day rowNum))))

(defn- timetable-row-not-empty?
  "Returns true if there at least one lesson for a given day and row in
   `Timetable`. Returns false otherwise."
  ([ttRow] (not (timetable-row-empty? ttRow)))

  ([day rowNum] (not (timetable-row-empty? day rowNum))))

(defn- timetable-row-get-lessons-satisfying-pred
  "Returns a sequence of vectors of `TimetableLessonInfo` object and the
   jQuery <div> element associated with that lesson.
   The `TimetableLessonInfo` objects must satisfy a criteria given by the
   `predFn` predicate."
  [day rowNum predFn]
  (let [ttRow (timetable-get-day-row day rowNum)]
    (filter (fn [[ttLessonInfo _]]
              (predFn ttLessonInfo))
            ttRow)))

(defn- timetable-enumerate-rows-in-day
  "Returns a sequence of [rowIndex, timetableRow], where `rowIndex` is the
   0-indexed row in the day, and `timetableRow` is the corresponding row."
  [day]
  (let [ttDay (timetable-get-day day)]
    (map vector (range (timetable-day-get-nr-rows ttDay)) ttDay)))

(defn- timetable-day-get-empty-and-non-empty-rows
  "Returns a hash like this:
   {
     :emptyRows    -> vector of empty row indices for the day
     :nonEmptyRows -> vector of non empty row indices for the day
   }"
  [day]
  (reduce (fn [dayPartition [rowIdx ttRow]]
            (if (timetable-row-empty? ttRow)
                (update-in dayPartition [:emptyRows] #(conj %1 rowIdx))
                (update-in dayPartition [:nonEmptyRows] #(conj %1 rowIdx))))
          {:emptyRows [], :nonEmptyRows []}
          (timetable-enumerate-rows-in-day day)))

(defn- timetable-prune-empty-rows-for-day!
  "Removes empty rows from a day in `Timetable`, based on the return value of
   the `timetable-day-get-empty-and-non-empty-rows` function."
  [day {:keys [emptyRows nonEmptyRows]}]
  (let [nrNonEmptyRows (count nonEmptyRows)]
    (set! Timetable
          (update-in Timetable [day]
                     (fn [ttDay]
                       (vec (map (fn [rowIdx]
                                   (timetable-get-day-row ttDay rowIdx))
                                 (cond (>= nrNonEmptyRows
                                           TIMETABLE-MIN-ROWS-FOR-DAY)
                                       nonEmptyRows

                                       (= nrNonEmptyRows 1)
                                       [(first nonEmptyRows)
                                        (first emptyRows)]

                                       :else
                                       (take TIMETABLE-MIN-ROWS-FOR-DAY
                                             emptyRows)))))))))

(defn- timetable-remove-lesson-group!
  "Removes a selected lesson group for a module from the `Timetable`.

   Returns a sequence of `TimetableLessonInfo` objects, augmented with the
   `:day`, `:rowNum` and `:divElem` keys. The `divElem` key's value is the
   jQuery <div> object associated with the `TimetableLessonInfo`."
  [moduleCode lessonType]
  (let [lessonGroupDetails (get-in ModulesSelected [moduleCode lessonType])
        lessonGroup        (:label lessonGroupDetails)
        lessonInfoSeq      (:info lessonGroupDetails)]
    (doall
      (map (fn [{:keys [day rowNum startTime endTime]}]
             (let [ttLessonInfo {:moduleCode moduleCode,
                                 :lessonType lessonType,
                                 :lessonGroup lessonGroup,
                                 :startTime startTime,
                                 :endTime endTime}
                   $lessonDiv   (timetable-remove-lesson! day
                                                          rowNum
                                                          ttLessonInfo)]
               (assoc ttLessonInfo
                      :day day,
                      :rowNum rowNum,
                      :divElem $lessonDiv)))
           lessonInfoSeq))))

(defn- timetable-has-lessons-for-day?
  "Returns true if there is some lesson on a given day, false otherwise.
   NOTE: This includes any fake lessons created temporarily from the user
         trying to change the timeslot for a lesson."
  [day]
  (reduce (fn [hasLesson [rowIdx ttRow]]
            (cond hasLesson                        hasLesson
                  (timetable-row-not-empty? ttRow) true
                  :else                            hasLesson))
          false
          (timetable-enumerate-rows-in-day day)))

(defn- html-timetable-prune-empty-rows-for-day!
  "Removes <tr> elements corresponding to empty rows in `Timetable`.
   The 2nd argument should be the return value of the
   `timetable-day-get-empty-and-non-empty-rows` function."
  [day {:keys [emptyRows nonEmptyRows]}]
  (let [$day           (nth HTML-Timetable day)
        $thElem        (.find $day "tr > th")
        nrNonEmptyRows (count nonEmptyRows)

        rowIndicesToPreserve
        (if (>= nrNonEmptyRows TIMETABLE-MIN-ROWS-FOR-DAY)
            nonEmptyRows
            ; Preserve the initial empty rows to make up for the minimum
            ; number of non-empty rows; remove the remaining empty rows.
            (concat nonEmptyRows
                    (take (- TIMETABLE-MIN-ROWS-FOR-DAY nrNonEmptyRows)
                          emptyRows)))

        orgTotalRows   (+ nrNonEmptyRows (count emptyRows))
        newNrRows      (max nrNonEmptyRows TIMETABLE-MIN-ROWS-FOR-DAY)

        $rows           (children $day "tr")
        $rowsToPreserve (map (fn [rowIdx] ($ (nth $rows rowIdx)))
                             rowIndicesToPreserve)]

    ; shift <th> element to the new 0th row
    (if (and (zero? (first emptyRows)) (> nrNonEmptyRows 0))
        (prepend (nth (children $day "tr") (first nonEmptyRows)) $thElem))

    ; Shift the final <tr> to the beginning of the day
    (doseq [$row (reverse $rowsToPreserve)]
      (prepend $day $row))

    ; remove empty <tr> elements
    (doseq [rowIdx (reverse (range newNrRows orgTotalRows))]
      (.remove (nth (children $day "tr") rowIdx)))

    ; adjust rowspan of <th>
    (attr $thElem "rowspan" newNrRows)))

(defn- html-timetable-display-saturday-if-needed!
  "Displays Saturday for the HTML Timetable if there is some lesson on that
   day, otherwise hides Saturday."
  []
  (if (timetable-has-lessons-for-day? time-helper/SATURDAY)
      (show (nth HTML-Timetable time-helper/SATURDAY))
      (hide (nth HTML-Timetable time-helper/SATURDAY))))

(def ^{:doc     "`Modules.WeekText` of the JavaScript global; set this using
                   the `set-WEEK-TEXT-ARRAY!` function"
       :private true}
  WEEK-TEXT-ARRAY nil)

(defn set-WEEK-TEXT-ARRAY!
  "Sets `WEEK-TEXT-ARRAY` to the `Modules.WeekText` JavaScript global"
  [weekTextArray]
  (set! WEEK-TEXT-ARRAY weekTextArray))

(defn- week-text-index-to-string
  "Retrieves the string at the given index to `WEEK-TEXT-ARRAY`"
  [weekTextIdx]
  (nth WEEK-TEXT-ARRAY weekTextIdx))

(defn- create-lesson-div
  "Creates a <div> element for a new lesson using jQuery"
  [& {:keys [day moduleCode moduleName lessonType lessonGroup startTime endTime
             venue weekTextIdx slotsOcc bgColorCssClass isActuallySelected?]}]
  (let [$divElem  ($ "<div />" (js-obj "class" "lesson"))]
    (.addClass $divElem bgColorCssClass)
    ; add background color css class
    (.append $divElem (text ($ "<p />") (str moduleCode " " moduleName)))
    (.append $divElem (text ($ "<p />") (str lessonType " [" lessonGroup "]")))
    (.append $divElem (text ($ "<p />") venue))
    ; make the <div> less opaque for a lesson added by jQuery UI draggable
    (if (not isActuallySelected?)
        (.addClass $divElem "lesson-droppable-not-hover"))

    ; add qTip
    (let [$qtipContent ($ "<div />")

          niceStartTime
          (time-helper/convert-time-index-to-nice-time startTime)

          niceEndTime
          (time-helper/convert-time-index-to-nice-time endTime)]
      (.append $qtipContent (text ($ "<p />") moduleCode))
      (.append $qtipContent (text ($ "<p />") moduleName))
      (.append $qtipContent
               (text ($ "<p />")
                     (str (Lesson-Type-Long-To-Nice lessonType) " Group "
                          lessonGroup)))
      (.append $qtipContent
               (text ($ "<p />")
                     (str (nth time-helper/DAY_INTEGER_TO_STRING day)
                          " " niceStartTime " - " niceEndTime)))
      (.append $qtipContent (text ($ "<p />")
                                  (str (week-text-index-to-string weekTextIdx)
                                       " @ " venue)))
      (.qtip $divElem
             (js-obj "content"  (js-obj "text" $qtipContent)
                     "position" (js-obj "my" "center left"
                                        "at" "center right")

                     "style"
                     (js-obj "classes"
                             (clojure.string/join
                               " " ["qtip-bootstrap" "qtip-rounded"
                                    "qtip-shadow"])))))
    $divElem))

(defn- html-timetable-remove-all-lessons!
  "Removes all lesson <div>s from the HTML timetable."
  []
  (.remove ($ ".lesson")))

(defn- html-timetable-reset!
  "Resets the HTML Timetable"
  []
  (html-timetable-remove-all-lessons!)
  (doseq [day (range time-helper/NR-DAYS)]
    (let [nrRows   (timetable-day-get-nr-rows day)
          $dayElem (nth HTML-Timetable day)]
      ; Remove rows >= TIMETABLE-MIN-ROWS-FOR-DAY
      (doseq [rowIdx (reverse (range TIMETABLE-MIN-ROWS-FOR-DAY nrRows))]
        (.remove (nth (children $dayElem "tr") rowIdx)))
      ; Remove all <td>
      (.remove (.find $dayElem "td"))
      ; Add clean <td>
      (doseq [rowIdx (range TIMETABLE-MIN-ROWS-FOR-DAY)]
        (.append (nth (children $dayElem "tr") rowIdx)
                 ($ Timetable-Row-TD-HTML-String)))
      (attr (.find $dayElem "tr > th") "rowspan" TIMETABLE-MIN-ROWS-FOR-DAY))))

(defn- add-module-lesson!
  "Adds a single lesson of a module the timetable.
   Returns a `ModulesSelectedLessonInfo` object augmented with the `:divElem`,
   `:moduleCode`, `:lessonType` and `:lessonGroup` keys."
  [& {:keys [moduleCode moduleName lessonType lessonGroup
             day startTime endTime venue weekTextIdx
             bgColorCssClass isActuallySelected?]}]
  (let [rowNum    (find-free-row-for-lesson day startTime endTime)
        slotsOcc  (- endTime startTime)

        $divElem
        (create-lesson-div :day day, :moduleCode moduleCode,
                           :moduleName moduleName, :lessonType lessonType,
                           :lessonGroup lessonGroup, :startTime startTime,
                           :endTime endTime, :venue venue,
                           :weekTextIdx weekTextIdx, :slotsOcc slotsOcc,
                           :bgColorCssClass bgColorCssClass,
                           :isActuallySelected? isActuallySelected?)]
    ; Create new row if necessary
    (if (= rowNum (timetable-day-get-nr-rows day))
        (add-new-row-to-timetable-day! day))

    ; Update in-memory representation of timetable
    (timetable-add-lesson! day
                           rowNum
                           {:moduleCode moduleCode,
                            :lessonType lessonType,
                            :lessonGroup lessonGroup,
                            :startTime startTime,
                            :endTime endTime}
                           $divElem)

    (let [$td (html-timetable-get-td day rowNum startTime)]
      (.append $td $divElem)
      ; Increase colspan and delete the <td> after it that occupy the timeslot
      ; of the lesson
      (attr $td "colspan" slotsOcc)
      (doseq [$siblingTd (take (- slotsOcc 1) (.nextAll $td))]
        (.remove $siblingTd))

      {:day day, :rowNum rowNum, :startTime startTime, :endTime endTime,
       :moduleCode moduleCode, :lessonType lessonType, :lessonGroup lessonGroup,
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
(declare overall-timetable-prune-empty-rows)
(declare add-missing-td-elements-replacing-lesson)
(declare remove-lesson-group-html)
(declare shift-lessons-upwards-to-replace-empty-slots!)

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
  [moduleCode lessonType selectedLessonGroup $divElem bgColorCssClass]
  (fn [evt ui]
    (let [$uiHelper (aget ui "helper")]
      (.addClass $uiHelper "lesson-draggable-helper")

      ; Set the width of the ui helper <div> to the width of the original <div>.
      ; The ui helper <div> is cloned from the original <div> and the original
      ; <div>'s width was not explicitly set. Hence the original <div> fits the
      ; width of its parent <td>.
      ; The ui helper has no such parent and will be resized according to its
      ; contents, which is not what we want. We want it to have the same width
      ; as that of the original <div>.
      (width $uiHelper (width $divElem))

      (let [allLessonGroups
            (get-all-lesson-group-strings moduleCode lessonType)

            notSelectedLessonGroups
            (remove #{selectedLessonGroup} allLessonGroups)

            ; Add all the <div> elements
            augmentedTTLessonInfoSeq
            (doall
              (flatten
                (map (fn [unselectedLessonGroup]
                       (add-module-lesson-group! moduleCode lessonType
                                                 unselectedLessonGroup
                                                 bgColorCssClass false))
                     notSelectedLessonGroups)))]
        (html-timetable-display-saturday-if-needed!)
        (set! Lessons-Created-By-Draggable augmentedTTLessonInfoSeq)))))

(defn- $lesson-div-remove
  "Removes the <div> object of a lesson module, and replaces the former
   columns occupied by its parent <td> with new <td> elements."
  [$lessonDiv startTime endTime]
  (let [$parentTd (parent $lessonDiv)
        qtipApi   (.qtip $lessonDiv "api")]
    (remove-attr $parentTd "colspan")
    (.destroy qtipApi true)
    (.remove $lessonDiv)
    (add-missing-td-elements-replacing-lesson $parentTd startTime endTime)
    (html-timetable-display-saturday-if-needed!)))

(defn- lesson-draggable-stop-evt-handler-maker
  "Returns a function used as the `stop` event handler for a draggable lesson."
  [moduleCode lessonType lessonGroup bgColorCssClass]
  (fn [evt ui]
    (let [$helper         (aget ui "helper")
          destLessonGroup (data $helper LESSON-GROUP-CHANGE-KEY)

          affectedDaysSet
          (get-set-from-seq-of-maps-with-day-key Lessons-Created-By-Draggable)]

      (doseq [augTTLessonInfo Lessons-Created-By-Draggable]
        (timetable-remove-lesson! (:day augTTLessonInfo)
                                  (:rowNum augTTLessonInfo)
                                  (dissoc augTTLessonInfo :divElem :day
                                          :rowNum)))

      (doseq [augTTLessonInfo Lessons-Created-By-Draggable]
        (let [$divElem  (:divElem augTTLessonInfo)]
          ($lesson-div-remove $divElem (:startTime augTTLessonInfo)
                              (:endTime augTTLessonInfo))))

      (overall-timetable-prune-empty-rows affectedDaysSet)
      (set! Lessons-Created-By-Draggable nil)

      ; User has dropped the draggable helper <div> onto a droppable
      (if (not (nil? destLessonGroup))
          (let [augTTLessonInfoSeq (timetable-remove-lesson-group! moduleCode
                                                                   lessonType)
                affectedDaysSet    (get-set-from-seq-of-maps-with-day-key
                                     augTTLessonInfoSeq)]
            (remove-lesson-group-html augTTLessonInfoSeq)
            (shift-lessons-upwards-to-replace-empty-slots! augTTLessonInfoSeq)
            (overall-timetable-prune-empty-rows affectedDaysSet)
            (update-ModulesSelected-for-affected-days! affectedDaysSet)

            ; add the newly selected lesson group
            (add-module-lesson-group! moduleCode lessonType destLessonGroup
                                      bgColorCssClass true)
            (html-timetable-display-saturday-if-needed!)
            (update-document-location-hash-with-changed-lesson-group!
              moduleCode lessonType destLessonGroup))))))

(defn- make-added-lessons-draggable
  "Makes the <div> elements of selected lessons draggable."
  [$divElemSeq moduleCode lessonType lessonGroup bgColorCssClass]
  ; And Drag event handler
  (doseq [$divElem $divElemSeq]
    (.addClass $divElem "lesson-cursor-grab")
    (.draggable $divElem
                (js-obj "zIndex" 100
                        "helper" "clone"
                        "revert" "invalid"
                        "start"  (lesson-draggable-start-evt-handler-maker
                                   moduleCode lessonType lessonGroup $divElem
                                   bgColorCssClass)
                        "stop"   (lesson-draggable-stop-evt-handler-maker
                                   moduleCode lessonType lessonGroup
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
                  (.addClass $divElem "lesson-droppable-hover")
                  (.qtip $divElem "show"))

                "out"
                (fn [evt ui]
                  (.removeClass $divElem "lesson-droppable-hover")
                  (.addClass $divElem "lesson-droppable-not-hover")
                  (.qtip $divElem "hide"))

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
  [moduleCode lessonType lessonGroup bgColorCssClass isActuallySelected?
   & {:keys [addedViaUrlHashInit?] :or {addedViaUrlHashInit? false}}]
  (if (module-has-lesson-group? moduleCode lessonType lessonGroup)
      (let [moduleName          (get-module-name-from-module-code moduleCode)
            modulesMapLessonSeq (get-ModulesMapLesson-seq moduleCode lessonType
                                                          lessonGroup)

            augLessonInfoSeq
            (doall
              (map (fn [modulesMapLesson]
                     (add-module-lesson!
                       :moduleCode moduleCode, :moduleName moduleName,
                       :lessonType lessonType, :lessonGroup lessonGroup,
                       :day         (:day modulesMapLesson),
                       :startTime   (:startTime modulesMapLesson),
                       :endTime     (:endTime modulesMapLesson),
                       :venue       (:venue modulesMapLesson),
                       :weekTextIdx (:weekTextIdx modulesMapLesson),
                       :bgColorCssClass bgColorCssClass,
                       :isActuallySelected? isActuallySelected?))
                   modulesMapLessonSeq))

            lessonInfoSeq       (map #(dissoc %1 :divElem :moduleCode
                                              :lessonType :lessonGroup)
                                     augLessonInfoSeq)
            $divElemSeq         (map #(:divElem %1) augLessonInfoSeq)]
        (if isActuallySelected?
            (do
              (update-ModulesSelected-with-lesson-group! moduleCode lessonType
                                                         lessonGroup
                                                         lessonInfoSeq)
              (if (module-not-in-ModulesSelectedOrder? moduleCode)
                  (do
                    (add-to-ModulesSelectedOrder! moduleCode)
                    ; Micro optimization for modules added via url hash
                    ; during initialization
                    (if (not addedViaUrlHashInit?)
                        (select2-box-update-modules!))))

              ; Only lesson types with more than 1 option of lesson group
              ; will be draggable
              (if (module-lesson-type-has-multiple-choices? moduleCode
                                                            lessonType)
                  (make-added-lessons-draggable $divElemSeq moduleCode
                                                lessonType lessonGroup
                                                bgColorCssClass)
                  ; for lessons with 1 option, we set the cursor style to
                  ; be `not-allowed` when hovering over it
                  (doseq [$divElem $divElemSeq]
                    (.addClass $divElem "lesson-cursor-not-draggable"))))

            ; lesson was added due to draggable <div>
            ; Make it droppable
            (make-fake-lessons-droppable augLessonInfoSeq))
        augLessonInfoSeq)))

(defn add-module!
  "Adds a module to the timetable.

   A random lesson group of each type of lesson (Lecture, Tutorial, etc) will
   be added."
  [moduleCode]
  (cond
    (not (is-module-code? moduleCode))
    (str "Non-existent module &quot;" moduleCode "&quot;")

    (not (is-module-selected? moduleCode))
    (let [lessonTypes     (get-all-lesson-types-for-module moduleCode)
          bgColorCssClass (get-next-lesson-bg-color-css-class)

          newModInfoSeq
          (reduce (fn [moduleInfoSeq lessonType]
                    (conj moduleInfoSeq
                          {:moduleCode  moduleCode,
                           :lessonType  lessonType,
                           :lessonGroup
                           (get-first-lesson-group-for-module-lesson-type
                             moduleCode lessonType)}))
                  []
                  lessonTypes)]

      (doseq [moduleInfo newModInfoSeq]
        (add-module-lesson-group! (:moduleCode moduleInfo)
                                  (:lessonType moduleInfo)
                                  (:lessonGroup moduleInfo)
                                  bgColorCssClass
                                  true))

      (html-timetable-display-saturday-if-needed!)

      ; Update URL hash with newly added module
      (update-document-location-hash-with-new-module!
        {:moduleCode moduleCode,

         :preToUrlHashLessonGroupSeqSorted
         (sort-PreToUrlHashLessonGroup-seq
           (map #(dissoc
                   (assoc %1 :lessonType
                          (Lesson-Type-Long-To-Short-Form (:lessonType %1)))
                   :moduleCode)
                newModInfoSeq))})
      "Added!")

      :else "Already Added!"))

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
        (reduce (fn [lgMapFinal [moduleCode missingLessonTypes]]
                  (reduce
                    (fn [lgMap lessonType]
                      (assoc-in lgMap [moduleCode lessonType]
                                (get-first-lesson-group-for-module-lesson-type
                                  moduleCode lessonType)))
                    lgMapFinal
                    missingLessonTypes))
                moduleLessonGroupsMap
                missingModuleLessonTypes)]

    ; Produce the final module info sequence
    (flatten
      (map (fn [moduleCode]
             (map (fn [[lessonType lessonGroup]]
                    {:moduleCode  moduleCode
                     :lessonType  lessonType
                     :lessonGroup lessonGroup})
                  (get moduleLessonGroupsMapFinal moduleCode)))
           moduleCodesSeq))))

(defn add-module-lesson-groups-from-url-hash!
  "Adds the module lesson groups available in the url hash. Erroneous lesson
   groups are ignored. If there are missing lesson groups for any module after
   going through the url hash, a random lesson group is chosen.

   NOTE: This function should only be called once."
  [urlHash]
  (let [moduleUrlHashArray                (.split urlHash "&")

        modUrlHashRegex
        #"^([A-Z]+\d{4}[A-Z]*)_(DL|L|LAB|PL|PT|R|SEM|ST|T|T2|T3)=([A-Z0-9]+)$"

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
                 {:moduleCode  (nth matchArray 1)
                  :lessonType  (Lesson-Type-Short-To-Long-Form
                                 (nth matchArray 2))
                  :lessonGroup (nth matchArray 3)})
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
                                  true
                                  :addedViaUrlHashInit? true)))

    (html-timetable-display-saturday-if-needed!)
    ; Update Select2 box, since this is not done in `add-module-lesson-group!`.
    ; Repeated work will be done there if we did so
    (select2-box-update-modules!)
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
  [$parentTd startTime endTime]
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
          (recur $newTdElem (inc timeIdx))))))

(defn- remove-lesson-group-html
  "Given a sequence of `TimetableLessonInfo` objects, each augmented with the
   `:divElem` key (whose value is the jQuery <div> object for that lesson),
   removes the actual <div> elements for these lesson from the HTML timetable.
   NOTE: This does NOT remove any empty rows / perform module shifting.

   The `augTTLessonInfoSeq` argument should be the return value of the
   `timetable-remove-lesson-group!` function."
  [augTTLessonInfoSeq]
  (doseq [{:keys [startTime endTime divElem] :as augTTLessonInfo}
          augTTLessonInfoSeq]
    ($lesson-div-remove divElem startTime endTime)))

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
        rowNum     (:rowNum removedAugTTLessonInfo)
        startTime  (:startTime removedAugTTLessonInfo)
        endTime    (:endTime removedAugTTLessonInfo)
        minTimeIdx time-helper/TIME-INDEX-MIN
        maxTimeIdx time-helper/TIME-INDEX-MAX]

    (.log js/console "get-largest-consecutive-time-slot-freed-up-by-lesson")
    [
      ; Find the lesson right before the removed lesson
      (let [immediateTTLessonInfoBefore
            (timetable-get-closest-TimetableLessonInfo-before-time
              day rowNum startTime)]
        (if (nil? immediateTTLessonInfoBefore)
            minTimeIdx
            (:endTime immediateTTLessonInfoBefore)))

      ; Find the lesson right after the removed lesson
      (let [immediateTTLessonInfoAfter
            (timetable-get-closest-TimetableLessonInfo-on-or-after-time
              day rowNum endTime)]
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
  [{:keys [day rowNum startTime endTime] :as removedAugTTLessonInfo}]
  (let [; This is the time range freed up by removal of the lesson
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
        (fn [{:keys [augTTLessonInfoMapToShift occupiedVec]
              :as   reduceResult}
             rowIdx]
          (if (timetable-row-empty? day rowIdx)
              ; Skip empty row
              reduceResult

              ; Non-empty row
              (let [ttLessonInfoToShift
                    (timetable-row-get-lessons-satisfying-pred
                      day rowIdx
                      (fn [{:keys [startTime endTime]}]
                        (and (>= startTime lowTimeIdx)
                             (<= (dec endTime) highTimeIdx)
                             (not-occupied? occupiedVec startTime endTime))))]
                (.log js/console "suspect above")
                (.log js/console (str "rowIdx = " rowIdx))
                (.log js/console (str "occupiedVec = " (.stringify js/JSON (clj->js occupiedVec))))
                {:augTTLessonInfoMapToShift
                 (reduce (fn [augTTLessonInfoMap [ttLessonInfo $divElem]]
                           (.log js/console ":augTTLessonInfoMapToShift")
                           (assoc augTTLessonInfoMap
                                  (assoc ttLessonInfo :day day :rowNum rowIdx)
                                  $divElem))
                         augTTLessonInfoMapToShift
                         ttLessonInfoToShift)

                 :occupiedVec
                 (reduce (fn [occVec [{:keys [startTime endTime]}  _]]
                           (.log js/console ":occupiedVec")
                           (reduce (fn [ov timeIdx] (assoc ov timeIdx true))
                                   occVec
                                   (range startTime endTime)))
                         occupiedVec
                         ttLessonInfoToShift)})))
        {:augTTLessonInfoMapToShift {},
         ; nil = free slot, true = occupied slot
         :occupiedVec      (vec (map (fn [t] nil)
                                     (range time-helper/TIME-INDEX-MIN
                                            (inc time-helper/TIME-INDEX-MAX))))}
        ; go through each row in the day, below the current row
        (drop (inc rowNum) (range (timetable-day-get-nr-rows day)))))))

(defn- shift-lesson-to-row!
  "Shifts a lesson from its original row up to the given row.

   The parameter `augTTLessonInfo` is a `TimetableLessonInfo` object augmented
   with the `:day` and `:rowNum` keys."
  [destinationRowNum {:keys [day rowNum startTime endTime] :as augTTLessonInfo}
   $divElem]
  (let [slotsOccupied (- endTime startTime)
        $sourceTd     (html-timetable-get-td day rowNum startTime)
        $destTd       (html-timetable-get-td day destinationRowNum startTime)
        ttLessonInfo  (dissoc augTTLessonInfo :day :rowNum)]
    (.log js/console (str "day = " day ", sourceRowNum = " rowNum ", destinationRowNum = " destinationRowNum))
    ; Transfer lesson <div> to the destination <td>
    (.append $destTd $divElem)
    ; restore the colspan of the source <td>
    (remove-attr $sourceTd "colspan")
    ; fill in removed <td> for the source <td>
    (add-missing-td-elements-replacing-lesson $sourceTd startTime endTime)
    ; remove sibling <td> for the destination <td> to make way for columns
    ; occupied by $divElem
    (doseq [$siblingTd (take (dec slotsOccupied) (.nextAll $destTd))]
      (.remove $siblingTd))
    ; update colspan of destination <td>
    (attr $destTd "colspan" slotsOccupied)
    ; update `Timetable` global
    (timetable-remove-lesson! day rowNum ttLessonInfo)
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
          (filter (fn [{:keys [day rowNum]}]
                    (timetable-row-not-empty? day rowNum))
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

(defn- overall-timetable-prune-empty-rows
  "Removes empty rows resulting from the removal of lessons on given days."
  [affectedDaysSet]
  (doseq [day affectedDaysSet]
    (let [rowsPartition (timetable-day-get-empty-and-non-empty-rows day)]
      (.log js/console (str "day = " day ", emptyRows = "
                            (.stringify js/JSON (clj->js (:emptyRows rowsPartition)))
                            ", nonEmptyRows = "
                            (.stringify js/JSON (clj->js (:nonEmptyRows rowsPartition)))))

      (timetable-prune-empty-rows-for-day! day rowsPartition)
      (html-timetable-prune-empty-rows-for-day! day rowsPartition))))

(defn remove-module!
  "Removes a module from the timetable.

   This deletes every lesson of the module from the timetable."
  [moduleCode]
  (if (is-module-selected? moduleCode)
      (let [lessonTypes
            (keys (get ModulesSelected moduleCode))

            augTTLessonInfoSeq
            (flatten (map #(timetable-remove-lesson-group! moduleCode %1)
                          lessonTypes))

            affectedDaysSet
            (get-set-from-seq-of-maps-with-day-key augTTLessonInfoSeq)]

        ; remove the lesson <div>s
        (remove-lesson-group-html augTTLessonInfoSeq)

        (.log js/console "Reached here man")

        ; Perform shifting due to removal of lesson <div>s
        (shift-lessons-upwards-to-replace-empty-slots! augTTLessonInfoSeq)
        (.log js/console "Here too bro")
        (overall-timetable-prune-empty-rows affectedDaysSet)
        (.log js/console "Whats up")

        (remove-module-from-ModulesSelected! moduleCode affectedDaysSet)
        (.log js/console "Boo ya!")
        (.log js/console (str "ModulesSelected:" (.stringify js/JSON (clj->js ModulesSelected))))

        (remove-module-from-document-location-hash! moduleCode)

        (remove-from-ModulesSelectedOrder! moduleCode)
        (.log js/console "I hear and obey!")
        (html-timetable-display-saturday-if-needed!)
        ; Update Select2 box
        (select2-box-update-modules!))))

(defn remove-all-modules!
  "Removes all modules from the timetable"
  []
  (html-timetable-reset!)
  (timetable-create!)
  (html-timetable-display-saturday-if-needed!)
  (reset-ModulesSelected!)
  (reset-ModulesSelectedOrder!)
  (select2-box-update-modules!)
  (document-location-hash-reset!))
