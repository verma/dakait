;; net utilities
;;
(ns dakait.net)

(defn get-json
  "Sends a get request to the server and gets back with data already EDNed"
  ([path response-cb error-cb]
   (get-json path {} response-cb error-cb))
  ([path params response-cb error-cb]
   (let [r (.get js/jQuery path (clj->js params))]
     (doto r
       (.done (fn [data]
                (response-cb (js->clj data :keywordize-keys true))))
       (.fail (fn [e]
                (error-cb (js->clj (.-responseJSON e) :keywordize-keys true))))))))

(defn http-post
  "Post an HTTP request"
  [path params scb ecb]
  (let [r (.post js/jQuery path (clj->js params))]
    (doto r
      (.success scb)
      (.fail ecb))))

(defn http-delete
  "Post an HTTP delete request"
  [url scb ecb]
  (let [r (.ajax js/jQuery
                 (js-obj "url" url
                         "type" "DELETE"))]
    (doto r
      (.success scb)
      (.fail ecb))))
