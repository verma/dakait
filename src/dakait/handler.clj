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

(defroutes app-routes
  (GET "/" [] (index-page))
  (GET "/a/lfiles" {params :params}
    (let [dst-path (mk-path (:path params))]
       (as-json (all-files dst-path))))
  (GET "/a/files" {params :params }
       (as-json (all-remote-files (:path params))))
  (GET "/a/params" {params :params} (pr-str params))
  (route/resources "/")
  (route/not-found "Not Found"))


(load-and-validate)

(def app
  (-> (handler/site app-routes)
      (wrap-base-url)))
