(ns ^{:doc "Code for Timetable Builder page"}
  cljs-nusmods.timetable
  (:use [jayq.core :only [$ attr children hide insert-after is parent prevent
                          remove-attr show text width]])
  (:require [clojure.set]
            [clojure.string]
            [cljs-nusmods.select2 :as select2]
            [cljs-nusmods.time    :as time-helper]))

; Data type Definitions
; =====================
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

(defn- sort-mod-info-url-seq
  "Sorts a sequence of mod info url strings, eg. CS1234_L=1"
  [modInfoUrlSeq]
  (sort (fn [modInfoUrlA modInfoUrlB]
          (< modInfoUrlA modInfoUrlB))
        modInfoUrlSeq))

(defn- mod-info-url-seq-to-url-hash
  "Converts a sequence of mod info url strings (eg. CS1234_L=1) to a url hash"
  [modInfoUrlSeq]
  (clojure.string/join "&" (sort-mod-info-url-seq modInfoUrlSeq)))

(defn- mod-info-to-url-repr
  "Converts a module info map to a url representation"
  [modInfo]
  (str (:moduleCode modInfo) "_"
       (Lesson-Type-Long-To-Short-Form (:lessonType modInfo))
       "=" (:lessonGroup modInfo)))

(defn- set-document-location-hash-from-module-info-seq
  "Sets document.location.hash to a canonical representation.

   NOTE: This function should only be called by the
         `add-module-lesson-groups-from-url-hash` function"
  [moduleInfoSeq]
  (let [moduleInfoUrlSeq (map mod-info-to-url-repr moduleInfoSeq)]
    (aset (aget js/document "location") "hash"
          (mod-info-url-seq-to-url-hash moduleInfoUrlSeq))))

