(ns ^{:doc "Code for Timetable Builder page"}
  cljs-nusmods.timetable
  (:use [jayq.core :only [$ attr children hide is prevent show text width]])
  (:require [clojure.set]
            [clojure.string]
            [cljs-nusmods.select2 :as select2]
            [cljs-nusmods.time    :as time-helper]))

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
   format (Text to the right of arrows `->` represent values):

       {
         Lesson Type String eg. 'Lecture', 'Tutorial', etc
           -> {
                :label -> Lesson label String
                :divs  -> Vector of HTML elements of the lessons
                          under this label
              }
       }"
  :private true
  }
  ModulesSelected {})

(def ^{:doc     "Vector containing module code strings in the order the modules
                 were added."
       :private true}
  ModulesSelectedOrder [])

(defn nr-modules-selected
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
  (let [containsModule (contains? ModulesSelected moduleCode)]
    containsModule))

(def ^{:doc "Vector of <tBody> objects representing the days of the timetable
             in the Timetable Builder page"
       :private true
      }
  HTML-Timetable (vec ($ :.day-container)))

(defn- create-empty-timetable-row
  "Creates an in-memory representation of an empty timetable row.

   Each row corresponds to a <tr> in the timetable. Each row is a map of the
   following format:

       {
         :nrLessonGroups (integer representing the number of lesson groups
                          occupying the current row)
         :occupied       (a vector of timeslots from 0800 to 2330 (inclusive)
                          in 30 min intervals. Each slot is a boolean value.
                          A value of `True` indicates that the slot is occupied.
                          A value of `False` indicates that the slot is free.)
       }"
  []
  {
    :nrLessonGroups 0,
    :occupied       (vec (map (fn [t] nil) (range 800 2400 50)))
  })

(defn- create-day-repr
  "Creates in-memory representation of a single day in the timetable.

   Each day is a vector of rows, and there is a minimum of 2 rows in each day.
   Multiple rows are needed when there are lessons with overlapping start and
   end time."
  []
  [(create-empty-timetable-row) (create-empty-timetable-row)])

