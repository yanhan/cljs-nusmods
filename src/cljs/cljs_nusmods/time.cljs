(ns ^{:doc "Time / Date related data and functions"}
  cljs-nusmods.time)

(def
  ^{:doc "Used for converting a day integer to its String representation"
   }
  DAY_INTEGER_TO_STRING
  ["Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday"])

(def
  ^{:doc "Number of days in the Timetable"}
  NR-DAYS 6)

(def ^{:doc "Index for Saturday"}
  SATURDAY 5)

(def
  ^{:doc "Time index for the earliest lesson starting time in the Timetable.
          This represents the time 0800."}
  TIME-INDEX-MIN 0)

(def
  ^{:doc "Time index for the latest lesson starting time in the Timetable.
          This represents the time 2330."}
  TIME-INDEX-MAX 31)

(defn convert-time-index-to-mult-of-50-int
  "Converts the index representation of a time in the day
   (index 0 represents 0800) to an integer which looks like 24H time, but in
   multiples of 50, eg. 1130 -> 1150, 1200 -> 1200"
  [timeIdx]
  (+ 800 (* 50 timeIdx)))
