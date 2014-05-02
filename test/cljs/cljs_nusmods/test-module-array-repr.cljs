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
