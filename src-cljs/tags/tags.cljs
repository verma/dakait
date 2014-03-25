(ns dakait.tags
  (:use [clojure.string :only [join split blank?]]
        [jayq.core :only [$ html ajax on bind hide show attr add-class remove-class val]]
        [jayq.util :only [log]])
  (:use-macros [jayq.macros :only [ready let-deferred]]))

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
                (error-cb (.-responseJSON e))))))))

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


(defn get-tags [tags-cb error-cb]
  (log "Getting tags")
  (get-json "/a/tags"
            tags-cb
            (fn [e] 
              (error-cb (.-responseJSON e)))))

(defn add-tag-remote
  "Add a tag by posting request to remote end"
  [name target scb ecb]
  (http-post "/a/tags" {:name name :target target}
             scb
             ecb))

(defn remove-tag-remote
  "Remote a tag by posting a request"
  [name scb ecb]
  (http-delete (str "/a/tags/" name) scb ecb))

(defn show-tags [tags]
  (let [content (map 
                  #(str "<tr style='background-color:" (:color %) "'>"
                        "<td>" (:name %) "</td>"
                        "<td class='target'>" (:target %) "</td>"
                        "<td class='delete'><button type='button' class='btn btn-danger' data-tag='" (:name %) "'>"
                        "<span class='glyphicon glyphicon-remove'></span> "
                        "Delete</button></td>"
                        "</tr>")
                  tags)]
    (let [elem ($ ".tags tbody")]
      (html elem "")
      (when-not (nil? (seq content))
        (html elem (str content))))))

(defn load-tags
  "Loads tags by querying the remote server"
  []
  (get-tags show-tags #(log %)))

(defn delete-tag
  "Delete a tag"
  [name]
  (log (str "Deleting " name))
  (remove-tag-remote name
                     #(load-tags)
                     #(load-tags)))

(defn attach-del-handlers
  "Attach handlers for delete command"
  []
  (on ($ ".tags") :click :button
      (fn [e]
        (.preventDefault e)
        (this-as me
                 (delete-tag (.getAttribute me "data-tag"))))))
(defn show-error
  "Show an error in the designated area"
  [msg]
  (html ($ :#add-tag-error) msg))


(defn add-tag
  "Add a new tag given the name and the target, makes sure to show updated results locally and 
  update the remote server accordingly"
  [name target done-cb]
  (log (str "Adding new tag " name " " target))
  (add-tag-remote name target
                  (fn [e]
                    (if (= (.-status e) 1)
                      (do
                        (load-tags)
                        (done-cb true))
                      (done-cb false)))
                  (fn [e]
                    (log e)
                    (done-cb false))))

(defn attach-add-handler
  "Attach handler to handle add events"
  []
  (on ($ :#add-tag-action) :click
      (fn [e]
        (.preventDefault e)
        (let [name (val ($ :#tag-name))
              target (val ($ :#tag-target))]
          (if (or (blank? name) (blank? target))
            (show-error "You need to specify both name and target fields")
            (do
              (show-error "")
              (add-class ($ "#add-button") "loading")
              (add-tag name target
                       (fn [status]
                         (remove-class ($ "#add-button") "loading")
                         (when-not status
                           (show-error "Tag not added"))))))))))

(ready
  (attach-del-handlers)
  (attach-add-handler)
  (load-tags))