(defn- update-document-location-hash
  "Updates document.location.hash with lesson grousp from a newly added module"
  [newModInfoSeq]
  (let [orgUrlHash       (aget (aget js/document "location") "hash")
        newModInfoElems  (map mod-info-to-url-repr newModInfoSeq)
        newModInfoUrlSeq (concat (clojure.string/split orgUrlHash #"&")
                                 newModInfoElems)]
    (aset (aget js/document "location") "hash"
          (mod-info-url-seq-to-url-hash newModInfoUrlSeq))))

(def ^{:doc "Vector of <tBody> objects representing the days of the timetable
             in the Timetable Builder page"
       :private true
      }
  HTML-Timetable (vec ($ :.day-container)))

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

(defn- timetable-add-lesson
  "Adds a lesson to the `Timetable` global, effectively 'marking' the
   time interval for that lesson as being occupied."
  [day rowNum ttLessonInfo $lessonDiv]
  (set! Timetable
        (update-in Timetable
                   [day rowNum]
                   (fn [ttRow]
                     (assoc ttRow ttLessonInfo $lessonDiv)))))

(defn- timetable-remove-lesson
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
  [moduleCode moduleName lessonType lessonLabel modulesMapLesson bgColorCssClass]
  (let [rowNum    (find-free-row-for-lesson modulesMapLesson)
        day       (:day modulesMapLesson)
        startTime (:startTime modulesMapLesson)
        endTime   (:endTime modulesMapLesson)
        slotsOcc  (- endTime startTime)
        $divElem  ($ "<div />" (js-obj "class" "lesson"))

        [hourClass minuteClass]
        (get-css-hour-minute-classes-for-time startTime)]
    ; Create new row if necessary
    (if (= rowNum (get-nr-rows-in-timetable-day day))
        (add-new-row-to-timetable-day day))

    ; Update in-memory representation of timetable
    (timetable-add-lesson day
                          rowNum
                          {:moduleCode moduleCode,
                           :lessonType lessonType,
                           :lessonGroup lessonLabel,
                           :startTime startTime,
                           :endTime endTime}
                          $divElem)

    ; add background color css class
    (.addClass $divElem bgColorCssClass)
    (.append $divElem (text ($ "<p />") (str moduleCode " " moduleName)))
    (.append $divElem (text ($ "<p />") (str lessonType " [" lessonLabel "]")))
    (.append $divElem (text ($ "<p />") (:venue modulesMapLesson)))
    (width $divElem (str (* half-hour-pixels slotsOcc) "px"))
    (let [dayHTMLElem (nth HTML-Timetable day)
          rowHTMLElem (nth (children dayHTMLElem "tr") rowNum)
          tdHTMLElem  (nth (children
                             rowHTMLElem
                             (str "td" "." hourClass "." minuteClass))
                           0)]
      (.append tdHTMLElem $divElem)
      ; Increase colspan and delete the <td> after it that occupy the timeslot
      ; of the lesson
      (attr tdHTMLElem "colspan" slotsOcc)
      (doseq [siblingTdElem (take (- slotsOcc 1) (.nextAll tdHTMLElem))]
        (.remove siblingTdElem))

      ; Returns a ModulesSelectedLessonInfo object
      {:day day, :rowNum rowNum, :startTime startTime, :endTime endTime})))

(defn- add-module-lesson-group
  "Adds a lesson group of a module to the timetable."
  [moduleCode lessonType lessonLabel bgColorCssClass
   & {:keys [ModulesMap]
      :or   {ModulesMap (aget js/window "ModulesMap")}}]
  (let [moduleName          (get-in ModulesMap [moduleCode "name"])
        modulesMapLessonSeq (get-in ModulesMap [moduleCode "lessons" lessonType
                                                lessonLabel])]
    (if (and moduleName modulesMapLessonSeq)
        (let [lessonInfoSeq (doall
                              (map (fn [modulesMapLesson]
                                     (add-module-lesson moduleCode
                                                        moduleName
                                                        lessonType
                                                        lessonLabel
                                                        modulesMapLesson
                                                        bgColorCssClass))
                                   modulesMapLessonSeq))]
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
          (add-module-lesson-group (:moduleCode moduleInfo)
                                   (:lessonType moduleInfo)
                                   (:lessonGroup moduleInfo)
                                   bgColorCssClass
                                   ModulesMap))

        ; Update URL hash with newly added module
        (update-document-location-hash newModInfoSeq))))

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
    (set-document-location-hash-from-module-info-seq moduleInfoFinal)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; Functions for Removing Modules
;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- add-missing-td-elements-replacing-lesson
  "Adds <td> elements that were removed by the `add-module-lesson` function
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
  "Given a removed lessonInfo, find the largest interval which is freed up due
   to the its removal.

   For instance, given this timetable row:

   x | x | x |  |  |  | y | y | y | y |  |  | z | z

   And we remove the lesson occupying the slots labelled 'y'. The 4 slots
   currently occupied by 'y', along with the 3 empty slots to its left and the
   2 empty slots to its right will be freed up.

   Given that the first slot occupied by the label 'x' is the index 0, this
   function will return the vector [3 11]."
  [removedLessonInfo]
  (let [day        (:day removedLessonInfo)
        row        (:rowNum removedLessonInfo)
        startTime  (:startTime removedLessonInfo)
        endTime    (:endTime removedLessonInfo)
        ttRow      (get-in Timetable [day row])
        minTimeIdx (time-helper/TIME-INDEX-MIN)
        maxTimeIdx (time-helper/TIME-INDEX-MAX)]

    [
      ; Find the minimum timeslot that's free
      (loop [timeIdx     (dec startTime)
             occupiedVec (:occupied ttRow)]
        (cond (< timeIdx minTimeIdx)           minTimeIdx
              (nil? (nth occupiedVec timeIdx)) (recur (dec timeIdx) occupiedVec)
              :else                            (inc timeIdx)))

      ; Find the maximum timeslot that's free
      (loop [timeIdx     (inc endTime)
             occupiedVec (:occupied ttRow)]
        (cond (> timeIdx maxTimeIdx)          maxTimeIdx
              (nil? (nth occupiedVec timeIdx) (recur (inc timeIdx) occupiedVec))
              :else                           (dec timeIdx)))
    ]))

