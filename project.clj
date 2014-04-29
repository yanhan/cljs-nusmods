(defproject cljs-nusmods "0.1.0-SNAPSHOT"
  :description "NUS Timetable Builder in ClojureScript"
  :url "http://example.com/FIXME"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [jayq "2.5.0"]]
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-ring "0.8.10"]]
  :ring {:handler cljs-nusmods.server/app}
  :cljsbuild {
    :builds [{
      :source-paths ["src/cljs"]
      :compiler {
        :output-to "resources/public/js/main.js"
        :optimizations :advanced
        :externs ["externs/jquery-1.9.js" "externs/foundation.js"]}}]})
