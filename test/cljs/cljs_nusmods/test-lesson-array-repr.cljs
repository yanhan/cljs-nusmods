(ns cljs-nusmods.test-lesson-array-repr
  (:require-macros [cemerick.cljs.test :refer (is deftest)])
  (:require [cljs-nusmods.lesson-array-repr :as lesson-array-repr]
            [cemerick.cljs.test :as t]))

(deftest test-get-lesson-type
  (is (= 3 (lesson-array-repr/get-lesson-type (array 0 3)))))

(deftest test-get-lesson-day
  (is (= 4 (lesson-array-repr/get-lesson-day (array 0 0 4)))))

(deftest test-get-lesson-start-time
  (is (= 9 (lesson-array-repr/get-lesson-start-time (array 0 0 0 9)))))

(deftest test-get-lesson-end-time
  (is (= 15 (lesson-array-repr/get-lesson-end-time (array 0 0 0 9 15)))))

(deftest test-get-lesson-start-time-string-for-exhibit-filter-wed-morning
  (is (= "Wednesday Morning"
         (lesson-array-repr/get-lesson-start-time-string-for-exhibit-filter
           (array 0 0 2 2 6)))))

(deftest test-get-lesson-start-time-string-for-exhibit-filter-1130
  (is (= "Monday Morning"
         (lesson-array-repr/get-lesson-start-time-string-for-exhibit-filter
           (array 0 0 0 7 9)))))

(deftest test-get-lesson-start-time-string-for-exhibit-filter-1200
  (is (= "Thursday Afternoon"
         (lesson-array-repr/get-lesson-start-time-string-for-exhibit-filter
          (array 0 0 3 8 10)))))

(deftest test-get-lesson-start-time-string-for-exhibit-filter-fri-afternoon
  (is (= "Friday Afternoon"
         (lesson-array-repr/get-lesson-start-time-string-for-exhibit-filter
           (array 0 0 4 14 18)))))

(deftest test-get-lesson-start-time-string-for-exhibit-filter-1800
  (is (= "Tuesday Evening"
         (lesson-array-repr/get-lesson-start-time-string-for-exhibit-filter
           (array 0 0 1 20 24)))))

(deftest test-get-lesson-start-time-string-for-exhibit-filter-wed-evening
  (is (= "Wednesday Evening"
         (lesson-array-repr/get-lesson-start-time-string-for-exhibit-filter
           (array 0 0 2 22 26)))))

(deftest test-get-lesson-type-string-lecture
  (is (= "Lecture"
         (lesson-array-repr/get-lesson-type-string
           (js-obj "Laboratory" "Tutorial" , "Recitation" "Tutorial",
                   "Lecture" "Lecture", "Sectional Teaching" "Lecture")
           (array "Laboratory" "Recitation" "Lecture" "Sectional Teaching")
           (array 0 3)))))

(deftest test-get-lesson-type-string-tutorial
  (is (= "Tutorial"
         (lesson-array-repr/get-lesson-type-string
           (js-obj "Laboratory" "Tutorial" , "Recitation" "Tutorial",
                   "Lecture" "Lecture", "Sectional Teaching" "Lecture")
           (array "Laboratory" "Recitation" "Lecture" "Sectional Teaching")
           (array 0 1)))))
