;; pusher.clj
;; Helps push stuff on to the server

(ns dakait.pusher
  (:use [dakait.config :only [config]]
        [dakait.util :only [filename join-path]])
  (:require [me.raynes.conch :refer [with-programs]]
            [clj-ssh.ssh :as ssh]
            [org.httpkit.client :as http]))

(defn init-pusher
  "Initilize stuff"
  []
  ;; We need to make sure we have curl available and in path
  )

(defn- push-to-server
  "Push the given file to server"
  [file {:keys [host port path private-key]}]
  (let [agent (ssh/ssh-agent {})]
    (ssh/add-identity agent {:private-key-path private-key})
    (let [session (ssh/session agent host {:strict-host-key-checking :no})]
      (ssh/with-connection session
        (let [channel (ssh/ssh-sftp session)]
          (ssh/with-channel-connection channel
            (ssh/sftp channel {} :cd path)
            (ssh/sftp channel {} :put file (filename file))))))))

(defn- parse-content-disp
  "Parses the content disposition header"
  [header]
  (when-let [m (re-find #"(?i)Filename=\"(.*)\"" header)]
    (second m)))


(defn- download-file
  "Download the given file to the given path, return the fully formed
  path of the file or nil"
  [url path]
  (let [{:keys [status headers body error]} @(http/get url {:user-agent "Dakait"
                                                            :as :byte-array})]
    (if (= status 200)
      (let [filename (if (:content-disposition headers)
                       (parse-content-disp (:content-disposition headers))
                       (filename url))
            out-path (join-path path filename)]
        (with-open [w (clojure.java.io/output-stream out-path)]
          (.write w body))
        out-path)
      nil)))

(defn do-push
  [url]
  ;; First bring down the file to local computer
  (if-let [path (download-file url (config :config-data-dir))]
    (do
      (push-to-server path {:host (config :sftp-host)
                            :port (config :sftp-port)
                            :path (config :push-path)
                            :private-key (config :private-key)})
      (clojure.java.io/delete-file path)
      true)
    false))
