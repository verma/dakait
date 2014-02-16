(ns dakait.config
    (:require [carica.core :as car]))

(def defaults { :server-name "Server"
                :sftp-port 22
                :username (System/getProperty "user.name")
                :base-path "." })

(defn- make-sure-required-exist []
  "Makes sure that required properties exist"
  (let [required '(:config-data-dir
                    :local-base-path
                    :sftp-host :private-key)]
    (doseq [k required]
      (when (nil? (car/config k))
        (throw (Exception. (str "Required configuration: " (name k))))))))

(defn config [k]
  "Get the associated configuration value, if the config doesn't exist, return the default"
  (let [value (car/config k)]
    (if (nil? value)
      (k defaults)
      value)))

(defn load-and-validate []
  "Load the configuration setting up appropriate defaults and check if we have
  all the things we need"
  (make-sure-required-exist)
  (let [props '(:config-data-dir 
                 :local-base-path
                 :server-name :sftp-host
                 :sftp-port :username :base-path :private-key)]
    (println "Using configuration:")
    (doseq [p props]
      (println (name p) ":" (config p)))))
