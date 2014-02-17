(ns dakait.handler
  (:use compojure.core
        dakait.views
        dakait.files
        dakait.config
        [clojure.core.async :only(>! go)]
        [dakait.downloader :only (channel run)]
        [dakait.assocs :only (load-associations add-association)]
        [dakait.tags :only (load-tags get-all-tags add-tag remove-tag find-tag)]
        [hiccup.middleware :only (wrap-base-url)])
  (:require [compojure.handler :as handler]
            [clojure.data.json :as json]
            [compojure.route :as route]))

(defn as-json[m]
  { :status 200
    :headers { "Content-Type" "application/json; charset=utf-8" }
    :body (json/write-str m) })

(defn as-json-error
  ([code error-message]
   { :status code
    :headers { "Content-Type" "application/json; charset=utf-8" }
    :body (json/write-str { :message error-message }) })
  ([error-message]
   (as-json-error 503 error-message)))

(defmacro do-with-cond [condition error msg & body]
  `(if ~condition
     (as-json-error ~error ~msg)
     (do
       ~@body)))

(defn random-html-color
  "Generate an awesome randome html color"
  []
  (let [r (java.util.Random.)]
    (str "hsl(" (.nextInt r 360) ",50%,70%)")))

(defn handle-files
  "Fetch files for the given path"
  [path]
  (try
    (as-json (all-remote-files path))
    (catch Exception e (as-json-error (.getMessage e)))))

(defn handle-apply-tag
  "Handle application of tags onto files"
  [tag target]
  (do-with-cond 
    (or (nil? tag) (nil? target)) 400 "Tag and taget file needs to be specified"
    (let [tag-obj (find-tag tag)
          dest (get tag-obj "target")]
      (do-with-cond
        (or (nil? tag-obj) (nil? dest)) 400 "The specified tag is invalid"
        (go (>! channel [:get target (get tag-obj "target")]))
        (add-association tag target)
        (as-json {:status 1})))))

(defn handle-get-all-tags []
  (let [s (seq (get-all-tags))]
    (as-json
      (cond
        (nil? s) []
        :else (map (fn [[n m]] (assoc m :name n)) s)))))

(defn handle-create-tag
  "Handle creation of new tags"
  [name target]
  (add-tag name target (random-html-color))
  (as-json {:status 1}))

(defn handle-remove-tag
  "Handle deletion of tags"
  [name]
  (remove-tag name)
  (as-json {:success 1}))

(defroutes app-routes
  (GET "/" [] (index-page))
  (GET "/tags" [] (tags-page))
  (GET "/a/files" {params :params }
       (handle-files (:path params)))
  (GET "/a/tags" [] (handle-get-all-tags))
  (POST "/a/tags" {params :params}
        (handle-create-tag (:name params) (:target params)))
  (DELETE "/a/tags/:name" [name]
          (handle-remove-tag name))
  (POST "/a/apply-tag" {params :params }
       (handle-apply-tag (:tag params) (:target params)))
  (GET "/a/params" {params :params} (pr-str params))
  (route/resources "/")
  (route/not-found "Not Found"))


(load-and-validate)
(load-tags (str (config :config-data-dir) "/tags.json"))
(load-associations)
(run)

(def app
  (-> (handler/site app-routes)
      (wrap-base-url)))
