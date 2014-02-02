(ns dakait.files
  (:use compojure.core
        [clojure.tools.logging :only (info error)]
        [carica.core :only [config]])
  (:require 
    [clojure.java.io :as io]
    [clj-ssh.ssh :as ssh]))

(def ^{:dynamic true} *ssh-agent* (ssh/ssh-agent {}))
(defn- agent-with-identity []
  (when (ssh/has-identity? *ssh-agent* "sftp-server")
    *ssh-agent*)
  (ssh/add-identity *ssh-agent* 
                { :name "sftp-server"
                  :public-key-path (config :public-key)
                  :private-key-path (config :private-key) })
  (identity *ssh-agent*))
  
(defn join-path [& parts]
  (.getPath (apply io/file parts)))

(defn all-files [path]
  (let [file-type (fn [e] (if (.isDirectory e) "dir" "file"))
        file-size (fn [e] (.length e))]
    (seq (->> (.listFiles (clojure.java.io/file path))
           (filter #(not (.isHidden %1)))
           (map (fn [e] { :name (.getName e)
                          :type (file-type e)
                          :size (file-size e)}))))))

(defn list-remote-files [path]
  (let [session (ssh/session (agent-with-identity) (config :sftp-host) {:strict-host-key-checking :no})]
    (ssh/with-connection session
      (let [channel (ssh/ssh-sftp session)]
        (ssh/with-channel-connection channel
          (when (not (nil? path)) (ssh/sftp channel {} :cd path))
          (ssh/sftp channel {} :ls))))))

(defn all-remote-files [path]
  (let [query-path (join-path (config :base-path) path)
        entries (list-remote-files query-path)
        not-hidden? (fn [e] (not= (.charAt (.getFilename e) 0) \.))
        file-type (fn [e] (if (.isDir (.getAttrs e)) "dir" "file"))
        file-size (fn [e] (.getSize (.getAttrs e)))
        ]
    (info "query path: " query-path)
    (->> entries 
         (filter not-hidden?)
         (map (fn [e] { :name (.getFilename e) 
                        :type (file-type e)
                        :size (file-size e)})))))

(defn mk-path [p]
  (let
    [base-path (io/file "/Users/verma")]
    (cond
      (nil? p) (.getPath base-path)
      :else (.getPath (io/file base-path (io/file p))))))

