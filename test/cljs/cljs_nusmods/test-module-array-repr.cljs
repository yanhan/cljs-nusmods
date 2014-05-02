(ns cljs-nusmods.test-module-array-repr
  (:require-macros [cemerick.cljs.test :refer (is deftest)])
  (:require [cljs-nusmods.module-array-repr :as module-array-repr]
            [cemerick.cljs.test :as t]))

(deftest test-get-module-code
  (is (= "CS1101S" (module-array-repr/get-module-code (array "CS1101S")))))
