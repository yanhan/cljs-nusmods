(defproject cljs-nusmods "0.1.0-SNAPSHOT"
  :description "NUS Timetable Builder in ClojureScript"
  :url "http://example.com/FIXME"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-http "0.9.2"]
                 [compojure "1.1.6"]
                 [environ "0.5.0"]
                 [ring/ring-json "0.3.1"]
                 [selmer "0.6.7"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [jayq "2.5.0"]]
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-ring "0.8.10"]
            [com.cemerick/clojurescript.test "0.3.0"]]
  :ring {:handler cljs-nusmods.server/app}
  :cljsbuild {
    :builds [{
      :source-paths ["src/cljs"]
      :compiler {
        :output-to "resources/public/js/main.js"
        :optimizations :advanced
        :externs ["externs/jquery-1.9.js"
                  "externs/bootstrap.js"
                  "externs/exhibit3.js"
                  "externs/select2.js"
                  "externs/jquery-ui.js"
                  "externs/qtip.js"
                  "externs/pace.js"
                  "externs/zeroclipboard.js"]}
    } {
      :source-paths ["src/cljs" "test/cljs"]
      :compiler {
        :output-to "target/testable.js"
        :optimizations :whitespace }}]

    :test-commands {
      "unit-tests" [
        ; Command to run the tests
        "phantomjs"
        ; The symbol :runner must be the 2nd argument
        :runner
        ; JavaScript libraries
        "test/js/vendor/jquery-2.1.0.min.js"
        ; JavaScript globals required by tests
        "test/js/globals.js"
        ; Actual compiled test file
        "target/testable.js"]
    }
  })