(defn- find-replacement-lessons-within-time
  "Given a lessonInfo which has been removed, find other lessons
   on the same day but in a row below the removed lesson, which could be shifted
   upwards and take the timeslots freed up by the removed lesson."
  [removedLessonInfo]
  (let [day       (:day removedLessonInfo)
        row       (:rowNum removedLessonInfo)
        startTime (:startTime removedLessonInfo)
        endTime   (:endTime removedLessonInfo)

        ; This is the time range freed up by removal of the lesson
        [lowTimeIdx highTimeIdx]
        (get-largest-consecutive-time-slot-freed-up-by-lesson
          removedLessonInfo)

        ModulesMap (aget js/window "ModulesMap")]

    (:lessonsToShiftUp
      (reduce
        (fn [reduceResult ttRow]
          (if (<= (:nrLessons ttRow) 0)
              ; Skip empty row
              reduceResult
              ; Non-empty row
              (let [ttRowOccupied      (:occupied ttRow)
                    currentOccupiedVec (:occupiedVec reduceResult)
  
                    ; get any lessons overlapping with [lowTimeIdx, highTimeIdx]
                    ttRowLessonsPartiallyInTimeRange
                    (reduce (fn [lessonsSet lesson] (conj lessonsSet lesson))
                            #{}
                            ; get everything in [lowTimeIdx, highTimeIdx] and
                            ; remove all `nil`
                            (filter #(not (nil? %1))
                                    (map #(nth ttRowOccupied %1)
                                         (range lowTimeIdx (inc highTimeIdx)))))
  
                    ; Filter those that are within the time range and can fit in
                    ; and unoccupied time interval
                    ttRowLessonsToShift
                    (filter #(let [lesson
                                   (get-in ModulesMap
                                           [(:moduleCode %1) "lessons"
                                            (:lessonType %1) (:lessonGroup %1)
                                            (:index %1)])

                                   lessonStart (:startTime lesson)
                                   lessonEnd   (:endTime lesson)]
                               (and
                                 ; check that lesson is in time range
                                 (>= lessonStart lowTimeIdx)
                                 (<= (dec lessonEnd) highTimeIdx)
                                 ; and is within an unoccupied interval
                                 (every? #(nil? %1)
                                         (map #(nth currentOccupiedVec %1)
                                              (range lessonStart lessonEnd)))))
                            ttRowLessonsPartiallyInTimeRange)]

                {:lessonToShiftUp
                 (reduce (fn [ttRowLessonsSet ttRowLesson]
                           (conj ttRowLessonsSet ttRowLesson))
                         (:lessonsToShiftUp reduceResult)
                         ttRowLessonsToShift)

                 :occupiedVec
                 (reduce (fn [occupiedVec ttRowLesson]
                           (reduce (fn [occVec timeIdx]
                                     (assoc occVec timeIdx true))
                                   occupiedVec
                                   ; get time range of the lesson
                                   (let [lesson
                                         (get-in ModulesMap
                                                 [(:moduleCode ttRowLesson)
                                                  "lessons"
                                                  (:lessonType ttRowLesson)
                                                  (:lessonGroup ttRowLesson)
                                                  (:index ttRowLesson)])]
                                     (range (:startTime lesson)
                                            (:endTime lesson)))))
                         currentOccupiedVec
                         ttRowLessonsToShift)})))
        {:lessonsToShiftUp #{},
         ; nil = free slot, true = occupied slot
         :occupiedVec      [vec (map (fn [t] nil) (range 800 2400 50))]}
        ; go through each row in the day, below the current row
        (drop (inc row) (get-timetable-day day))))))

(defn- shift-lesson-to-row
  "Shifts a lesson from its original row up to the given row."
  [ttRowLesson rowNum]
  j
  )

