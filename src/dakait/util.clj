(ns dakait.util
  (:require
           [clojure.java.io :as io]))

(defn join-path
  "Join path elements together"
  [& parts]
  (.getPath (reduce #(io/file %1 %2) parts)))

(defn filename
  "Get the last component from the given path"
  [s]
  (->> (clojure.string/split s #"/")
       (remove empty?)
       last))

(defn supported-portable?
  "Checks if the user agent string is one of the portable devices we support iPad, iPhone"
  [ua]
  (or (re-find #"iPad" ua)
      (re-find #"iPhone" ua)))



