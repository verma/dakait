(ns dakait.start
  (:use [jayq.core :only [$ html ajax]]
        [jayq.util :only [log]])
  (:use-macros [jayq.macros :only [ready]]))

(defn get-files [path files-cb]
  (.get js/jQuery "/a/files" 
        (fn [d] (files-cb d))))

(defn format-size [n]
  (let [[size postfix] (cond
                        (< n 1000) [n "B"]
                        (< n 1000000) [(/ n 1000) "KB"]
                        (< n 1000000000) [(/ n 1000000.0) "MB"]
                        (< n 1000000000000) [(/ n 1000000000.0) "GB"]
                        (< n 1000000000000000) [(/ n 1000000000000.0) "TB"]
                        :else [n "B"])
         fixedSize (if (< n 1000000) 0 2)]
    (apply str [(.toFixed size fixedSize) " " postfix])))

(defn startup []
  (let 
    [file-size (fn [n] (if (= (.-type n) "file") (format-size (.-size n)) ""))
     to-row (fn [n] (apply str ["<tr><td></td><td>" (.-name n) "</td><td class='size'>" (file-size n) "</td></tr>"]))]
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
