(defproject cljs-nusmods "0.1.0-SNAPSHOT"
  :description "NUS Timetable Builder in ClojureScript"
  :url "http://example.com/FIXME"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [jayq "2.5.0"]]
  :plugins [[lein-cljsbuild "1.0.3"]]
  :cljsbuild {
    :builds [{
      :source-paths ["src/cljs"]
      :compiler {
        :output-to "resources/public/js/main.js"
        :optimizations :advanced
        :externs ["externs/jquery-1.9.js"]}}]})
