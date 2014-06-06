(ns dakait.files
  (:use compojure.core
        dakait.config
        [clojure.core.async :only (thread)]
        [dakait.util :only (join-path)]
        [clojure.tools.logging :only (info error)])
  (:require 
    [clojure.java.io :as io]
    [clj-ssh.ssh :as ssh]))

(def ssh-agent (atom nil))
(def ssh-session (atom nil))

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

(def session-invalidator (atom nil))
(defn- reset-session-invalidate
  "This function acts as a hit on the session being active, every time this function is called
  session invalidation is reset, when the reset invalidation expires the ssh session is
  invalidated and set to nil"
  []
  (swap! session-invalidator
         (fn [si]
           (when-not (nil? si)
             (future-cancel si))
           (future
             (Thread/sleep 120000)
             (info "Invalidating session")
             (swap! ssh-session (fn [s]
                                  (ssh/disconnect s)
                                  nil))
             (reset! session-invalidator nil)))))


(defn- session []
  "Get the currently active session, if one doesn't exist, create a new one and make sure the
  session is connected"
  (when (nil? @ssh-session)
    ;; Recreate our session if it doesn't exist or is not connected for some reason
    (let [host (config :sftp-host)
          user (config :username)
          port (config :sftp-port)
          agent (agent-with-identity)
          session (ssh/session agent host {:port port
                                           :username user
                                           :strict-host-key-checking :no})]
      (info "New session created with param: " host user port)
      (reset! ssh-session session)
      (ssh/connect session)))
  ;; seems to me the ssh/connected? seems to return true and then the channel creation fails with "Session not connected"
  ;; Explictely call connect every time the session is requested.
  (reset-session-invalidate)
  @ssh-session)

(defn all-files [path]
  (let [file-type (fn [e] (if (.isDirectory e) "dir" "file"))
        file-size (fn [e] (.length e))]
    (seq (->> (.listFiles (clojure.java.io/file path))
           (filter #(not (.isHidden %1)))
           (map (fn [e] { :name (.getName e)
                          :type (file-type e)
                          :size (file-size e)}))))))

(defn list-remote-files
  "Get the list of all files at the given path"
  [path]
  (let [this-channel (ssh/ssh-sftp (session))]
    (ssh/with-channel-connection this-channel
      (when-not (nil? path)
        (ssh/sftp this-channel {} :cd path))
      (ssh/sftp this-channel {} :ls))))

(defn all-remote-files [path]
  (let [query-path (join-path (config :base-path) path)
        now (quot (.getTime (java.util.Date.)) 1000)
        entries (list-remote-files query-path)
        not-hidden? (fn [e] (not= (.charAt (.getFilename e) 0) \.))
        file-type (fn [e] (if (.isDir (.getAttrs e)) "dir" "file"))
        file-size (fn [e] (.getSize (.getAttrs e)))
        last-modified (fn [e] (-> e (.getAttrs) (.getMTime)))
        recent? (fn [e] (< (- now (last-modified e)) 10))
        ]
    (->> entries 
         (filter not-hidden?)
         (map (fn [e] { :name (.getFilename e) 
                        :type (file-type e)
                        :modified (last-modified e)
                        :recent (recent? e)
                        :size (file-size e)})))))

