(ns ^{:doc "Helper functions for array representation of Module object"}
  cljs-nusmods.module-array-repr
  (:require [cljs-nusmods.lesson-array-repr :as lesson-array-repr]))

(def MODULE_CODE_REGEX #"^\D+(\d{4})\D*$")

(defn get-module-code
  "Retrieves the module code from the array representation of a module"
  [moduleArrayRepr]
  (nth moduleArrayRepr 0))

(defn get-module-name
  "Obtains the name of a module from its array representation"
  [moduleArrayRepr]
  (nth moduleArrayRepr 1))

(defn get-module-level 
  "Obtains the level of a module from its array representation"
  [moduleArrayRepr]
  (let [moduleCode (get-module-code moduleArrayRepr)]
    (if (= "PH2302 / GEK2039" moduleCode)
      2000
      (let [matchArray (.match moduleCode MODULE_CODE_REGEX)
            ; TODO: Handle case where module code does not match regex
            moduleDigitsInt (js/parseInt (nth matchArray 1))]
        (- moduleDigitsInt (mod moduleDigitsInt 1000))))))

(defn get-module-mc
  "Retrieves the mcs of a module from its array representation"
  [moduleArrayRepr]
  (nth moduleArrayRepr 2))

(defn get-module-exam-date
  "Retrieves the exam date string of a module from its array representation"
  [examDateStringsArray moduleArrayRepr]
  (nth examDateStringsArray (nth moduleArrayRepr 3)))

(defn- get-module-timetable
  "Retrieves the timetable of a module from its array representation"
  [moduleArrayRepr]
  (nth moduleArrayRepr 4))

(defn- module-lesson-timings-for-exhibit-filter-fn-maker
  "Given a lesson type (the string 'Lecture' or 'Tutorial'), returns a function
   which when given the array representation of a Module, returns a JavaScript
   array of strings of lesson timings for that type of lesson"
  [lessonType]
  (fn [lessonTypesHash lessonTypesStringsArray moduleArrayRepr]
    (let [timetable (get-module-timetable moduleArrayRepr)]
      (clj->js
        (map lesson-array-repr/get-lesson-start-time-string-for-exhibit-filter
             (filter
               (fn [lessonArrayRepr]
                 (= lessonType
                    (lesson-array-repr/get-lesson-overall-type-string
                      lessonTypesHash lessonTypesStringsArray lessonArrayRepr)))
               timetable))))))

(def
  ^{:doc "Returns a JavaScript array of strings of Lecture timings of a module
          from its array representation"}
  get-module-lecture-timings
  (module-lesson-timings-for-exhibit-filter-fn-maker "Lecture"))

(def
  ^{:doc "Returns a JavaScript array of strings of Tutorial timings of a module
          from its array representation"}
  get-module-tutorial-timings
  (module-lesson-timings-for-exhibit-filter-fn-maker "Tutorial"))
