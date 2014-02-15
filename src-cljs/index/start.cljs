(ns dakait.index
  (:use [clojure.string :only [join split]]
        [jayq.core :only [$ html ajax on bind hide show attr add-class]]
        [jayq.util :only [log]])
  (:use-macros [jayq.macros :only [ready let-deferred]]))

(def current-path (atom []))
(def hide-timeout (atom nil))

(def current-sort-key (atom :name))           ;; default sort key is name
(def current-order-is-ascending? (atom true)) ;; default order is ascending

(def current-file-set (atom {}))              ;; current set of files

;; Sort map, each function takes one argument which indicates whether we intend
;; to do an ascending or a descending sort
;;
(def sort-funcs 
  {:name (fn [items asc]
           (sort-by
             #(str (.-type %) (.-name %))
             (if (true? asc) compare (comp - compare))
             items)) 

   :size (fn [items asc]
           (sort-by
             (if (true? asc)
               #(.-size %)
               #(- (.-size %)))
             items))

   :modified (fn [items asc]
               (sort-by
                 (if (true? asc)
                   #(.-modified %)
                   #(- (.-modified %)))
                 items))
   })

(defn get-files [path files-cb error-cb]
  (log path)
  (let [r (.get js/jQuery "/a/files" (js-obj "path" path))]
    (.done r files-cb)
    (.fail r 
           (fn [e] 
             (error-cb (.-responseJSON e))))))

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

(defn sort-files [files]
  (let [f (@current-sort-key sort-funcs)]
    (f files @current-order-is-ascending?)))

(defn show-loading-indicator []
  (let [to (.setTimeout js/window
             (fn [] 
               (reset! hide-timeout nil)
               (.log js/console "Timeout called, showing loader")
               (show ($ :#loader))) 100)]
    (reset! hide-timeout to)))

(defn hide-loading-indicator []
  (when-not (nil? @hide-timeout)
    (.clearTimeout js/window @hide-timeout)
    (reset! hide-timeout nil))
  (hide ($ :#loader)))

(defn show-no-files-indicator []
  (show ($ :.no-files))
  )

(defn hide-no-files-indicator []
  (hide ($ :.no-files))
  )

(defn hide-error []
  (hide ($ :#error)))

(defn show-error [message]
  (html ($ "#error .message") (str "Sorry, there was an error processing your request:<br><br>" message))
  (show ($ "#error")))

(defn clear-listing []
  (html ($ "#file-list tbody") ""))

(defn format-date [n]
  (let [dt (* (.-modified n) 1000)
        now (.getTime (js/Date.))
        diffInSecs (quot (- now dt) 1000)
        diffInHours (quot diffInSecs 3600)]
    (log (.-name n) diffInSecs " " diffInHours)
    (cond
      (< diffInHours 1) "Less than an hour ago"
      (< diffInHours 2) "An hour ago"
      (< diffInHours 24) (str diffInHours " hours ago")
      (< diffInHours 48) "A day ago"
      (< diffInHours 168) (str (quot diffInHours 24) " days ago")
      :else (.toDateString (js/Date. dt)))))

(defn show-files [files]
  (if (= (count files) 0)
    (do
      (clear-listing)
      (show-no-files-indicator))
    (let 
      [file-size (fn [n] (if (= (.-type n) "file") (format-size (.-size n)) ""))
       klass (fn [n] (.-type n))
       target (fn [n] (.-name n))
       to-row (fn [n] (apply str ["<tr class=\"" (klass n) "\" target=\"" (target n) "\"><td>"
                                  (.-name n) "</td><td class='size'>"
                                  (file-size n) "</td><td class='last-modified'>"
                                  (format-date n) "</td></tr>"]))
       html-content (apply str (map to-row files))]
      (html ($ "#file-list tbody") html-content))))

(defn make-path [elems]
  (join "/" elems))

(defn make-link [parts]
  (let [link (make-path parts)]
    (apply str ["<a href=\"" link "\">" (last parts) "</a>"])))

(defn show-path [elems]
  (let [link-parts (reduce 
                     (fn [acc e] 
                       (conj acc (conj (last acc) e)))
                     [[(first elems)]]
                     (rest elems))
        path-string (join "/" (map make-link link-parts))]
    (html ($ :.current-path) path-string)))

(defn show-ui-sort-indicators []
  (let [sort-key (name @current-sort-key)
        sort-asc @current-order-is-ascending?
        sort-fields ["#sort-name" "#sort-size" "#sort-modified"]]
    ;; first clear all classes
    (doseq [f sort-fields]
      (attr ($ f) :class ""))
    ;; now apply class to the new element
    (add-class
      ($ (str "#sort-" sort-key))
      (if (true? sort-asc)
        "glyphicon glyphicon-chevron-down"
        "glyphicon glyphicon-chevron-up"))))

(defn refresh-view [files]
  (->> files
       sort-files
       show-files))

(defn set-sort [type asc]
  (reset! current-sort-key type)
  (reset! current-order-is-ascending? asc)
  (show-ui-sort-indicators)
  (when-not (zero? (count @current-file-set))
    (refresh-view @current-file-set)))

(defn push-sort-order
  "Push a new state onto the sort order state machine"
  [type]
  (let [sk @current-sort-key
        asc @current-order-is-ascending?]
    (cond
      (= sk type) (set-sort type (not asc))
      :else (set-sort type true))))


(defn load-path [path]
  (let [req-path (join "/" path)]
    (hide-no-files-indicator)
    (show-loading-indicator)
    (get-files req-path 
               (fn [files]
                 (hide-loading-indicator)
                 (show-path path)
                 (reset! current-file-set files)
                 (refresh-view files))
               (fn [error]
                 (hide-loading-indicator)
                 (show-error (.-message error))))))


(defn push-path [elem]
  (reset! current-path (conj @current-path elem))
  (load-path @current-path))

(defn reset-path [path]
  (log "resetting path")
  (let [parts (split path #"/")]
    (reset! current-path parts)
    (load-path @current-path)))

(defn attach-click-handler []
  (on ($ :table) :click :.dir
      (fn [e]
        (.preventDefault e)
        (this-as me
          (push-path (.getAttribute me "target"))))))

(defn attach-shortcut-handler []
  (on ($ :.current-path) :click :a
      (fn [e]
        (.preventDefault e)
        (this-as me
          (reset-path (.getAttribute me "href"))))))

(defn attach-sort-handlers []
  (doseq [n [:name :size :modified]]
    (on ($ (str "#action-sort-" (name n))) :click
        (fn [e]
          (.preventDefault e)
          (push-sort-order n)))))

(defn startup []
  (set-sort :name true)
  (hide-no-files-indicator)
  (hide-loading-indicator)
  (hide-error)
  (push-path ".")
  (attach-click-handler)
  (attach-sort-handlers)
  (attach-shortcut-handler))

(ready (startup))
