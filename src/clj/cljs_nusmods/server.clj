(ns cljs-nusmods.server
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as resp]
            clojure.string
            clout.core
            selmer.parser
            selmer.filters))

; String replace
(selmer.filters/add-filter! :replace clojure.string/replace)

; "sem2" -> "Sem 2"
(selmer.filters/add-filter! :display-sem
                            (fn [s]
                              (clojure.string/capitalize
                                (str (subs s 0 3) " " (last s)))))

(def acad-year-sem-route
  (clout.core/route-compile "/:acad-year/:sem/"
                            {:acad-year #"20\d{2}-20\d{2}"
                             :sem       #"sem(?:1|2)"}))

(defroutes app-routes
  (GET acad-year-sem-route [acad-year sem]
       (selmer.parser/render-file "public/index.html"
                                  {:acad-year acad-year :sem sem}))
  (GET "/" [] (resp/redirect "2013-2014/sem2/"))
  (route/resources "/"))

(def app
  (handler/site app-routes))
