(ns dakait.index
  (:use
        [clojure.string :only [join split]]
        [jayq.core :only [$ html append css text ajax on bind hide show attr add-class remove-class]]
        [jayq.util :only [log]])
  (:require [crate.core :as crate]
            [reagent.core :as reagent])
  (:use-macros [jayq.macros :only [ready let-deferred]]))

(def current-path (atom []))
(def hide-timeout (atom nil))
(def tag-store (atom nil))

(def current-sort-key (atom :name))           ;; default sort key is name
(def current-order-is-ascending? (atom true)) ;; default order is ascending

(def current-file-set (atom {}))              ;; current set of files

(def tag-action-handler (atom (fn[])))        ;; What to call when a tag link is attached to

(def downloads (reagent/atom {:active [] :pending []}))

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

(defn tag-attach [path tag done]
  (log "Attaching " tag " to path: " path)
  (let [r (.post js/jQuery "/a/apply-tag" (js-obj "tag" tag
                                                  "target" path))]
    (.success r
           #(if (= (.-status %) 1)
              (done true) (done false)))
    (.fail r
           #(done false))))

(defn update-tags-modal
  "Updates the tags modal to show our tags whenever its popped up"
  [tags]
  (let [tags-html (map #(str "<a href='#' class='tag-item' style='background-color:" (.-color %) "'>"
                             (.-name %) "</a>") tags)
        tag-str (apply str tags-html)]
    (html ($ ".all-tags") tag-str)))


(defn update-tags [done-cb]
  (log "querying tags")
  (let [r (.get js/jQuery "/a/tags")]
    (.done r
           (fn [data]
             (reset! tag-store data)
             (update-tags-modal data)
             (done-cb)))
    (.fail r done-cb)))

(defn format-size [n]
  (let [[size postfix] (cond
                        (< n 1000) [n "B"]
                        (< n 1000000) [(/ n 1000) "K"]
                        (< n 1000000000) [(/ n 1000000.0) "M"]
                        (< n 1000000000000) [(/ n 1000000000.0) "G"]
                        (< n 1000000000000000) [(/ n 1000000000000.0) "T"]
                        :else [n "B"])
         fixedSize (if (< n 1000000) 0 1)]
    (apply str [(.toFixed size fixedSize) postfix])))

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
  (html ($ ".listing") ""))

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
       tags-color-map (reduce #(assoc %1 (.-name %2) (.-color %2)) {} @tag-store)
       linked (fn [n]
                (if (= (.-type n) "dir")
                  [:a.target-link {:href "#"} (.-name n)]
                  (.-name n)))
       tagged (fn [n]
                (if (nil? (.-tag n))
                  [:span.list-item-tag]
                  (do
                    (let [tag-name (.-tag n)
                          color (get tags-color-map tag-name)]
                      [:span.list-item-tag {:style (str "color:" color ";")}
                       tag-name]))))
       target (fn [n] (-> (.-name n)
                          (clojure.string/replace "'" "\\'")
                          (clojure.string/replace "\"" "\\\"")))
       to-row (fn [n] (crate/html 
                       [:div {:class (str "list-item " (.-type n)) :target (.-name n)}
                        [:div.row {}
                         [:div.col-sm-10 {}
                          [:div.row {}
                           [:div {:class "col-sm-10 list-item-name"} (linked n)]
                           [:div {:class "col-sm-2 list-item-size"} (file-size n)]
                           ]
                          [:div.row.subitem {}
                           [:div {:class "col-sm-6 list-item-tag-button"} (tagged n)]
                           [:div {:class "col-sm-6 list-item-modified"} (format-date n)]]]
                         [:div {:class "col-sm-2 tag-button-container"}
                          [:button {:class "btn btn-default btn-lg tag-item-action"} "Tag"]]]]))]

      (html ($ ".listing") "")
      (doseq [f files]
        (append ($ ".listing") (to-row f))))))

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
    (update-tags
      #(get-files req-path 
                  (fn [files]
                    (hide-loading-indicator)
                    (show-path path)
                    (reset! current-file-set files)
                    (refresh-view files))
                  (fn [error]
                    (hide-loading-indicator)
                    (show-error (.-message error)))))))


(defn push-path [elem]
  (reset! current-path (conj @current-path elem))
  (load-path @current-path))

