(ns dakait.handler
  (:use dakait.views
        dakait.files
        dakait.config
        dakait.mdns
        dakait.staging
        dakait.pusher
        org.httpkit.server
        compojure.core
        [compojure.handler :only (site)]
        [clojure.core.async :only(>! go alts! timeout)]
        [dakait.util :only (join-path)]
        [dakait.downloader :only (run start-download downloads-in-progress downloads-pending)]
        [dakait.assocs :only (load-associations add-association get-association)]
        [dakait.tags :only (load-tags get-all-tags add-tag remove-tag find-tag)]
        [clojure.tools.logging :only (info error)]
        [hiccup.middleware :only (wrap-base-url)])
  (:require [ring.middleware.reload :as reload]
            [clojure.tools.cli :refer [parse-opts]]
            [compojure.route :as route]
            [clojure.data.json :as json]))

;; All the channels that need to be notified about any download status updates
;;
(def ws-downloads-channels (atom []))
(def start-options (atom nil))

(defn as-json
  ([]
   (as-json {}))
  ([m]
   { :status 200
    :headers { "Content-Type" "application/json; charset=utf-8" }
    :body (json/write-str m) }))

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

(defn add-tag-info
  "Add tag info to all files provided given the base path"
  [base-path files]
  (map (fn [f]
         (let [name (:name f)
               file-path (join-path base-path name)
               ass (get-association file-path)]
           (if (nil? ass)
             f
             (assoc f :tag ass)))) files))

(defn random-html-color
  "Generate an awesome randome html color"
  []
  (let [r (java.util.Random.)]
    (str "hsl(" (.nextInt r 360) ",50%,70%)")))

(defn handle-config
  "Returns the current required set of configuration to client"
  []
  (as-json {:server-name (config :server-name)}))

(defn handle-files
  "Fetch files for the given path"
  [path]
  (try
    (->> path
         all-remote-files
         (add-tag-info (join-path (config :base-path) path))
         as-json)
    (catch Exception e 
      (info "There was an error handling files request: " (.getMessage e))
      (.printStackTrace e)
      (as-json-error (.getMessage e)))))

(defn handle-apply-tag
  "Handle application of tags onto files"
  [tag target]
  (do-with-cond 
    (or (nil? tag) (nil? target)) 400 "Tag and taget file needs to be specified"
    (let [tag-obj (find-tag tag)
          dest (:target tag-obj)]
      (do-with-cond
        (or (nil? tag-obj) (nil? dest)) 400 "The specified tag is invalid"
        (let [target-path (join-path (config :base-path) target)
              dest-path (join-path (config :local-base-path) (:target tag-obj))]
          ;; start the download
          ;;
          ;; (start-download target-path dest-path)
          ;; setup appropriate association
          ;;
          (stage-file target-path dest-path)
          (add-association tag target-path)
          (as-json))))))

(defn handle-get-all-tags []
  (let [s (seq (get-all-tags))]
    (as-json
      (cond
        (nil? s) []
        :else (map (fn [[n m]] (assoc m :name n)) s)))))

(defn handle-create-tag
  "Handle creation of new tags"
  [name target]
  (let [random-color (random-html-color)]
    (add-tag name target random-color)
    (as-json {:name name
              :target target
              :color random-color})))

(defn handle-remove-tag
  "Handle deletion of tags"
  [name]
  (remove-tag name)
  (as-json))

(defn handle-active-downloads
  "Handle active downloads"
  []
  (as-json {:active (downloads-in-progress)
            :pending (map (fn [d] {:from (first d) :to (second d)}) (downloads-pending))}))


(defn handle-push
  "Handle a URL push"
  [url]
  (info "Going to try and push: " url)
  (try
    (if (do-push url)
      (as-json {:message "Pushed successfully"})
      (as-json-error "Failed to complete action"))
    (catch Exception e
      (as-json-error (.getMessage e)))))

(defn ws-downloads-pusher
  "Pushes downloads status every so often"
  []
  (go (while true
        (alts! [(timeout 1000)])
        (let [msg (json/write-str {:active (downloads-in-progress)
                                   :pending (map (fn [d] {:from (first d) :to (second d)}) (downloads-pending))})]
          (doseq [c @ws-downloads-channels]
            (send! c msg))))))

;; This end-point handles all incoming websocket connections, or long polling connections
(defn ws-downloads
  "Handle incoming websocket connections for downloads updates"
  [request]
  (with-channel request channel
    (on-close channel (fn [status]
                        (println "Websocket channel is going away!")
                        (swap! ws-downloads-channels
                               (fn [chs] (remove #(= % channel) chs)))))
    (println "New downloads notification")
    (swap! ws-downloads-channels #(cons channel %))))

(defroutes app-routes
  (GET "/" [] (if (:debug @start-options)
                (debug-index-page)
                (index-page)))
  (GET "/a/config" []
       (handle-config))
  (GET "/a/files" {params :params }
       (handle-files (:path params)))
  (GET "/a/tags" [] (handle-get-all-tags))
  (POST "/a/tags" {params :params}
        (handle-create-tag (:name params) (:target params)))
  (DELETE "/a/tags/:name" [name]
          (handle-remove-tag name))
  (POST "/a/apply-tag" {params :params }
       (handle-apply-tag (:tag params) (:target params)))
  (GET "/a/downloads" [] (handle-active-downloads))
  (GET "/ws/downloads" [] ws-downloads)
  (GET "/a/params" {params :params} (pr-str params))
  (POST "/a/push" {params :params}
        (handle-push (:url params)))
  (route/resources "/")
  (route/not-found "Not Found"))


(def app
  (-> (site app-routes)
      wrap-base-url
      reload/wrap-reload))

(def cli-options
  [["-d" "--debug"]])

(defn do-init [& args]
  "Initialize program"
  (try
    (reset! start-options (:options (parse-opts args cli-options)))

    (load-and-validate)
    (load-tags (str (config :config-data-dir) "/tags.json"))
    (load-associations)
    (init-stager)
    (run)
    (ws-downloads-pusher)
    (catch Exception e
      (println "Program initialization failed: " (.getMessage e))
      (System/exit 1))))

