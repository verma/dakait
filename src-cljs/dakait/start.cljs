(ns dakait.start
  (:use [jayq.core :only [$ html ajax]]
        [jayq.util :only [log]])
  (:use-macros [jayq.macros :only [ready]]))

(defn get-files [path files-cb]
  (.get js/jQuery "/a/files" 
        (fn [d] (files-cb d))))


(defn startup []
  (let 
    [to-row (fn [n] (apply str ["<tr><td></td><td>" (.-name n) "</td></tr>"]))]
    (get-files "." 
      (fn [f]
        (let 
          [html-content (apply str (map to-row f))]
          (log html-content)
          (log $fl)
          (html ($ "#file-list tbody") html-content))))
    )
  )


(ready (startup))
