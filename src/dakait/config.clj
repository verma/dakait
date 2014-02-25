(ns dakait.config
  (:use [dakait.util :only [join-path]]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.data.json :as json]))

(def loaded-config (atom {}))
(def config-file (join-path (System/getProperty "user.dir") "config.json"))
(def defaults { :server-name "Server"
                :sftp-port 22
                :concurrency 4
                :username (System/getProperty "user.name")
                :base-path "." })

(defn config [k]
  "Get the associated configuration value, if the config doesn't exist, return the default"
  (get @loaded-config k (k defaults)))

(defn- make-sure-required-exist []
  "Makes sure that required properties exist"
  (let [required '(:config-data-dir
                    :local-base-path
                    :sftp-host :private-key)]
    (doseq [k required]
      (when (nil? (config k))
        (throw (Exception. (str "Required configuration: " (name k))))))))


(defn load-and-validate []
  "Load the configuration setting up appropriate defaults and check if we have
  all the things we need"
  (reset! loaded-config
          (->> config-file
               slurp
               json/read-str
               keywordize-keys))

  (make-sure-required-exist)
  (let [props '(:config-data-dir 
                 :local-base-path
                 :concurrency
                 :server-name :sftp-host
                 :sftp-port :username :base-path :private-key)]
    (println "Using configuration: " config-file)
    (doseq [p props]
      (println (name p) ":" (config p)))))
