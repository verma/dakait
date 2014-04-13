(ns dakait.index
  (:use [dakait.components :only [current-path listing sort-order]]
        [clojure.string :only [join split]]
        [jayq.core :only [$ html append css text ajax on bind hide show attr add-class remove-class]]
        [jayq.util :only [log]])
  (:require [cljs.core.async :refer [chan <!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [jayq.macros :refer [ready]]
                   [cljs.core.async.macros :refer [go]]))

(def hide-timeout (atom nil))
(def tag-store (atom nil))

(def current-sort-key (atom :name))           ;; default sort key is name
(def current-order-is-ascending? (atom true)) ;; default order is ascending

(def current-file-set (atom {}))              ;; current set of files

(def tag-action-handler (atom (fn[])))        ;; What to call when a tag link is attached to

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
  [path listing-chan]
  (get-files path
             #(go (>! listing-chan %))
             #(log %)))

(defn full-page
  "Full page om component"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:name "Server"
       :downloads {:active []
                   :pending []}
       :current-path "."
       :listing []
       :sort-order [:modified false]
       :path-chan (chan)
       :listing-chan (chan)
       :sort-order-chan (chan)})
    om/IWillMount
    (will-mount [_]
      (let [path-chan (om/get-state owner :path-chan)
            listing-chan (om/get-state owner :listing-chan)
            sort-order-chan (om/get-state owner :sort-order-chan)
            path (om/get-state owner :path)]
        ;; Start the loop to listen to user clicks on the quick shortcuts
        (go (loop []
              (let [path (<! path-chan)]
                (log "The user wants to visit: " path)
                (recur))))
        ;; Start the loop to list to responses to listings
        (go (loop []
              (let [listing (<! listing-chan)
                    [key asc] (om/get-state owner :sort-order)]
                (log "Got listing" listing)
                (om/set-state! owner :listing listing)
                (recur))))
        (go (loop []
              (let [new-order (<! sort-order-chan)]
                (log "Changing sort order to", new-order)
                (om/set-state! owner :sort-order new-order)
                (recur))))
        ;; queue initial file listing
        (get-file-listing path listing-chan)))
    om/IRenderState
    (render-state [this state]
      (let [[sort-key sort-asc] (om/get-state owner :sort-order)
            sort-list (fn [listing] ((sort-key sort-funcs) listing sort-asc))]
        (dom/div #js {:className "page"}
          (dom/div #js {:className "clearfix"}
            (dom/h1 #js {:className "name"} (:name state)))
          (om/build current-path (:current-path state)
                    {:opts {:path-chan (:path-chan state)}})
          (om/build sort-order (:sort-order state)
                    {:opts {:sort-order-chan (:sort-order-chan state)}})
          (om/build listing (sort-list (:listing state))))))))

(defn startup
  "Page initialization"
  []
  (om/root full-page {} {:target (. js/document (getElementById "app"))}))

(ready (startup))
