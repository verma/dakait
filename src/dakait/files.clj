(ns dakait.files
  (:use compojure.core
        dakait.config
        [clojure.tools.logging :only (info error)])
  (:require 
    [clojure.java.io :as io]
    [clj-ssh.ssh :as ssh]))

(def ssh-agent (atom nil))
(def ssh-session (atom nil))
(def ssh-channel (atom nil))

(defn- agent-with-identity []
  "Get an agent with properties setup correctly and the identity added from configuration"
  (when
    (or (nil? @ssh-agent)
        (not (ssh/ssh-agent? @ssh-agent)))
    (let [agent (ssh/ssh-agent {})]
      (ssh/add-identity agent { 
                  :private-key-path (config :private-key) })
      (info "new agent")
      (reset! ssh-agent agent)))
  @ssh-agent)

(defn- session []
  "Get the currently active session, if one doesn't exist, create a new one"
  (when (nil? @ssh-session)
    (let [host (config :sftp-host)
          user (config :username)
          port (config :sftp-port)
          agent (agent-with-identity)
          session (ssh/session agent host {:port port
                                           :username user
                                           :strict-host-key-checking :no})]
      (reset! ssh-session session)))
  @ssh-session)

(defn- channel []
  "Get a connected channel, if the session is disconnected, connect it and then reconnect the channel
  and return it"
  (let [this-session (session)]
    ;; reconnect session if not connected
    (when-not (ssh/connected? this-session)
      (ssh/connect this-session))
    (when (nil? @ssh-channel)
      (let [channel (ssh/ssh-sftp this-session)]
        (reset! ssh-channel channel))))
  (when-not (ssh/connected-channel? @ssh-channel)
    (info "Reconnecting channel..")
    (ssh/connect-channel @ssh-channel))
  @ssh-channel)
  
(defn join-path [& parts]
  "Join the paths together"
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
  "Get the list of all files at the given path"
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

