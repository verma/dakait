;; tags.clj
;; Tags management, no real database is used
;; disk files

(ns dakait.tags
  (:require
    [clojure.java.io :as io]
    [clojure.data.json :as json]))

(def source-file (atom nil))
(def all-tags (atom {}))

(defn- flush-to-disk
  "Flushes the current content of tags to disk"
  []
  (when-not (nil? @source-file)
    (spit @source-file (json/write-str @all-tags))))

(defn load-tags
  "Load tags from source file"
  [file]
  (reset! source-file file)
  (when (.exists (io/as-file file))
    (reset! all-tags (->> file
                          slurp
                          json/read-str))))

(defn get-all-tags
  "Return all tags that we know of"
  []
  @all-tags)

(defn add-tag
  "Add the given tag to the list of tags"
  [name target color]
  (reset! all-tags (assoc @all-tags name {:target target :color color}))
  (flush-to-disk))

(defn find-tag
  "Find the tag with the given name"
  [name]
  (get @all-tags name))

(defn remove-tag
  "Remove the given tag"
  [name]
  (when-not (nil? (find-tag name))
    (reset! all-tags (dissoc @all-tags name))
    (flush-to-disk)))
