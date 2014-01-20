(ns dakait.files
  (:use compojure.core)
  (:require 
    [clojure.java.io :as io]
    [clj-ssh.ssh :as ssh]))

(defn all-files [path]
  (let [file-type (fn [e] (if (.isDirectory e) "dir" "file"))]
    (seq (->> (.listFiles (clojure.java.io/file path))
           (filter #(not (.isHidden %1)))
           (map (fn [e] { :name (.getName e) :type (file-type e)}))))))


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

(defn mk-path [p]
  (let
    [base-path (io/file "/Users/verma")]
    (cond
      (nil? p) (.getPath base-path)
      :else (.getPath (io/file base-path (io/file p))))))

