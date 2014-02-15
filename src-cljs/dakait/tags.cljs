(ns dakait.tags
  (:use [clojure.string :only [join split]]
        [jayq.core :only [$ html ajax on bind hide show attr add-class]]
        [jayq.util :only [log]])
  (:use-macros [jayq.macros :only [ready let-deferred]]))

(defn get-tags []
  (log "Getting tags")
  (let [r (.get js/jQuery "/a/tags")]
    (.done r -cb)
    (.fail r 
           (fn [e] 
             (error-cb (.-responseJSON e))))))

