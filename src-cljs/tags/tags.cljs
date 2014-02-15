(ns dakait.tags
  (:use [clojure.string :only [join split]]
        [jayq.core :only [$ html ajax on bind hide show attr add-class]]
        [jayq.util :only [log]])
  (:use-macros [jayq.macros :only [ready let-deferred]]))

(defn get-tags [tags-cb error-cb]
  (log "Getting tags")
  (let [r (.get js/jQuery "/a/tags")]
    (.done r tags-cb)
    (.fail r 
           (fn [e] 
             (error-cb (.-responseJSON e))))))

(defn show-tags [tags]
  (let [content (str
                  (map 
                    #(str "<tr style='background-color:" (.-color %) "'>"
                          "<td>" (.-name %) "</td>"
                          "<td class='target'>" (.-target %) "</td>"
                          "<td class='delete'><button type='button' class='btn btn-danger' data-tag='" (.-name %) "'>Delete</button></td>"
                          "</tr>")
                    tags))]
    (html ($ ".tags tbody") (str content))))

(defn delete-tag
  "Delete a tag"
  [name]
  (log (str "Deleting " name)))

(defn attach-del-handlers
  "Attach handlers for delete command"
  []
  (on ($ ".tags") :click :button
      (fn [e]
        (.preventDefault e)
        (this-as me
                 (delete-tag (.getAttribute me "data-tag"))))))

(ready
  (attach-del-handlers)
  (get-tags show-tags #(log %)))