(defn reset-path [path]
  (log "resetting path")
  (let [parts (split path #"/")]
    (reset! current-path parts)
    (load-path @current-path)))

(defn attach-click-handler []
  (on ($ :.listing) :click :a.target-link
      (fn [e]
        (.preventDefault e)
        (this-as me
          (let [jme ($ me)
                parent-item (.closest jme ".list-item")
                path (attr parent-item "target")]
            (push-path path))))))

(defn attach-tagref-handler
  "Handle application of tags"
  []
  (on ($ :.listing) :click :.tag-item-action
      (fn [e]
        (.preventDefault e)
        (this-as me
          (let [jme ($ me)
                parent (.closest jme ".list-item")
                path (attr parent "target")
                full-path (conj @current-path path)
                str-path (apply str (interpose "/" full-path))
                span (.find parent ".list-item-tag")]
            (reset! tag-action-handler
                    (fn [tag color]
                      (add-class span "loading")
                      (tag-attach str-path tag
                                  (fn [res]
                                    (remove-class span "loading")
                                    (when (true? res)
                                      (attr span "style" 
                                            (str "color:" color ";"))
                                      (html span tag))))))))
        (.modal ($ "#tagsModal")))))

(defn attach-tag-action-handler
  "Handler for stuff when link on the tag modal is clicked"
  []
  (on ($ "#tagsModal") :click :a.tag-item
      (fn [e]
        (.preventDefault e)
        (this-as me
          (let [jme ($ me)
                tag (text jme)
                color (css jme "background-color")]
            (log "Selected tag: " tag ", color: " color)
            (@tag-action-handler tag color)))
        (.modal ($ "#tagsModal") "hide"))))

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

(defn update-downloads-state
  "Gets the current status of downloads"
  [dls]
  (js/setTimeout
    (fn []
      (let [r (.get js/jQuery "/a/downloads")
            f (fn [d] (js->clj d :keywordize-keys true))]
        (.success r #(reset! dls (f %))))) 1000))

(defn download-item[dl]
  (let [ds (:download-status dl)
        from (:from dl)
        to (:to dl)
        filen (->> (clojure.string/split from #"/")
                   (remove empty?)
                   last)]
    ^{:key from} [:div.download-item nil
                  [:div.title nil filen]
                  (if (nil? ds)
                    [:div.status {} "Waiting..."]
                    [:div.status {}
                     [:div.thin-progress null
                      [:div.thin-progress-bar {:style {:width (:percent-complete ds)}}]]
                     [:div.row {}
                      [:div.col-sm-2 {} (:percent-complete ds)]
                      [:div.col-sm-2 {} (:downloaded ds)]
                      [:div.col-sm-2 {} (:rate ds)]
                      [:div.col-sm-2 {} (:eta ds)]]])
                  [:div.desc {} (str from " -> " to)]]))

(defn downloads-component []
  (update-downloads-state downloads)
  (let [active (:active @downloads)
        pending (:pending @downloads)
        total (+ (count active) (count pending))]
    (if (zero? total)
      [:div.nodownloads nil "No Active Downloads"]
      [:div.current-downloads {}
       [:div.section-title {} "Active"]
       (map download-item active)
       [:div.section-title {} "Pending"]
       (map download-item pending)])))

(defn active-downloads-indicator-component []
  (let [active-count (count (:active @downloads))]
    [:span nil (str "Downloads (" active-count ")")]))

(defn start-downloads-tracker []
  (reagent/render-component [downloads-component]
                            (.getElementById js/document "downloadsBody"))
  (reagent/render-component [active-downloads-indicator-component]
                            (.getElementById js/document "downloadsLink")))

(defn attach-download-viewer
  "Attach a handler to view current downloads"
  []
  (on ($ "body") :click "#downloadsLink"
      (fn [e]
        (.preventDefault e)
        (.modal ($ "#downloadsModal")))))

(defn startup []
  (set-sort :modified false)
  (hide-no-files-indicator)
  (hide-loading-indicator)
  (hide-error)
  (push-path ".")
  (start-downloads-tracker)
  (attach-download-viewer)
  (attach-click-handler)
  (attach-tagref-handler)
  (attach-tag-action-handler)
  (attach-sort-handlers)
  (attach-shortcut-handler))

(ready (startup))
