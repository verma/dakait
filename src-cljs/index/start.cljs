(ns dakait.index
  (:use [dakait.components :only [current-path listing sort-order download-activity-monitor tags-modal]]
        [dakait.net :only [get-json http-delete http-post]]
        [dakait.downloads :only [start-listening-for-downloads]]
        [clojure.string :only [join split]]
        [jayq.core :only [$ html append css text ajax on bind hide show attr add-class remove-class]]
        [jayq.util :only [log]])
  (:require [cljs.core.async :refer [chan <!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [jayq.macros :refer [ready]]
                   [cljs.core.async.macros :refer [go]]))

(def app-state (atom {:name "Server"
                      :downloads {:active []
                                  :pending []}
                      :current-path "."
                      :listing []
                      :tags [] }))

;; Sort map, each function takes one argument which indicates whether we intend
;; to do an ascending or a descending sort
;;
(def sort-funcs 
  {:name (fn [items asc]
           (sort-by
             #(str (:type %) (:name %))
             (if (true? asc) compare (comp - compare))
             items)) 

   :size (fn [items asc]
           (sort-by
             (if (true? asc)
               :size
               #(- (:size %)))
             items))

   :modified (fn [items asc]
               (sort-by
                 (if (true? asc)
                   :modified
                   #(- (:modified %)))
                 items))
   })

(defn get-files [path files-cb error-cb]
  (log path)
  (get-json "/a/files" {:path path} files-cb error-cb))

(defn tag-attach [path tag done]
  (log "Attaching " tag " to path: " path)
  (http-post "/a/apply-tag" {:tag tag :target path}
             #(done true)
             #(done false)))

(defn get-file-listing
  "Gets the file listing and posts it to the given channel"
  [path scb]
  (get-files path
             scb
             #(log %)))

(defn request-tags
  "Gets the tags available on the server and notified through the given channel"
  [tags-chan]
  (get-json "/a/tags"
            (fn [data]
              (go (>! tags-chan (js->clj data :keywordize-keys true))))
            (fn []
              (log "Failed to load tags"))))

(defn add-tag
  "Add a tag by posting request to remote end"
  [name target scb ecb]
  (http-post "/a/tags" {:name name :target target}
             scb
             ecb))

(defn remove-tag
  "Remote a tag by posting a request"
  [name scb ecb]
  (http-delete (str "/a/tags/" name) scb ecb))

(defn merge-listing
  "Merge all the information about downloads and tags from the two separate sources
  into one listing"
  [listing tags downloads current-path]
  (let [tags (into {} (for [t tags] [(:name t) t]))

        ;; strip out the string from the last . to the end of the path, and return true if it matches current path
        current-path? (fn [p] 
                        (let [last-period (.lastIndexOf p "./")
                              last-slash (.lastIndexOf p "/")
                              path (.substring p last-period last-slash)]
                          (= path current-path)))

        ;; Clean out the path and pick the last component of it
        filename (fn [p]
                   (last (split p #"/")))
        dls (into {} (for [dl (:active downloads) :when (current-path? (:from dl))] [(filename (:from dl)) dl]))
        attach-tag (fn [l]
                     (if-let [tag-name (:tag l)]
                       (assoc l :tag (get tags tag-name))
                       l))
        attach-dl (fn [l]
                    (if-let [dl (get dls (:name l))]
                      (do
                        (assoc l :download (:download-status dl)))
                      l))]
    (->> listing
         ;; Associate tag information
         (map #(->> %
                    attach-tag 
                    attach-dl)))))


(defn full-page
  "Full page om component"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:is-loading false
       :sort-order [:modified false]
       :path-chan (chan)
       :listing-chan (chan)
       :sort-order-chan (chan)
       :tags-chan (chan)
       :downloads-chan (chan)
       :apply-chan (chan)
       :add-chan (chan)
       :remove-chan (chan)
       :modal-chan (chan)})
    om/IWillMount
    (will-mount [_]
      (let [path-chan (om/get-state owner :path-chan)
            sort-order-chan (om/get-state owner :sort-order-chan)
            downloads-chan (om/get-state owner :downloads-chan)
            tags-chan (om/get-state owner :tags-chan)
            apply-chan (om/get-state owner :apply-chan)
            add-chan (om/get-state owner :add-chan)
            remove-chan (om/get-state owner :remove-chan)]
        ;; Start the loop to listen to user clicks on the quick shortcuts
        (go (loop []
              (let [path (<! path-chan)]
                (om/set-state! owner :is-loading path)
                (get-file-listing path
                                  (fn [list]
                                    (om/set-state! owner :is-loading false)
                                    (om/update! app :current-path path)
                                    (om/update! app :listing list)))
                (recur))))
        ;; Start loop for listening to sort requests
        (go (loop []
              (let [new-order (<! sort-order-chan)]
                (om/set-state! owner :sort-order new-order)
                (recur))))
        ;; Start loop for downloads
        (go (loop [info nil]
              (when-not (nil? info)
                (om/update! app :downloads info))
              (recur (<! downloads-chan))))
        ;; Start loop for tags
        (go (loop []
              (let [new-tags (<! tags-chan)]
                (log "Got tags" new-tags)
                (om/update! app :tags new-tags))
              (recur)))
        ;; Start loop for tag applications
        (go (loop []
              (let [[path name] (<! apply-chan)]
                (log "Applying tag " name " to " path)
                (tag-attach path name 
                            (fn [v]
                              (when v
                                (let [fname (last (split path #"/"))]
                                  (om/transact! app :listing
                                                (fn [lst]
                                                  (let [idx (-> (keep-indexed #(if (= fname (:name %2)) %1) lst)
                                                                first)]
                                                    (log idx)
                                                    (assoc-in lst [idx :tag] name)))))))))

              (recur)))
        ;; Add chan
        (go (loop []
              (let [[name path] (<! add-chan)]
                (log "Adding tag " name " with target " path)
                (add-tag name path
                         (fn [d]
                           (om/transact! app :tags
                                         (fn [t]
                                           (vec (cons (js->clj d :keywordize-keys true) t)))))
                         #(log "Failed to add tag!")))
              (recur)))
        ;; go loop for removing tags
        (go (loop []
              (let [tag (<! remove-chan)]
                (log "Removing tag " tag)
                (remove-tag tag
                            (fn []
                              (om/transact! app :tags (fn [ts]
                                                        (vec (remove #(= (:name %) tag) ts)))))
                            #(log "Failed to delete tag: " tag)))
              (recur)))
        ;; queue initial file listing
        (request-tags tags-chan)
        (go (>! path-chan "."))
        ;; start download manager
        (start-listening-for-downloads downloads-chan)))
    om/IRenderState
    (render-state [this state]
      (let [[sort-key sort-asc] (om/get-state owner :sort-order)
            sort-list (fn [listing] ((sort-key sort-funcs) listing sort-asc))]
        (dom/div #js {:className "page"}
          (dom/div #js {:className "clearfix"}
            (dom/h1 #js {:className "name"} (:name app))
            (om/build download-activity-monitor (:downloads app)))
          ;; Setup current path view
          ;;
          (om/build current-path
                    (:current-path app)
                    {:opts {:path-chan (:path-chan state)}})
          ;; Set the sort order view
          ;;
          (om/build sort-order
                    (:sort-order state)
                    {:opts {:sort-order-chan (:sort-order-chan state)}})
          ;; listing view
          ;;
          (om/build listing {:listing (-> (:listing app)
                                          (merge-listing (:tags app) (:downloads app) (:current-path app))
                                          (sort-list))
                             :current-path (:current-path app)}
                             {:opts {:path-chan (:path-chan state)
                                     :modal-chan (:modal-chan state)}})
          ;; setup our tags modal
          ;;
          (om/build tags-modal
                    (:tags app)
                    {:opts {:modal-chan (:modal-chan state)
                            :apply-chan (:apply-chan state)
                            :remove-chan (:remove-chan state)
                            :add-chan (:add-chan state)}})
          ;; when a load is inprogress show the loading indicator
          ;;
          (when (:is-loading state)
            (dom/div #js {:className "dim"}
                     (dom/div #js {:className "loader"} ""))))))))

(defn startup
  "Page initialization"
  []
  (om/root full-page app-state {:target (. js/document (getElementById "app"))}))

(ready (startup))
