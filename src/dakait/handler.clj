(ns dakait.handler
  (:use compojure.core
        dakait.views
        dakait.files
        dakait.config
        [clojure.core.async :only(>! go)]
        [dakait.downloader :only (channel run)]
        [dakait.tags :only (load-tags get-all-tags)]
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

(defn handle-apply-tag
  "Handle application of tags onto files"
  [tag target]
  (if
    (or (nil? tag) (nil? target))
    (as-json-error "Tag and target file needs to be specified")
    (do
      (go (>! channel [:get target tag]))
      (as-json {:status 1}))))

(defn handle-get-all-tags []
  (let [s (seq (get-all-tags))]
    (as-json
      (cond
        (nil? s) []
        :else (map (fn [[n m]] (assoc m :name n)) s)))))

(defroutes app-routes
  (GET "/" [] (index-page))
  (GET "/tags" [] (tags-page))
  (GET "/a/files" {params :params }
       (handle-files (:path params)))
  (GET "/a/tags" [] (handle-get-all-tags))
  (POST "/a/apply-tag" {params :params }
       (handle-apply-tag (:tag params) (:target params)))
  (GET "/a/params" {params :params} (pr-str params))
  (route/resources "/")
  (route/not-found "Not Found"))


(load-and-validate)
(load-tags (str (config :config-data-dir) "/tags.json"))
(run)

(def app
  (-> (handler/site app-routes)
      (wrap-base-url)))
