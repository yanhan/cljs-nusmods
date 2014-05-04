(ns cljs-nusmods.test-time
  (:require-macros [cemerick.cljs.test :refer (is deftest)])
  (:require [cljs-nusmods.time :as time-helper]
            [cemerick.cljs.test :as t]))

(deftest test-convert-time-index-to-mult-of-50-int-0800
  (is (= 800 (time-helper/convert-time-index-to-mult-of-50-int 0))))

(deftest test-convert-time-index-to-mult-of-50-int-0930
  (is (= 950 (time-helper/convert-time-index-to-mult-of-50-int 3))))

(deftest test-convert-time-index-to-mult-of-50-int-0630
  (is (= 650 (time-helper/convert-time-index-to-mult-of-50-int -3))))
