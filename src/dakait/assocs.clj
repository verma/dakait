;; assocs.clj
;; Association handling
;;

(ns dakait.assocs
  (:use dakait.config
        [dakait.util :only (join-path)]
        [clojure.tools.logging :only (info error)])
  (:require
    [clojure.java.io :as io]
    [clojure.data.json :as json]))


;; File association functions, we need to store current associations for files
;; that the user has tagged so that user may return tags back to the user.
;;
;; The way staging works is this:
;; 1.  The download of a file is indepdent of its tag
;; 2.  The tags may be changed while the file is downloading 
;;     or has been downloaded.
;; 3.  If the file has been downloaded the new tag moves the file to the tag's
;;     location.
;; 4.  If the file is still being downloaded, the tagging operation is just delayed
;;     till the file is downloaded and then staged at its appropriate location based
;;     on the tag.
;;

(def assocs (atom {}))

(def config-file (join-path (config :config-data-dir) "assocs.json"))

(defn load-associations
  "Load associations from our configuration file"
  []
  (info "Associations file: " config-file)
  (when (.exists (io/file config-file))
    (->> config-file
         slurp
         json/read-str
         (reset! assocs))))

(defn- flush-to-disk
  "Write new set of associations to disk"
  [assocs]
  (->> assocs
       json/write-str
       (spit config-file)))

(defn- assoc-key
  "Given a file in the remote file system, append the server info for a unique key"
  [path]
  (str (config :username) "@" (config :sftp-host) ":" path))

(defn add-association
  "Add a new association"
  [tag path]
  (let [key (assoc-key path)]
    (reset! assocs (assoc @assocs key tag)))
  (flush-to-disk @assocs))

(defn get-association
  "Get an already set association, nil otherwise"
  [path]
  (let [key (assoc-key path)]
    (get @assocs key)))
