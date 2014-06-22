(ns cljs-nusmods.server
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as resp]
            clout.core))

(def acad-year-sem-route
  (clout.core/route-compile "/:acad-year/:sem/"
                            {:acad-year #"20\d{2}-20\d{2}"
                             :sem       #"sem(?:1|2)"}))

(defroutes app-routes
  (GET acad-year-sem-route [acad-year sem]
       (resp/resource-response "index.html" {:root "public"}))
  (GET "/" [] (resp/redirect "2013-2014/sem2/"))
  (route/resources "/"))

(def app
  (handler/site app-routes))
