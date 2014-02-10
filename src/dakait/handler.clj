(ns dakait.handler
  (:use compojure.core
        dakait.views
        dakait.files
        dakait.config
        [hiccup.middleware :only (wrap-base-url)])
  (:require [compojure.handler :as handler]
            [clojure.data.json :as json]
            [compojure.route :as route]))

(defn as-json[m]
  { :status 200
    :headers { "Content-Type" "application/json; charset=utf-8" }
    :body (json/write-str m) })

(defn as-json-error [error-message]
  { :status 503
    :headers { "Content-Type" "application/json; charset=utf-8" }
    :body (json/write-str { :message error-message }) })

(defn handle-files
  "Fetch files for the given path"
  [path]
  (try
    (as-json (all-remote-files path))
    (catch Exception e (as-json-error (.getMessage e)))))

(defroutes app-routes
  (GET "/" [] (index-page))
  (GET "/a/files" {params :params }
       (handle-files (:path params)))
  (GET "/a/params" {params :params} (pr-str params))
  (route/resources "/")
  (route/not-found "Not Found"))


(load-and-validate)

(def app
  (-> (handler/site app-routes)
      (wrap-base-url)))
