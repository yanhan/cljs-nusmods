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
  (is (= "5 May 2014 17:00"
         (module-array-repr/get-module-exam-date
           (array "29 Apr 2014 13:00" "8 May 2014 09:00" "5 May 2014 17:00"
                  "30 Apr 2014 17:00")
           (array 0 0 0 2)))))
