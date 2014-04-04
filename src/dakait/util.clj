(ns dakait.util
  (:require
           [clojure.java.io :as io]))

(defn join-path
  "Join path elements together, if any of the path components start with a /
  the function assumes that the path is being reset to root and will ignore all parts
  before that"
 [p & parts]
  (let [p (if (= p "") "." p)]
    (.getPath (reduce #(if (.startsWith %2 "/")
                         (io/file %2)
                         (io/file %1 %2)) (io/file p) parts))))

(defn filename
  "Get the last component from the given path"
  [s]
  (->> (clojure.string/split s #"/")
       (remove empty?)
       last))


(defn map-vals
  "Maps the values of the given given map using the given function"
  [f col]
  (into {} (for [[k v] col] [k (f v)])))

