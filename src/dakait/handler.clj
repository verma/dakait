(ns dakait.handler
  (:use compojure.core
        dakait.views
        dakait.files
        [carica.core :only [config]]
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

(defn check-config []
  (let [host (config :sftp-host)
        pk (config :private-key)
        pb (config :public-key)]
    (println "Dakiat server.")
    (when (some nil? [host pk pb])
      (println "Configuration invalid, need host, private key and public key, did you setup resources/config.clj?")
      (System/exit 1))
    (println "Using configuration:")
    (println "... sftp server:" host)
    (println "... private key:" pk)
    (println "...  public key:" pb)))


(check-config)

(def app
  (-> (handler/site app-routes)
      (wrap-base-url)))
