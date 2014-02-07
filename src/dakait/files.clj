(ns dakait.files
  (:use compojure.core
        [clojure.tools.logging :only (info error)]
        [carica.core :only [config]])
  (:require 
    [clojure.java.io :as io]
    [clj-ssh.ssh :as ssh]))

(def ssh-agent (atom nil))
(def ssh-session (atom nil))
(def ssh-channel (atom nil))

(defn- agent-with-identity []
  (when
    (or (nil? @ssh-agent)
        (not (ssh/ssh-agent? @ssh-agent)))
    (let [agent (ssh/ssh-agent {})]
      (ssh/add-identity agent { :name "sftp-server"
                  :public-key-path (config :public-key)
                  :private-key-path (config :private-key) })
      (info "new agent")
      (reset! ssh-agent agent)))
  @ssh-agent)

(defn- session []
  (when (nil? @ssh-session)
    (let [session (ssh/session (agent-with-identity) (config :sftp-host) {:strict-host-key-checking :no})]
      (reset! ssh-session session)))
  (when-not (ssh/connected? @ssh-session)
    (info "Reconnecting session...")
    (ssh/connect @ssh-session))
  @ssh-session)

(defn- channel []
  (when (nil? @ssh-channel)
    (let [channel (ssh/ssh-sftp (session))]
      (reset! ssh-channel channel)))
  (when-not (ssh/connected-channel? @ssh-channel)
    (info "Reconnecting channel")
    (ssh/connect-channel @ssh-channel))
  @ssh-channel)
  
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
  ;;(let [this-channel (ssh/ssh-sftp (session))]
    ;;(ssh/with-channel-connection this-channel
  (let [this-channel (channel)]
      (when (not (nil? path)) (ssh/sftp this-channel {} :cd path))
      (ssh/sftp this-channel {} :ls)))

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

