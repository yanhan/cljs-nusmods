(ns ^{:doc "Time / Date related data and functions"}
  cljs-nusmods.time)

(def
  ^{:doc "Used for converting a day integer to its String representation"
   }
  DAY_INTEGER_TO_STRING
  ["Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday"])

(defn convert-time-index-to-mult-of-50-int
  "Converts the index representation of a time in the day
   (index 0 represents 0800) to an integer which looks like 24H time, but in
   multiples of 50, eg. 1130 -> 1150, 1200 -> 1200"
  [timeIdx]
  (+ 800 (* 50 timeIdx)))
