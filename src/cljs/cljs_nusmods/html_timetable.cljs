(ns ^{:doc "HTML Timetable related functions"}
  cljs-nusmods.html-timetable
  (:use     [jayq.core :only [$ attr children hide insert-after parent prepend
                              remove-attr show text]]
            [cljs-nusmods.timetable-constants :only [TIMETABLE-MIN-ROWS-FOR-DAY]])
  (:require clojure.string
            [cljs-nusmods.dom-globals :as domGlobals]
            [cljs-nusmods.time        :as time-helper]))

(def ^{:doc "Vector of <tBody> objects representing the days of the timetable
             in the Timetable Builder page"
       :private true
      }
  HTML-Timetable (vec ($ :.day-container)))

(def ^{:doc "HTML string for the <td> elements in a <tr> on the Timetable
             Builder page"}
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

(defn add-new-row-to-day!
  "Adds a new row to the HTML Timetable"
  [day nrRowsInDay]
  (.append (nth HTML-Timetable day)
           ($ (str "<tr class='day-row'>"
                   Timetable-Row-TD-HTML-String "</tr>")))
  ; Modify the rowspan of the <th> in the 0th row for the same day, so that its
  ; height will stretch to fit the new number of rows
  (let [thElem (.find (nth HTML-Timetable day) "tr > th")]
    (attr thElem "rowspan" nrRowsInDay)))

(defn- get-css-hour-minute-classes-for-time
  "Returns the .hXX and .mXX classes for a 0-indexed time"
  [timeIdx]
  (let [time50 (time-helper/convert-time-index-to-mult-of-50-int timeIdx)
        hour   (quot time50 100)
        minute (rem time50 100)]
    [(str "h" (if (< hour 10) "0" "") hour)
     (str "m" (if (not= minute 0) "30" "00"))]))

(defn get-td
  "Given a day, row and start time index of a lesson, retrieves the
   jQuery object for the <td> element in the HTML representation of the
   Timetable."
  [day rowNum timeIdx]
  (let [$day (nth HTML-Timetable day)
        $row (nth (children $day "tr") rowNum)

        [hourClass minuteClass]
        (get-css-hour-minute-classes-for-time timeIdx)]
    (nth (children $row (str "td" "." hourClass "." minuteClass)) 0)))

(defn add-missing-td-elements-replacing-lesson
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

(defn prune-empty-rows-for-day!
  "Removes <tr> elements corresponding to empty rows in `TIMETABLE`.
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

(defn show-or-hide-saturday!
  "Shows/Hides Saturday for the HTML Timetable depending on the value supplied
   to the `showSaturday?` parameter."
  [showSaturday?]
  (if showSaturday?
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

(def ^{:doc     "The string representing weekly frequency for a lesson"
       :private true}
  EVERY-WEEK "Every Week")

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

(defn create-lesson-div
  "Creates a <div> element for a new lesson using jQuery"
  [& {:keys [day moduleCode moduleName lessonType lessonGroup startTime endTime
             venue weekTextIdx slotsOcc bgColorCssClass isActuallySelected?]}]
  (let [$divElem        ($ "<div />" (js-obj "class" "lesson"))
        $lessonTypePara (text ($ "<p />") lessonType)
        weekText        (week-text-index-to-string weekTextIdx)]
    (.addClass $divElem bgColorCssClass)
    ; add background color css class
    (.append $divElem
             (.append ($ "<div />")
                      (text ($ "<span />" (js-obj "class" "module-code"))
                            (str moduleCode " "))
                      (text ($ "<span />" (js-obj "class" "module-name"))
                            moduleName)))
    (.append $lessonTypePara
             (text ($ "<span />" (js-obj "class" "lesson-group"))
                   (str " [" lessonGroup "]")))
    (.append $divElem $lessonTypePara)
    (.append $divElem (text ($ "<p />" (js-obj "class" "venue")) venue))
    (if (not= weekText EVERY-WEEK)
        (.append $divElem
                 (text ($ "<p />" (js-obj "class" "frequency")) weekText)))

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
                                  (str weekText " @ " venue)))
      (.qtip $divElem
             (js-obj "content"  (js-obj "text" $qtipContent)
                     "position" (js-obj "my"       "center left"
                                        "at"       "center right"
                                        "viewport" domGlobals/$window)

                     "style"
                     (js-obj "classes"
                             (clojure.string/join
                               " " ["qtip-bootstrap" "qtip-rounded"
                                    "qtip-shadow"])))))
    $divElem))

(defn $lesson-div-remove
  "Removes the <div> object of a lesson module, and replaces the former
   columns occupied by its parent <td> with new <td> elements."
  [$lessonDiv startTime endTime]
  (let [$parentTd (parent $lessonDiv)
        qtipApi   (.qtip $lessonDiv "api")]
    (remove-attr $parentTd "colspan")
    (.destroy qtipApi true)
    (.remove $lessonDiv)
    (add-missing-td-elements-replacing-lesson $parentTd startTime endTime)))

(defn remove-lesson-group
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

(defn shift-lesson-to-row!
  "Shifts a lesson from its original row up to the given row.

   The parameter `augTTLessonInfo` is a `TimetableLessonInfo` object augmented
   with the `:day` and `:rowNum` keys."
  [destinationRowNum {:keys [day rowNum startTime endTime] :as augTTLessonInfo}
   $divElem]
  (let [slotsOccupied (- endTime startTime)
        $sourceTd     (get-td day rowNum startTime)
        $destTd       (get-td day destinationRowNum startTime)]
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
    (attr $destTd "colspan" slotsOccupied)))

(defn- remove-all-lessons!
  "Removes all lesson <div>s from the HTML timetable."
  []
  (.remove ($ ".lesson")))

(defn reset-timetable!
  "Resets the HTML Timetable"
  [nrRowsInDaysVec]
  (remove-all-lessons!)
  (doseq [[day nrRows] nrRowsInDaysVec]
    (let [$dayElem (nth HTML-Timetable day)]
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
