(ns ^{:doc "Helper functions for working with the array representation of
            Lesson objects"}
  cljs-nusmods.lesson-array-repr
  (:require [cljs-nusmods.time :as time-helper]))

(defn get-lesson-label
  "Retrieves the label (class number) of a Lesson from its array representation"
  [lessonArrayRepr]
  (nth lessonArrayRepr 0))

(defn get-lesson-type
  "Retrieves the (index to an Array of Lesson type Strings) of a Lesson from
   its array representation"
  [lessonArrayRepr]
  (nth lessonArrayRepr 1))

(defn get-lesson-type-string
  "Retrieves the type string of a Lesson from its array representation"
  [lessonArrayRepr lessonTypesStringsArray]
  (nth lessonTypesStringsArray (get-lesson-type lessonArrayRepr)))

(defn get-lesson-day
  "Retrieves the day of a Lesson from its array representation"
  [lessonArrayRepr]
  (nth lessonArrayRepr 2))

(defn get-lesson-start-time
  "Retrieves the start time of a Lesson from its array representation"
  [lessonArrayRepr]
  (nth lessonArrayRepr 3))

(defn get-lesson-end-time
  "Retrieves the end time of a Lesson from its array representation"
  [lessonArrayRepr]
  (nth lessonArrayRepr 4))

(defn get-lesson-venue
  "Retrieves the (index to an Array of venue Strings) of a Lesson from its array
   representation"
  [lessonArrayRepr]
  (nth lessonArrayRepr 5))

(defn get-lesson-venue-string
  "Retrieves the venue string of a Lesson from its array representation"
  [lessonArrayRepr venuesStringsArray]
  (nth venuesStringsArray (get-lesson-venue lessonArrayRepr)))

(defn get-lesson-weektext
  "Retrieves the (index to an Array of WeekText Strings) of a Lesson from its
   array representation"
  [lessonArrayRepr]
  (nth lessonArrayRepr 6))

(defn get-lesson-start-time-string-for-exhibit-filter
  "Given the array representation of a lesson, returns a string of its
   start time suitable for the `Lecture Timings` / `Tutorial Timings`
   Exhibit Facet in the Module Finder page.
   An example of such a string is 'Tuesday Afternoon'."
  [lessonArrayRepr]
  (let [dayString (time-helper/DAY_INTEGER_TO_STRING
                    (get-lesson-day lessonArrayRepr))
        startTime (time-helper/convert-time-index-to-mult-of-50-int
                    (get-lesson-start-time lessonArrayRepr))
        endTime   (time-helper/convert-time-index-to-mult-of-50-int
                    (get-lesson-end-time lessonArrayRepr))]
    (str dayString " "
         (cond
           (<= startTime 1150) "Morning"
           (>= startTime 1800) "Evening"
           :else "Afternoon"))))

(defn get-lesson-overall-type-string
  "Returns either the String 'Lecture' or 'Tutorial' based on the lesson type
   of the array representation of the Lesson object"
  [lessonTypesHash lessonTypesStringsArray lessonArrayRepr]
  (let [lessonType (get-lesson-type lessonArrayRepr)]
    (aget lessonTypesHash (nth lessonTypesStringsArray lessonType))))
