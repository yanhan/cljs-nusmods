(ns ^{:doc "Code for Timetable Builder page"}
  cljs-nusmods.timetable
  (:use [jayq.core :only [$ attr children hide insert-after is parent prepend
                          prevent remove-attr show text width]])
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
        ModulesMap (aget js/window "ModulesMap")
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

(defn- shift-lesson-to-row
  "Shifts a lesson from its original row up to the given row.

   The parameter `augTTLessonInfo` is a `TimetableLessonInfo` object augmented
   with the `:day` and `:rowNum` keys."
  [destinationRowNum augTTLessonInfo $divElem]
  (let [day                     (:day augTTLessonInfo)
        sourceRowNum            (:rowNum augTTLessonInfo)
        startTime               (:startTime augTTLessonInfo)
        endTime                 (:endTime augTTLessonInfo)
        slotsOccupied           (- endTime startTime)
        [hourClass minuteClass] (get-css-hour-minute-classes-for-time startTime)
        tdSelectorString        (str "td" "." hourClass "." minuteClass)
        $sourceTableRow         (do (.log js/console (str "sourceRowNum = " sourceRowNum))
                                    (nth (children (nth HTML-Timetable day) "tr")
                                     sourceRowNum))
        $sourceTd               (nth
                                  (children $sourceTableRow tdSelectorString)
                                  0)
        $destTableRow           (nth (children (nth HTML-Timetable day) "tr")
                                     destinationRowNum)
        $destTd                 (nth
                                  (children $destTableRow tdSelectorString)
                                  0)
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
    (timetable-remove-lesson day sourceRowNum ttLessonInfo)
    (timetable-add-lesson day destinationRowNum ttLessonInfo $divElem)))

(defn- shift-lessons-upwards-to-replace-empty-slots
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
                (shift-lesson-to-row rowNum augTTLessonInfo $divElem))
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
          $day            (nth HTML-Timetable day)]
      (if (>= nrNonEmptyRows 2)
          ; we have at least 2 non empty rows, so remove all empty rows
          (do (set! Timetable
                    (update-in Timetable [day]
                               (fn [ttDay]
                                 (vec (map (fn [rowIdx] (nth ttDay rowIdx))
                                           nonEmptyRowsVec)))))
              ; remove empty <tr> elements
              (doseq [[idx rowIdx] (map vector (range nrEmptyRows)
                                        emptyRowsVec)]
                (.remove (nth (children $day "tr") (- rowIdx idx)))))

          ; We have less than 2 non empty rows. We remove as many empty rows
          ; as we can, while ensuring that there are 2 rows for that day.
          (let [nrEmptyRowsToKeep    (- 2 nrNonEmptyRows)
                emptyRowsToKeepVec   (take nrEmptyRowsToKeep emptyRowsVec)
                rowsToKeepSeq        (sort (fn [a b] (< a b))
                                          (concat nonEmptyRowsVec
                                                  emptyRowsToKeepVec))
                emptyRowsToRemoveVec (drop nrEmptyRowsToKeep emptyRowsVec)]
            (do (set! Timetable
                      (update-in Timetable [day]
                                 (fn [ttDay]
                                   (vec (map (fn [rowIdx] (nth ttDay rowIdx))
                                             rowsToKeepSeq)))))
                ; remove empty <tr> elements
                (doseq [[idx rowIdx]
                        (map vector
                             (range (count emptyRowsToRemoveVec))
                             emptyRowsToRemoveVec)]
                  (.remove (nth (children $day "tr") (- rowIdx idx)))))))

      ; Ensure that the <th> for 'MON', 'TUE', 'WED', 'THU', 'FRI' exists
      (if (empty? (.find $day "tr > th"))
          (let [$trArray (children $day "tr")
                $tr0     (nth $trArray 0)]
            (prepend $tr0
                     ($ (time-helper/DAY-INDEX-TO-TH-HTML-STRING day)))))
      ; adjust rowspan of <th>
      (attr (.find $day "tr > th") "rowspan" (max nrNonEmptyRows 2)))))

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

(defn remove-module
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
          (timetable-remove-lesson day rowNum ttLessonInfo)))

        (.log js/console "Reached here man")

        ; Perform shifting due to removal of lesson <div>s
        (shift-lessons-upwards-to-replace-empty-slots augTTLessonInfoSeq)
        (.log js/console "Here too bro")
        (timetable-prune-empty-rows affectedDaysSet)
        (.log js/console "Whats up")

        ; Update `ModulesSelected`
        (update-ModulesSelected-for-affected-days affectedDaysSet)
        (.log js/console "Boo ya!")
        (.log js/console (str "ModulesSelected:" (.stringify js/JSON (clj->js ModulesSelected))))

        ; Remove from `ModulesSelectedOrder`
        (set! ModulesSelectedOrder (remove #{moduleCode} ModulesSelectedOrder))
        (.log js/console "I hear and obey!")
        ; Update Exhibit3 box
        (select2/select2-box-set-val select2/$Select2-Box
                                     (get-selected-module-codes-as-js-array)))))

(defn remove-all-modules
  "Removes all modules from the timetable"
  []
  ; !!!
  nil
  )
