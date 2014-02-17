;; downloader.clj
;; async sub-service to download stuff
;;

(ns dakait.downloader
  (:use dakait.config
        [dakait.util :only (join-path)]
        [clojure.core.match :only (match)]
        [clojure.core.async :only (chan thread <!!)]
        [clojure.tools.logging :only (info error)])
  (:require
    [clojure.java.io :as io]
    [clojure.data.json :as json]))

;; Channel processing
;;
(defn- process-message
  "Process a recieved message"
  [msg]
  (match msg
    [:get from to] (do
                     (info "Getting file " from " -> " to))
    :else (error "Not sure what command was sent to me")))

(def channel (chan))

(defn run-with-channel
  "Run the listener loop with the given channel"
  [channel]
  (thread (while true
            (process-message (<!! channel)))))

(defn run []
  "Run the downloader loop"
  (run-with-channel channel))
