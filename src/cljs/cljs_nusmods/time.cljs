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

(defn convert-time-index-to-nice-time
  "Converts the index representation of time in the day to a human friendly
   24H time string.

   For example, 0 will be converted to the string `0800`, 15 will be converted
   to the string `1530`."
  [timeIdx]
  (let [time50  (convert-time-index-to-mult-of-50-int timeIdx)
        time502 (if (= 50 (rem time50 100)) (- time50 20) time50)]
    (if (< time502 1000)
        (str "0" time502)
        (str time502))))

(def ^{:doc     "Regex for extracting info from Exam Date strings"
       :private true}
  EXAM-DATE-REGEX #"^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})")

(def ^{:doc     "3 character strings for months of the year"
       :private true}
  MONTH-3CHAR-STRINGS
  ["" "Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"])

(defn exam-date-to-human-friendly-format
  "Converts an exam date to a more friendly format.
   Time is assumed to be Singapore Time."
  [examDateString]
  (if (= "No Exam" examDateString)
      examDateString
      (let [matchArray (.match examDateString EXAM-DATE-REGEX)
            year       (nth matchArray 1)
            month      (js/parseInt (nth matchArray 2) 10)
            day        (js/parseInt (nth matchArray 3) 10)
            hour       (js/parseInt (nth matchArray 4) 10)
            hourP      (if (<= hour 12) hour (- hour 12))
            minute     (nth matchArray 5)
            amPm       (if (>= hour 12) "PM" "AM")]
        (str day " " (nth MONTH-3CHAR-STRINGS month) " " year " "
             hourP ":" minute " " amPm))))
