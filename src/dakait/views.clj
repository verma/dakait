(ns dakait.views
  (:use 
    dakait.config
    [clojure.java.io :only (resource)]
    [clostache.parser :only (render)]))

(defn render-template [file data]
  (render (slurp file) data))

(defn render-resource [file data]
  (-> file
      resource
      slurp
      (render data)))

(defn index-page []
  (render-resource "templates/index.mustache" {:title "Hello"
                                               :server-name (config :server-name) }))
