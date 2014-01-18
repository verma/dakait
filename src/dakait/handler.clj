(ns dakait.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-ssh.ssh :as ssh]
            [compojure.route :as route]))

(defn all-files [path]
  (seq (->> (.listFiles (clojure.java.io/file path))
         (filter #(not (.isHidden %1)))
         (map #(.getName %)))))


(defn list-remote-files [path]
  (let [agent (ssh/ssh-agent {})]
    (let [session (ssh/session agent "home" {:strict-host-key-checking :no})]
      (ssh/with-connection session
        (let [channel (ssh/ssh-sftp session)]
          (ssh/with-channel-connection channel
            (ssh/sftp channel {} :ls)))))))

(defn all-remote-files [path]
  (let [entries (list-remote-files path)
        not-hidden? (fn [e] (not= (.charAt (.getFilename e) 0) \.))
        file-type (fn [e] (if (.isDir (.getAttrs e)) "dir" "file"))]
    (->> entries 
         (filter not-hidden?)
         (map (fn [e] { :name (.getFilename e) :type (file-type e)})))))

(defn as-json[m]
  { :status 200
    :headers { "Content-Type" "application/json; charset=utf-8" }
    :body (json/write-str m) })

(defroutes app-routes
  (GET "/" [] (as-json (all-remote-files "./")))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