(defn init
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

(defn- add-new-row-to-timetable-day
  "Adds a new row to the 0-indexed day in the Timetable, where 0 = Monday,
   1 = Tuesday, until 4 = Friday."
  [day]
  (set! Timetable
        (update-in Timetable [day]
                   (fn [ttDay]
                     (conj ttDay (create-empty-timetable-row)))))
  (.append (nth HTML-Timetable day)
           ($ "<tr class='day-row'>
                 <td class='h08 m00'></td>
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
                 <td class='h23 m30'></td>
               </tr>"))
  ; Modify the rowspan of the <th> in the 0th row in the timetable
  (let [nrRows (get-nr-rows-in-timetable-day day)
        thElem (.find (nth HTML-Timetable day) "tr > th")]
    (attr thElem "rowspan" nrRows)))

(defn- timetable-mark-lesson-slots-occupied
  "Given a 0-indexed row number obtained by `find-free-row-for-lesson`, marks
   the timeslots for the lesson in that row as occupied."
  [rowNum lesson]
  (let [day       (:day lesson)
        startTime (:startTime lesson)
        endTime   (:endTime lesson)]
    (set! Timetable
          (update-in Timetable [day rowNum]
                     (fn [ttRow]
                       {
                         :nrLessonGroups (inc (:nrLessonGroups ttRow))
                         :occupied       (reduce (fn [occupiedVec idx]
                                                   (assoc occupiedVec idx true))
                                                 (:occupied ttRow)
                                                 (range startTime endTime))
                       })))))

(defn- find-free-row-for-lesson
  "Returns the 0-indexed row which can accomodate the given lesson (timeslots
   for the lesson are not occupied). If no existing row can accomodate the
   lesson, then the total number of existing rows is returned to indicate that
   a new row must be created."
  [lesson]
  (let [ttDay     (get-timetable-day (:day lesson))
        startTime (:startTime lesson)
        endTime   (:endTime lesson)]
    (:rowIndex
      (reduce
        (fn [result row]
          (cond
            (:foundFreeRow result)
            result

            (zero? (:nrLessonGroups row))
            (assoc result :foundFreeRow true)

            (not (some true?
                       (map (fn [timeIdx] (nth (:occupied row) timeIdx))
                            (range startTime endTime))))
            (assoc result :foundFreeRow true)

            :else
            (update-in result [:rowIndex] inc)))
        {
          :foundFreeRow false
          :rowIndex     0
        }
        ttDay))))

(defn- get-css-hour-minute-classes-for-time
  "Returns the .hXX and .mXX classes for a 0-indexed time"
  [timeIdx]
  (let [time50 (time-helper/convert-time-index-to-mult-of-50-int timeIdx)
        hour   (quot time50 100)
        minute (rem time50 100)]
    [(str "h" (if (< hour 10) "0" "") hour)
     (str "m" (if (not= minute 0) "30" "00"))]))

(defn- add-module-lesson
  "Adds a single lesson of a module (obtained from the `ModulesMap` global) to
   the timetable, and returns its jQuery div element."
  [moduleCode moduleName lessonType lessonLabel lesson bgColorCssClass]
  (let [rowNum      (find-free-row-for-lesson lesson)
        day         (:day lesson)
        startTime   (:startTime lesson)
        endTime     (:endTime lesson)
        slotsOcc    (- endTime startTime)
        divElem     ($ "<div />" (js-obj "class" "lesson"))
        [hourClass minuteClass] (get-css-hour-minute-classes-for-time
                                  startTime)]
    ; Create new row if necessary
    (if (= rowNum (get-nr-rows-in-timetable-day day))
        (add-new-row-to-timetable-day day))
    (timetable-mark-lesson-slots-occupied rowNum lesson)

    ; add background color css class
    (.addClass divElem bgColorCssClass)
    (.append divElem (text ($ "<p />") (str moduleCode " " moduleName)))
    (.append divElem (text ($ "<p />") (str lessonType " [" lessonLabel "]")))
    (.append divElem (text ($ "<p />") (:venue lesson)))
    (width divElem (str (* half-hour-pixels slotsOcc) "px"))
    (let [dayHTMLElem (nth HTML-Timetable day)
          rowHTMLElem (nth (children dayHTMLElem "tr") rowNum)
          tdHTMLElem  (nth (children
                             rowHTMLElem
                             (str "td" "." hourClass "." minuteClass))
                           0)]
      (.append tdHTMLElem divElem)
      ; Increase colspan and delete the <td> after it that occupy the timeslot
      ; of the lesson
      (attr tdHTMLElem "colspan" slotsOcc)
      (doseq [siblingTdElem (take (- slotsOcc 1) (.nextAll tdHTMLElem))]
        (.remove siblingTdElem))
      divElem)))

(defn- add-module-lesson-group
  "Adds a lesson group of a module to the timetable."
  [moduleCode lessonType lessonLabel bgColorCssClass
   & {:keys [ModulesMap]
      :or   {ModulesMap (aget js/window "ModulesMap")}}]
  (let [moduleName (get-in ModulesMap [moduleCode "name"])
        lessons    (get-in ModulesMap [moduleCode "lessons" lessonType
                                       lessonLabel])]
    (if (and moduleName lessons)
        (let [lessonDivs (vec (map (fn [lesson]
                                (add-module-lesson moduleCode
                                                   moduleName
                                                   lessonType
                                                   lessonLabel
                                                   lesson
                                                   bgColorCssClass))
                              lessons))]
          ; Update ModulesSelected with the lesson group
          (set! ModulesSelected
                (assoc-in ModulesSelected [moduleCode lessonType] lessonLabel))

          ; Add to ModulesSelectedOrder
          (if (not-any? #{moduleCode} ModulesSelectedOrder)
              (do
                (set! ModulesSelectedOrder
                      (conj ModulesSelectedOrder moduleCode))
                ; Update select2 box.
                ; NOTE: This does a quadratic amount of work but I do not have
                ;       a workaround.
                (select2/select2-box-set-val
                  select2/$Select2-Box
                  (get-selected-module-codes-as-js-array))))))))

(defn add-module
  "Adds a module to the timetable.

   A random lesson group of each type of lesson (Lecture, Tutorial, etc) will
   be added."
  [moduleCode]
  (if (not (contains? ModulesSelected moduleCode))
      (let [ModulesMap      (aget js/window "ModulesMap")
            module          (get ModulesMap moduleCode)
            lessonsMap      (get module "lessons")
            bgColorCssClass (get-next-lesson-bg-color-css-class)]
        (doseq [[lessonType lessonGroupsMap] lessonsMap]
          (add-module-lesson-group moduleCode
                                   lessonType
                                   (first (keys lessonGroupsMap))
                                   bgColorCssClass
                                   ModulesMap)))))

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

(defn- get-module-info-from-url-hash-module-info
  "Computes the final module info sequence for modules added via the url hash
   on page initialization.

   NOTE: This function should only be called by
         `add-module-lesson-groups-from-url-hash`"
  [moduleInfoSeq]
  (let [ModulesMap         (aget js/window "ModulesMap")

        moduleLessonGroupsMap
        (reduce (fn [lgMap modInfo]
                  ; Replaces earlier lesson groups with later ones if we
                  ; encounter a duplicate lesson type
                  (assoc-in lgMap [(:moduleCode modInfo) (:lessonType modInfo)]
                            (:lessonGroup modInfo)))
                {}
                moduleInfoSeq)

        moduleCodesSeq (keys moduleLessonGroupsMap)

        ; Map of Module Code -> Sequence of lesson type strings
        moduleLessonTypes
        (reduce (fn [lessonTypesMap moduleCode]
                  (let [ltMap (get moduleLessonGroupsMap moduleCode)]
                    (assoc lessonTypesMap moduleCode (keys ltMap))))
                {}
                moduleCodesSeq)

        ; Similar to `moduleLessonTypes`, but obtained from ModulesMap
        moduleLessonTypesFull
        (reduce (fn [lessonTypesMap moduleCode]
                  (assoc lessonTypesMap moduleCode
                         (keys (get-in ModulesMap
                                       [moduleCode "lessons"]))))
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
                        (let [lessonGroups
                              (get-in ModulesMap
                                      [moduleCode "lessons" lessonType])]
                          (assoc-in lgMap [moduleCode lessonType]
                                           (first (keys lessonGroups)))))
                      lgMapFinal
                      missingLessonTypes)))
                moduleLessonGroupsMap
                (keys missingModuleLessonTypes))]

    ; Produce the final module info sequence
    (flatten
      (map (fn [moduleCode]
             (let [lessonTypesMap (get moduleLessonGroupsMapFinal moduleCode)]
               (map (fn [lessonType]
                      (let [lessonGroup (get lessonTypesMap lessonType)]
                        {:moduleCode  moduleCode
                         :lessonType  lessonType
                         :lessonGroup lessonGroup}))
                    (keys lessonTypesMap))))
           moduleCodesSeq))))

