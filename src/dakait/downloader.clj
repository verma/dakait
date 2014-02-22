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
(def download-states (atom {})) ;; directly modified by download threads

(defn- make-download-command
  "Makes operating system specific script + scp command"
  [src dest tmp-file]
  (let [os (clojure.string/lower-case (System/getProperty "os.name"))
        scp-command (list "scp"
                          "-i" (config :private-key) ;; identity file
                          "-B" ;; batch run
                          "-r" ;; recursive if directory
                          "-o" "StrictHostKeyChecking=no"
                          "-P" (config :sftp-port) ;; the port to use
                          (str (config :username) "@" (config :sftp-host) ":\"" src "\"") ;; source
                          dest)]
    (cond
      (= os "mac os x") (concat (list "script" "-t" "0" "-q" tmp-file)
                                scp-command)
      (= os "linux") (list
                       "script" "-f" "-e" "-q"
                       "-c" (apply str (interpose " " scp-command))
                       tmp-file))))
      

;; Download management
;;
(defn- download
  "Download the given file or directory to the given directory"
  [src dest]
  (.mkdirs (io/file dest)) ;; Make sure the destination directory exists
  (let [tmp-file (.getAbsolutePath (java.io.File/createTempFile "downloader" "txt"))
        args (make-download-command src dest tmp-file)
        update-to-map (fn [s]
                        (when-not (empty? s)
                          (let [parts (remove empty? (clojure.string/split s #"\s"))]
                            (when (= (count parts) 6)
                              {:filename (first parts)
                               :percent-complete (second parts)
                               :downloaded (nth parts 2)
                               :rate (nth parts 3)
                               :eta (nth parts 4)}))))]
    [(future (do
               (try
                 (info "Starting process: " args)
                 (let [p (apply sh/proc (map str args))]
                   (with-open [rdr (io/reader (:out p))]
                     (doseq [line (line-seq rdr)]
                       (let [state-map (update-to-map line)]
                         (info "source key: " src)
                         (info "download state map: " state-map)
                         (reset! download-states 
                                 (assoc @download-states src state-map)))))
                   (info "Process ended")
                   (sh/exit-code p))
                 (catch Exception e
                   (info "Download process failed: " (.getMessage e))
                   (.printStackTrace e)
                   0)
                 (finally
                   (io/delete-file tmp-file true))))) src dest]))

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
  (map (fn [[fut src dest]]
         {:from src
          :to dest
          :download-status (get @download-states src)}) @active-downloads))

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
