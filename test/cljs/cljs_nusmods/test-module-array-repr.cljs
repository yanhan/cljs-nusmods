(ns cljs-nusmods.test-module-array-repr
  (:require-macros [cemerick.cljs.test :refer (is deftest)])
  (:require [cljs-nusmods.module-array-repr :as module-array-repr]
            [cemerick.cljs.test :as t]))

(deftest test-get-module-code
  (is (= "CS1101S" (module-array-repr/get-module-code (array "CS1101S")))))

(deftest test-get-module-name
  (is (= "Programming Methodology (Scheme)"
         (module-array-repr/get-module-name
           (array "CS1101S" "Programming Methodology (Scheme)")))))

(deftest test-get-module-mc
  (is (= 4 (module-array-repr/get-module-mc (array 0 0 4)))))

(deftest test-get-module-exam-date
  (is (= 4 (module-array-repr/get-module-exam-date
             (array 0 0 0 4)))))

(deftest test-get-module-exam-date-string
  (is (= "5 May 2014 5:00 PM"
         (module-array-repr/get-module-exam-date-string
           (array "2014-04-29T13:00+0800" "2014-05-08T09:00+0800"
                  "2014-05-05T17:00+0800" "2014-04-30T17:00+0800")
           (array 0 0 0 2)))))

(deftest test-get-module-lecture-timings
  (is (= ["Monday Afternoon" "Wednesday Evening" "Friday Morning"]
         (js->clj
           (module-array-repr/get-module-lecture-timings
             (js-obj "Seminar" "Lecture"
                     "Laboratory" "Tutorial"
                     "Tutorial" "Tutorial"
                     "Lecture" "Lecture"
                     "Recitation" "Lecture")
             (array "Lecture" "Recitation" "Laboratory" "Seminar" "Tutorial")
             (array 0 0 0 0
                    (array
                      ; Lecture, Monday 1230
                      (array 0 0 0 9)
                      ; Tutorial, Thursday 0800
                      (array 0 4 3 0)
                      ; Laboratory, Wednesday 0900
                      (array 0 2 2 2)
                      ; Tutorial, Saturday 1630
                      (array 0 4 5 17)
                      ; Seminar (Lecture), Wednesday 1800
                      (array 0 3 2 20)
                      ; Laboratory, Friday 1100
                      (array 0 2 4 6)
                      ; Recitation (Lecture), Friday 1000
                      (array 0 1 4 4))))))))

(deftest test-get-module-tutorial-timings
  (is (= ["Tuesday Afternoon" "Monday Morning" "Saturday Evening"
          "Monday Evening"]
         (js->clj
           (module-array-repr/get-module-tutorial-timings
             (js-obj "Seminar" "Lecture"
                     "Laboratory" "Tutorial"
                     "Tutorial" "Tutorial"
                     "Lecture" "Lecture"
                     "Recitation" "Lecture")
             (array "Lecture" "Recitation" "Laboratory" "Seminar" "Tutorial")
             (array 0 0 0 0
                    (array
                      ; Lecture, Monday 1230
                      (array 0 0 0 9)
                      ; Tutorial, Tuesday 1300
                      (array 0 4 1 10)
                      ; Laboratory (Tutorial), Monday 0900
                      (array 0 2 0 2)
                      ; Tutorial, Saturday 1800
                      (array 0 4 5 20)
                      ; Seminar, Wednesday 1800
                      (array 0 3 2 20)
                      ; Laboratory (Tutorial), Monday 1900
                      (array 0 2 0 22)
                      ; Recitation, Friday 1000
                      (array 0 1 4 4))))))))