(defn- set-document-location-hash
  "Sets document.location.hash to a canonical representation.

   NOTE: This function should only be called by the
         `add-module-lesson-groups-from-url-hash` function"
  [moduleInfoSeq]
  (let [sortedModInfo (sort (fn [modInfoA modInfoB]
                              (let [moduleCodeA (:moduleCode modInfoA)
                                    moduleCodeB (:moduleCode modInfoB)]
                                (if (not= moduleCodeA moduleCodeB)
                                    (< moduleCodeA moduleCodeB)
                                    (< (:lessonType modInfoA)
                                       (:lessonType modInfoB)))))
                            moduleInfoSeq)]
    (aset (aget js/document "location") "hash"
          (clojure.string/join "&"
            (map (fn [modInfo]
                   (str (:moduleCode modInfo)
                        "_"
                        (Lesson-Type-Long-To-Short-Form (:lessonType modInfo))
                        "="
                        (:lessonGroup modInfo)))
                 sortedModInfo)))))

(defn add-module-lesson-groups-from-url-hash
  "Adds the module lesson groups available in the url hash. Erroneous lesson
   groups are ignored. If there are missing lesson groups for any module after
   going through the url hash, a random lesson group is chosen.

   NOTE: This function should only be called once."
  [urlHash]
  (let [ModulesMap         (aget js/window "ModulesMap")
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
          (fn [modInfo]
            (not (nil? (get-in ModulesMap
                               [(:moduleCode modInfo) "lessons"
                                (:lessonType modInfo)
                                (:lessonGroup modInfo)]))))
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

    ; Add the module lesson groups
    (doseq [modInfo moduleInfoFinal]
      (let [moduleCode      (:moduleCode modInfo)
            bgColorCssClass (get moduleToColorsMap moduleCode)]
        (add-module-lesson-group moduleCode
                                 (:lessonType modInfo)
                                 (:lessonGroup modInfo)
                                 bgColorCssClass)))

    ; Update url hash
    (set-document-location-hash moduleInfoFinal)))

(defn remove-module
  "Removes a module from the timetable.

   This deletes every lesson of the module from the timetable."
  [moduleCode]
  (if (contains? ModulesSelected moduleCode)
    ;!!!
    nil
  ))

(defn remove-all-modules
  "Removes all modules from the timetable"
  []
  ; !!!
  nil
  )
