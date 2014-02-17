(ns dakait.util
  (:require
           [clojure.java.io :as io]))

(defn join-path
  "Join path elements together"
  [& parts]
  (.getPath (reduce #(io/file %1 %2) parts)))

