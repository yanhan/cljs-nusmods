(ns cljs-nusmods.server
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]))

(defroutes app-routes
  (GET "/" [] "<h1>Hello World</h1>")
  (route/resources "/"))

(def app
  (handler/site app-routes))
