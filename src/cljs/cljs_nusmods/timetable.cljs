(ns ^{:doc "Code for Timetable Builder page"}
  cljs-nusmods.timetable
  (:use [jayq.core :only [$ hide is one prevent show]]))

(def ^{:doc     "In-memory representation of a Timetable"
       :private true}
  Timetable nil)

(defn- create-empty-timetable-row
  "Creates an in-memory representation of an empty timetable row.

   Each row corresponds to a <tr> in the timetable. Each row is a vector of
   timeslots from 0800 to 2330 (inclusive) in 30 min intervals.

   Timeslots not occupied by lessons will be represented by `nil`.
   Timeslots occupied by a lesson will be represented by a map of the
   following format:

   {
     :moduleCode  -> module code string
     :lessonType  -> lesson type string
     :lessonLabel -> lesson label string
   }
   "
  []
  (vec (map (fn [t] nil) (range 800 2400 50))))

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
