;; downloader.clj
;; async sub-service to download stuff
;;

(ns dakait.downloader
  (:use dakait.config
        [dakait.util :only (join-path)]
        [clojure.core.match :only (match)]
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

(defn- escape-for-commandline
  "Replace all spaces in string with command line escaped spaces (with slash before every space)
  based on an answer here: http://stackoverflow.com/a/20053121/73544 best thing to do is to escape
  all the things"
  [s]
  (->> s
    (map #(if (Character/isLetterOrDigit %)
             (str %)
             (str "\\" %)))
    (apply str)))

(defn- os-proof
  "Operating system specific filename formatting, os x accepts the scp commands as a list
  whereas linux accepts it as a single argument, we need to quote dest for linux in case there are
  spaces in the destination since its passed to the shell as it is"
  [s]
  (let [os (clojure.string/lower-case (System/getProperty "os.name"))]
    (cond
      (= os "mac os x") s
      (= os "linux") (escape-for-commandline s)
      :else (throw (Exception. "scp handling is not implemented for this os")))))


(defn- make-download-command
  "Makes operating system specific script + scp command"
  [src dest tmp-file]
  (let [os (clojure.string/lower-case (System/getProperty "os.name"))
        os-src (os-proof src)
        os-dest (os-proof dest)
        scp-command (list "scp"
                          "-i" (config :private-key) ;; identity file
                          (if (config :use-ipv6) "-6" "-4") ;; use appropriate flag to force IP version selection
                          "-B" ;; batch run
                          "-r" ;; recursive if directory
                          "-o" "StrictHostKeyChecking=no"
                          "-P" (config :sftp-port) ;; the port to use
                          (str (config :username) "@" (config :sftp-host) ":\"" os-src "\"") ;; source
                          os-dest)]
    (info "making download command, src: " os-src ", dest: " os-dest)
    (cond
      (= os "mac os x") (concat (list "script" "-t" "0" "-q" tmp-file)
                                scp-command)
      (= os "linux") (list
                       "script" "-f" "-e" "-q"
                       "-c" (apply str (interpose " " scp-command))
                       tmp-file)
      :else (throw (Exception. "scp handling is not implemented for this os")))))
      

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
                          (let [parts (reverse (remove empty? (clojure.string/split s #"\s")))]
                            (when (= (first parts) "ETA")
                              (let [[eta rate dl pc & junk] (rest parts)]
                                    {:percent-complete pc
                                     :downloaded dl
                                     :rate rate
                                     :eta eta})))))]
                              ;; scp command shows ETA as the last thing when downloading, but doesn't when done.
                              ;; make sure to drop the first element when its ETA, we reverese the components to make
                              ;; it easier on us to destruct them later
    [(future (do
               (try
                 (info "Starting process: " args)
                 (let [p (apply sh/proc (map str args))]
                   (with-open [rdr (io/reader (:out p))]
                     (doseq [line (line-seq rdr)]
                       (let [state-map (update-to-map line)]
                         (swap! download-states assoc src state-map))))
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
              ; start any new tasks that need to be started if we have room
              ;
              (when (< (count @active-downloads) concurrency)
                (when-let [next-task (peek @download-queue)]
                  (swap! download-queue pop)
                  (let [[src dest f] next-task
                        p (download src dest)]
                    (info "Process is: " (apply str p))
                    ; false indicates that the user has not been notified about this completion yet
                    (let [new-dl-state (conj (vec p) f false)] 
                      (swap! active-downloads conj new-dl-state)))))

              ; call callbacks on any completed but not triggered tasks
              ;
              (swap! active-downloads
                     (fn [dls]
                       (map #(match [%]
                                    [[(t :guard realized?) s d cb false]] (do
                                                                            (cb (= @t 0))
                                                                            [t s d cb true])
                                    :else %) dls)))

              ; Remove any completed futures from our active downloads list
              ;
              (swap! active-downloads #(remove last %))

              (catch Exception e
                (warn "Exception in downloader thread: " (.getMessage e))
                (.printStackTrace e))
              (finally
                (Thread/sleep 1000))))))

(defn downloads-in-progress
  "Gets all active downloads"
  []
  (map (fn [[fut src dest & rest]]
         {:from src
          :to dest
          :download-status (get @download-states src)}) @active-downloads))

(defn downloads-pending
  "Get all the pending downloads as a seq"
  []
  (seq @download-queue))

(defn start-download
  "Start a download for the given file"
  ([src dest]
   (start-download src dest (fn [s])))
  ([src dest f]
   (info "Queuing download, source: " src ", destination: " dest)
   (swap! download-queue conj [src dest f])))

(defn run
  "Run the downloader loop"
  []
  (download-manager (config :concurrency)))
