;; downloader.clj
;; async sub-service to download stuff
;;

(ns dakait.downloader
  (:use dakait.config
        [dakait.util :only (join-path)]
        [clojure.core.async :only (thread)]
        [clojure.tools.logging :only (info warn error)])
  (:require
    [clj-ssh.ssh :as ssh]
    [clojure.java.io :as io]
    [me.raynes.conch.low-level :as sh]
    [clojure.data.json :as json]))

(def active-downloads (atom []))
(def download-queue (atom (clojure.lang.PersistentQueue/EMPTY)))

;; Download management
;;
(defn- download
  "Download the given file or directory to the given directory"
  [src dest]
  (.mkdirs (io/file dest)) ;; Make sure the destination directory exists
  (let [args (list "scp"
                "-i" (config :private-key) ;; identity file
                "-B" ;; batch run
                "-r" ;; recursive if directory
                "-P" (config :sftp-port) ;; the port to use
                (str (config :username) "@" (config :sftp-host) ":\"" src "\"") ;; source
                dest)] ;; destination)
    [(future (do
               (try
                 (info "Starting process: " args)
                 (let [p (apply sh/proc (map str args))
                       code (sh/exit-code p)]
                   (info "Process ended")
                   (info "stdout:" (sh/stream-to-string p :out))
                   (info "stderr:" (sh/stream-to-string p :err))
                   code)
                 (catch Exception e
                   (info "Download process failed: " (.getMessage e))
                   (.printStackTrace e)
                   0)))) src dest]))

(defn- download-manager
  "Runs in an end-less loop looking for new down load requests and dispatching them"
  [concurrency]
  (info "Starting download manager")
  (thread (while true
            ;; check if we have room for another download, if we do check if we have a task waiting
            ;; if so, start it up
            (try
              (when (< (count @active-downloads) concurrency)
                (let [next-task (peek @download-queue)]
                  (when next-task
                    (reset! download-queue (pop @download-queue))
                    (let [[src dest] next-task
                          p (download src dest)]
                      (info "Process is: " (apply str p))
                      (reset! active-downloads (conj @active-downloads p))))))

              (doseq [task @active-downloads]
                (let [t (first task)]
                  (when (realized? t)
                    (info "Exited code: " @t))))

              ;; Remove any completed futures from our active downloads list
              (reset! active-downloads (remove #(->> % first realized?) @active-downloads))
              (catch Exception e
                (warn "Exception in downloader thread: " (.getMessage e))
                (.printStackTrace e))
              (finally
                (Thread/sleep 1000))))))

(defn downloads-in-progress
  "Gets all active downloads"
  []
  @active-downloads)

(defn downloads-pending
  "Get all the pending downloads as a seq"
  []
  (seq @download-queue))

(defn start-download
  "Start a download for the given file"
  [src dest]
  (info "Queuing download, source: " src ", destination: " dest)
  (reset! download-queue (conj @download-queue [src dest])))

(defn run
  "Run the downloader loop"
  []
  (download-manager (config :concurrency)))
