(ns cljs-nusmods.server
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as resp]))

(defroutes app-routes
  (GET "/" [] (resp/redirect "index.html"))
  (route/resources "/"))

(def app
  (handler/site app-routes))
