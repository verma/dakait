;; staging.clj
;; Staging support, all downloads are downloaded to the configured staging area, when the download
;; completes, the files are moved to the destination path, if a file is already in the destination area
;; and was successfully downloaded, this module also helps locate it and help move it to another destination
;; path
;;

(ns dakait.staging
  (:use dakait.config
        [dakait.util :only (join-path map-vals filename)]
        [clojure.core.match :only (match)]
        [dakait.downloader :only (start-download)]
        [clojure.tools.logging :only (info error)])
  (:require
    [me.raynes.conch :refer [with-programs]]
    [clojure.walk :refer [keywordize-keys]]
    [clojure.java.io :as io]
    [clojure.data.json :as json]))


(def staged (atom {}))
(def staged-info-file (atom nil)) 

(defn- flush-to-disk
  "Write the current state of staged information to disk"
  []
  (->> @staged
       json/write-str
       (spit @staged-info-file)))

(defn- read-from-disk
  "Read current state from disk"
  []
  (when (.exists (io/file @staged-info-file))
    (->> @staged-info-file
         slurp
         json/read-str
         (map-vals #(let [m (keywordize-keys %)]        ;; All keys need to be keywordized, and the :download-state's value needs to be keywordized
                      (assoc m :download-state (keyword (:download-state m))))))))


(defn init-stager
  "Initializes the stager"
  []
  (reset! staged-info-file (join-path (config :config-data-dir) "staging.json"))
  (reset! staged (read-from-disk)))


(defn- move-to-dir
  "Move file from given source to given destination"
  [src dest]
  (.mkdirs (io/file dest))
  (with-programs [mv]
    (info "Moving " src " -> " dest)
    (mv src dest {:verbose true})))

(defn stage-file
  "Helps stage the file, triggers download of the file to the staged configuration area, once the
   download finishes the file is moved to appropriate area"
  [target-path dest-path]
  ;; Depending on what our download state is we do a few things
  ;; check the file download status
  (match [(get-in @staged [target-path :download-state])]
    [:downloaded]
         (do
           (info "The file has been downloaded already, so will move it")
           (let [filen (filename target-path)
                 src (join-path (get-in @staged [target-path :dest]) filen)]
             (move-to-dir src dest-path)
             (swap! staged assoc-in [target-path :dest] dest-path)))
    [:downloading]
         (do
           (info "The file is still in progress, just updating path")
           (swap! staged assoc-in [target-path :dest]  dest-path))
    [nil]
         (do
           (info "There is no download state, start one")
           (start-download target-path (config :staging-dir)
                           (fn [code]
                             (if code ;; this file was successfully downloaded, move the file to destination
                               (let [info (get @staged target-path)]
                                 (move-to-dir (join-path (config :staging-dir) (filename target-path)) (:dest info))
                                 (swap! staged assoc-in [target-path :download-state] :downloaded))
                               (swap! staged assoc-in [target-path :download-state] nil)) ;; don't set any state, back to nil so the download could be re-triggered
                             (flush-to-disk)))
           (swap! staged assoc target-path {:download-state :downloading
                                            :dest dest-path})))

  ;; Finally flush state to disk
  (flush-to-disk))
