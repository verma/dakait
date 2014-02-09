(ns dakait.views
  (:use 
    dakait.config
    [clostache.parser :only (render)]))

(defn render-template [file data]
  (render (slurp file) data))

(defn index-page []
  (render-template "templates/index.mustache" {:title "Hello"
                                               :server-name (config :server-name) }))