(defn- shift-lessons-upwards-to-replace-empty-slots
  "For each removed lesson, see if there are any lessons in rows below it that
   can be shifted upwards to occupy the empty slots resulting from its removal."
  [removedLessonInfoSeq]
  (let [; we do not consider any lessons on empty rows for ease of
        ; implementation
        exclude-lessons-on-empty-rows
        (fn [lessonInfoSeq]
          (filter #(let [day   (:day %1)
                         row   (:rowNum %1)
                         ttRow (get-in Timetable [day row])]
                     (not= (:nrLessons ttRow) 0))
                  lessonInfoSeq))

        sort-lesson-info-seq (fn [lessonInfoSeq]
                               (sort #(let [dayA (:day %1)
                                            dayB (:day %2)]
                                        (if (= dayA dayB)
                                          (< (:rowNum %1) (:rowNum %2))
                                          (< dayA dayB)))
                                     lessonInfoSeq))]

    (loop [currentLessonInfoSeq
           (sort-lesson-info-seq (exclude-lessons-on-empty-rows
                                   removedLessonInfoSeq))

           nextLessonInfoVec    []]
      (cond (and (empty? currentLessonInfoSeq) (empty? nextLessonInfoVec))
            nil

            ; we're done for the current set of lessons. Move on to the
            ; set of lessons that were shifted upwards.
            (empty? currentLessonInfoSeq)
            (recur
              (sort-lesson-info-seq (exclude-lessons-on-empty-rows
                                      nextLessonInfoVec))
              [])

            ; find timeslot to shift up to replace gap
            :else
            (let [currentLessonInfo       (first currentLessonInfoSeq)
                  rowNum                  (:rowNum currentLessonInfo)
                  replacementTTRowLessons (find-replacement-lessons-within-time
                                            currentLessonInfo)]
              (doseq [ttRowLesson replacementTTRowLessons]
                (shift-lesson-to-row ttRowLesson rowNum))
              ; TODO: `replacementLessons` is not in the format we want. Make it so.
              (recur (rest currentLessonInfoSeq)
                     ; append newly shifted lessons to nextLessonInfoVec
                     (loop [newlyShiftedLessons    replacementLessons
                            nextLessonInfoVecPrime nextLessonInfoVec]
                       (if (empty? newlyShiftedLessons)
                           nextLessonInfoVecPrime
                           (recur (rest newlyShiftedLessons)
                                  (conj nextLessonInfoVecPrime
                                        (first newlyShiftedLessons)))))))))))

(defn remove-module
  "Removes a module from the timetable.

   This deletes every lesson of the module from the timetable."
  [moduleCode]
  (if (contains? ModulesSelected moduleCode)
      (let [lessonTypes
            (keys (get ModulesSelected moduleCode))

            ttLessonInfoAugmentedSeq
            (flatten (map (fn [lessonType]
                            (remove-lesson-group-html moduleCode lessonType))
                          lessonTypes))]
        (doseq [ttLessonInfoAug ttLessonInfoAugmentedSeq]
          (let [day          (:day ttLessonInfoAug)
                rowNum       (:rowNum ttLessonInfoAug)
                ttLessonInfo (dissoc ttLessonInfoAug :day :rowNum)]
          (timetable-remove-lesson day rowNum ttLessonInfo)))
        (shift-lessons-upwards-to-replace-empty-slots seqOfRemovedLessons)
        ; TODO: Modify `ModulesSelected` to update :rowNum

        ; Remove from `ModulesSelected`
        (set! ModulesSelected (dissoc ModulesSelected moduleCode))
        ; Remove from `ModulesSelectedOrder`
        (set! ModulesSelectedOrder (remove #{moduleCode} ModulesSelectedOrder))
        ; Update Exhibit3 box
        (select2/select2-box-set-val select2/$Select2-Box
                                     (get-selected-module-codes-as-js-array)))))

(defn remove-all-modules
  "Removes all modules from the timetable"
  []
  ; !!!
  nil
  )
