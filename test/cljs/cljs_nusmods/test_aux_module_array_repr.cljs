(ns cljs-nusmods.test-aux-module-array-repr
  (:require-macros [cemerick.cljs.test :refer (is deftest)])
  (:require [cljs-nusmods.aux-module-array-repr :as aux-module-array-repr]
            [cemerick.cljs.test :as t]))

(deftest test-get-module-type-faculty
  (let [typeBitmask aux-module-array-repr/MODULE_TYPE_FACULTY
        resultArray (aux-module-array-repr/get-module-types
                      (array 0 0 typeBitmask))]
    (is (and (= (.-length resultArray) 1)
             (= (get aux-module-array-repr/MODULE_TYPES_MAP
                     aux-module-array-repr/MODULE_TYPE_FACULTY)
                (nth resultArray 0))))))

(deftest test-get-module-type-multiple
  (let [typeBitmask (bit-or aux-module-array-repr/MODULE_TYPE_FACULTY
                            aux-module-array-repr/MODULE_TYPE_SS
                            aux-module-array-repr/MODULE_TYPE_GEM)
        resultArray (aux-module-array-repr/get-module-types
                      (array 0 0 typeBitmask))]
    (is (and (= (.-length resultArray) 3)
             (= (get aux-module-array-repr/MODULE_TYPES_MAP
                     aux-module-array-repr/MODULE_TYPE_FACULTY)
                (nth resultArray 0))
             (= (get aux-module-array-repr/MODULE_TYPES_MAP
                     aux-module-array-repr/MODULE_TYPE_SS)
                (nth resultArray 1))
             (= (get aux-module-array-repr/MODULE_TYPES_MAP
                     aux-module-array-repr/MODULE_TYPE_GEM)
                (nth resultArray 2))))))

(deftest test-get-module-type-not-in-cors
  (let [resultArray (aux-module-array-repr/get-module-types (array 0 0 0))]
    (is (and (= (.-length resultArray) 1)
             (= aux-module-array-repr/NOT_IN_CORS (nth resultArray 0))))))
