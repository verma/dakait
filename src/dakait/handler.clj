(ns dakait.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [compojure.route :as route]))

(defn all-files [path]
  (seq (->> (.listFiles (clojure.java.io/file path))
         (filter #(not (.isHidden %1)))
         (map #(.getName %)))))

(defn as-json[m]
  { :status 200
    :headers { "Content-Type" "application/json; charset=utf-8" }
    :body (json/write-str m) })

(defroutes app-routes
  (GET "/" [] (as-json (all-files "/Users/verma")))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
