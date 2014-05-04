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

(deftest test-get-module-description
  (is (= "Basic culinary skills in 3 months =)"
         (aux-module-array-repr/get-module-description
           (array "Basic culinary skills in 3 months =)")))))

(deftest test-get-module-department-has-faculty
  (let [moduleDepartment (aux-module-array-repr/get-module-department
                           (js-obj 0 1, 1 1, 2 2, 3 1, 4 2, 5 1)
                           (array "Chemistry" "Science" "Engineering"
                                  "Mathematics" "Electrical Engineering"
                                  "Statistics")
                           (array 0 3))]
    (and (is (= 1 (.-length moduleDepartment)))
         (is (= "Mathematics" (first moduleDepartment))))))

(deftest test-get-module-department-no-faculty
  (let [moduleDepartment (aux-module-array-repr/get-module-department
                           (js-obj 0 2, 1 2, 2 2, 3 5, 4 5, 5 5, 6 6)
                           (array "Computer Science" "Information Systems"
                                  "School of Computing" "Economics" "Philosophy"
                                  "Arts & Social Sciences" "Law")
                           (array 0 6))]
    (is (= "Law" moduleDepartment))))

(deftest test-get-module-lecturers
  (let [moduleLecturers (aux-module-array-repr/get-module-lecturers
                          (array "Mr Funny" "Mr Boring" "Miss Soft" "Mr Strong"
                                 "Sir Peter" "King Danny" "Lady Ace")
                          (array 0 0 0 (array 4 2 0 6)))]
    (is (= ["Sir Peter" "Miss Soft" "Mr Funny" "Lady Ace"]
           (js->clj moduleLecturers)))))

(deftest test-get-module-prereqs-string-has-prereqs
  (is (= "CS1010 or equivalent"
         (aux-module-array-repr/get-module-prereqs-string
           (array "String One" "String Two" "CS1010 or equivalent" "String 3")
           (array 0 0 0 0 2)))))

(deftest test-get-module-prereqs-string-no-prereqs
  (is (= -1 (aux-module-array-repr/get-module-prereqs-string
              (array "A1" "A2" "A3")
              (array 0 0 0 0 -1)))))

(deftest test-get-module-preclusions-string-has-preclusions
  (is (= "ModOneA, ModOneB, ModOneE"
         (aux-module-array-repr/get-module-preclusions-string
           (array "String One" "ModOneA, ModOneB, ModOneE", "ModTwo")
           (array 0 0 0 0 0 1)))))

(deftest test-get-module-preclusions-string-no-preclusions
  (is (= -1 (aux-module-array-repr/get-module-preclusions-string
              (array "String One" "String Two" "String Three")
              (array 0 0 0 0 0 -1)))))

(deftest test-get-module-workload-string-has-workload
  (is (= "4-4-2-4-8"
         (aux-module-array-repr/get-module-workload-string
           (array "1-2-3-4-5" "3-4-5" "6-8-9-1-3" "4-4-2-4-8" "5-6-9-1-3")
           (array 0 0 0 0 0 0 3)))))

(deftest test-get-module-workload-string-no-workload
  (is (= -1 (aux-module-array-repr/get-module-workload-string
              (array "workload1" "workload2")
              (array 0 0 0 0 0 0 -1)))))
