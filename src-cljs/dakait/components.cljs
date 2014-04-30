(ns dakait.components
  (:use [dakait.util :only [format-file-size format-date]]
        [clojure.string :only [join split]]
        [jayq.core :only [$ html append css text ajax on bind hide show attr add-class remove-class]]
        [dakait.net :only [http-post]]
        [jayq.util :only [log]])
  (:require [cljs.core.async :as async :refer [>!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn current-path
  "Manages the current path display"
  [path owner {:keys [path-chan] :as opts}]
  (reify
    om/IRender
    (render [this]
      (let [parts (split path #"/")
            paths (->> parts
                       (reduce (fn [acc p]
                                 (cons (cons p (first acc)) acc)) (list))
                       (map #(->> % reverse (join "/")))
                       reverse)
            handler (fn [p] (fn [] (go (>! path-chan p))))]
        (apply dom/h3 #js {:className "current-path"}
               (->> (map (fn [p path] (dom/a #js {:href "#" :onClick (handler path)} p)) parts paths)
                    (interpose " / ")))))))

(defn listing
  "Manages and shows the files listing"
  [{:keys [listing current-path]} owner {:keys [path-chan modal-chan] :as opts}]
  (reify
    om/IRender
    (render [this]
      (apply dom/div #js {:className "listing"}
             (map (fn [l]
                    (let [push-handler (fn [name]
                                         (fn []
                                           (go (>! path-chan (str current-path "/" name)))))
                          tag-handler (fn [name]
                                        (fn []
                                          (log "Requesting tag for " name)
                                          (go (>! modal-chan (str current-path "/" name)))))
                          item-class (str "list-item " (:type l))
                          item-modified (format-date (:modified l))
                          item-size (if (= (:type l) "dir") "" (format-file-size (:size l)))
                          tag-item (let [tag (:tag l)
                                         dl (:download l)
                                         color (if tag (:color tag) "#999")
                                         dlinfo (if dl
                                                  (str (:percent-complete dl) ", " (:rate dl) ", eta: " (:eta dl))
                                                  "")]
                                     (dom/div #js {:className "col-sm-6 list-item-tag"
                                                   :style #js {:color color}}
                                              (when tag (dom/span #js {:className "tag-info"} (:name tag)))
                                              (when dl (dom/span #js {:className "dl-info"} dlinfo))))]
                      (dom/div #js {:className item-class
                                    :key (:name l)}
                        (dom/div #js {:className "row"}
                          (dom/div #js {:className "col-sm-10"}
                            (dom/div #js {:className "row"}
                              (dom/div #js {:className "col-sm-10 list-item-name"}
                                (if (= (:type l) "dir")
                                  (dom/a #js {:className "target-link" :onClick (push-handler (:name l))} (:name l))
                                  (dom/span nil (:name l))))
                              (dom/div #js {:className "col-sm-2 list-item-size"} item-size))
                            (dom/div #js {:className "row subitem"}
                              tag-item
                              (dom/div #js {:className "col-sm-6 list-item-modified"} item-modified)))
                          (dom/div #js {:className "col-sm-2 tag-button-container"}
                            (dom/button #js {:className "btn btn-default btn-lg tag-item-action"
                                             :type "button"
                                             :onClick (tag-handler (:name l)) } "Tag")))
                          (when-let [pc (:percent-complete (:download l))]
                            (dom/div #js {:className "thin-progress"}
                              (dom/div #js {:className "thin-progress-bar"
                                              :style #js {:width pc}}))))))
                  listing)))))

(defn download-activity-monitor
  "A little widget that shows download activity"
  [downloads owner]
  (reify
    om/IRender
    (render [_]
      (let [dls (count (:active downloads))
            pen (count (:pending downloads))
            as-kb (fn [s]
                    (if-let [m (re-find #"(\d+\.?\d*)KB/s" s)]
                      (js/parseFloat (second m))
                      (if-let [m (re-find #"(\d+\.?\d*)MB/s" s)]
                        (* (js/parseFloat (second m)) 1000)
                        0)))
            dl->bw (fn [dl]
                     (if-let [ds (:download-status dl)] (as-kb (:rate ds)) 0))
            tb (reduce #(+ %1 (dl->bw %2)) 0 (:active downloads))
            tb-str (if (< tb 1000)
                     (str (.toFixed tb 2) "KB/s")
                     (str (.toFixed (/ tb 1000) 2) "MB/s"))]

      (dom/div #js {:className "download-monitor pull-right"}
        (when-not (zero? (+ dls pen))
          (dom/div #js {:className "activity-monitor"}
            (dom/div nil (str "Active: " dls " Pending: " pen))
            (dom/div nil tb-str))))))))

(defn sort-order
  "Manages sort order and indicates changes over a channel"
  [[key asc] owner {:keys [sort-order-chan] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [gen-handler (fn [k]
                          (fn []
                            (log "clicked")
                            (go (>! sort-order-chan [k (if (= k key) (not asc) asc)]))))
            grp-button (fn [k title]
                         (dom/a #js {:className "btn btn-default" :role "button" :onClick (gen-handler k)}
                                title
                                " "
                                (when (= k key)
                                  (dom/span #js {:className (str "glyphicon glyphicon-chevron-" (if asc "up" "down"))} ""))))]
        (log key asc)
        (dom/div #js {:className "btn-group btn-group-justified"}
                 (grp-button :name "Name")
                 (grp-button :size "Size")
                 (grp-button :modified "Modified"))))))

(defn- add-tag-form
  "A horizontal add tag form"
  [_ owner {:keys [add-cb] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:ready false})
    om/IDidMount
    (did-mount [_]
      (.focus (om/get-node owner "name")))
    om/IRenderState
    (render-state [_ state]
      (let [values (fn []
                     [(.-value (om/get-node owner "name"))
                      (.-value (om/get-node owner "target"))])
            text-changed (fn []
                           (om/set-state! owner :ready
                                          (every? #(> (count %) 0) (values))))
            trigger-add (fn []
                          (let [[name target] (values)]
                            (set! (.-value (om/get-node owner "name")) "")
                            (set! (.-value (om/get-node owner "target")) "")
                            (.focus (om/get-node owner "name"))
                            (add-cb name target)))
            key-up (fn [e]
                     (when (and (= (.-keyCode e) 13)
                                (om/get-state owner :ready))
                       (trigger-add)))]
        (dom/div #js {:className "panel panel-default"
                      :style #js {:marginTop "10px"}}
          (dom/div #js {:className "panel-body"}
            (dom/input #js {:type "text"
                            :className "form-control"
                            :placeholder "Name"
                            :ref "name"
                            :onChange text-changed
                            :onKeyUp key-up
                            :style #js {:marginBottom "5px"}})
            (dom/input #js {:type "text"
                            :className "form-control"
                            :placeholder "Target Path"
                            :onChange text-changed
                            :ref "target"
                            :onKeyUp key-up
                            :style #js {:marginBottom "5px"}})
            (dom/div #js {:className "row"}
              (dom/div #js {:className "col-md-12"}
                (dom/button #js {:className "btn btn-success btn-block"
                                 :disabled (not (:ready state))
                                 :onClick trigger-add
                                 :style #js {:marginTop "5px"}} "Add")))))))))

(defn tags-modal
  "The tags modal component, allows users to pick a tag to associate with
  a file, also allows them to create a new tag if need be, needs a few channels to make things
  work, a channel to show popup, a channel that recieves the response, and a channel that is notified
  about any new tags being added"
  [tags owner {:keys [modal-chan apply-chan add-chan remove-chan] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:target nil
       :adding false})

    om/IDidMount
    (did-mount [this]
      (go (loop []
            (log "in loop")
            (let [file (<! modal-chan)]
              (log "Setting file " file)
              (om/set-state! owner {:target file})
              (.modal ($ (om/get-node owner)) "show"))
            (recur))))
    om/IRenderState
    (render-state [_ state]
      (let [close-dialog #(.modal ($ (om/get-node owner)) "hide")
            make-handler (fn [name] (fn []
                                      (log "Tag chosen: " name)
                                      (go (>! apply-chan [(:target state) name]))
                                      (close-dialog)))
            remove-handler (fn [name] (fn []
                                        (go (>! remove-chan name))))]
        (dom/div #js {:className "modal fade" :role "dialog"}
          (dom/div #js {:className "modal-dialog modal-sm"}
            (dom/div #js {:className "modal-content"}
              (dom/div #js {:className "modal-header"}
                (dom/button #js {:type "button" :className "close" :onClick close-dialog} "x")
                (dom/h4 #js {:className "modal-title"} "Choose a Tag"))
              (dom/div #js {:className "modal-body"}
                (when (zero? (count tags))
                  (dom/div #js {:className "no-tags"} "There don't seem to be any tags defined, add one!"))
                (apply dom/table #js {:className "all-tags"
                                      :style #js {:width "100%"}}
                  (map #(dom/tr nil
                          (dom/td nil
                            (dom/a #js {:className "tag-item"
                                        :onClick (if (:editing state) (fn[]) (make-handler (:name %)))
                                        :style #js {:backgroundColor (:color %)}}
                                 (:name %)))
                          (when (:editing state)
                            (dom/td #js {:style #js {:width "50px"}}
                              (dom/button #js {:className "btn btn-sm btn-danger col-md-1"
                                               :onClick (remove-handler (:name %))
                                               :style #js {:width "100%" :height "50px"}}
                                (dom/span #js {:className "glyphicon glyphicon-remove"})))))
                         tags))
                  (when (:editing state)
                    (om/build add-tag-form
                              tags ;; we don't really need to pass anything in here, triggers bug
                              {:opts {:add-cb (fn [name path]
                                                (go (>! add-chan [name path])))}})))
              (dom/div #js {:className "modal-footer"}
                (dom/button #js {:className (str "btn" " " (if (:editing state) "btn-danger" "btn-success"))
                                 :onClick (fn [] (om/update-state! owner :editing not))}
                            (dom/span #js {:className "glyphicon glyphicon-edit"})
                            " "
                            (if (:editing state) "Done" "Edit"))
                (dom/button #js {:className "btn btn-defalt btn-info"
                                 :type "button" 
                                 :onClick close-dialog } "Close")))))))))

(defn content-pusher-modal
  "This modal accepts a URL to a resource, downloads the resource and pushes it back to the server to
   a specified location configured on the server side"
  [_ owner {:keys [pusher-modal-chan] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:can-accept false
       :adding false})

    om/IDidMount
    (did-mount [this]
      (.modal ($ (om/get-node owner)) #js {:keyboard false
                                           :show false
                                           :backdrop "static"})
      (go (loop []
            (<! pusher-modal-chan)
            (log "Got request to show pusher modal")
            (.modal ($ (om/get-node owner)) "show")
            (recur))))
    om/IRenderState
    (render-state [_ state]
      (let [close-dialog #(.modal ($ (om/get-node owner)) "hide")
            ;; handler to handle stuff when the user types in or pastes in the URL
            ;;
            change-handler #(let [v (.-value (om/get-node owner "url"))]
                              (log v)
                              (om/set-state! owner :can-accept (> (count v) 0)))
            ;; Handler to handle an upload request
            ;;
            handle-upload (fn []
                            (let [v (.-value (om/get-node owner "url"))]
                              (log "Pushing upload " v)
                              (om/set-state! owner :processing true)
                              (http-post "/a/push" {:url v}
                                         #(close-dialog)
                                         #(do
                                            (om/set-state! owner :error true)
                                            (om/set-state! owner :processing false)))))]
        (dom/div #js {:className "modal fade" :role "dialog"}
          (dom/div #js {:className "modal-dialog modal-sm"}
            (dom/div #js {:className "modal-content"}
              (dom/div #js {:className "modal-body"}
                (dom/input #js {:className "form-control input-lg"
                                :style #js {:margin "20px 0px"}
                                :ref "url"
                                :onChange change-handler
                                :disabled (:processing state)
                                :placeholder "Resource URL"})
                (when (:processing state)
                  (dom/div #js {:className "loader"}))
                (when (:error state)
                  (dom/div #js {:className "alert alert-danger"}
                           "There seems to be a problem pushing your upload, check logs?"))
                (dom/button #js {:className "btn btn-sm btn-success btn-block"
                                 :disabled (or (not (:can-accept state)) (:processing state))
                                 :onClick handle-upload
                                 :style #js {:marginBottom "5px"}} "OK")
                (dom/button #js {:className "btn btn-sm btn-info btn-block"
                                 :disabled (:processing state)
                                 :onClick close-dialog} "Close")))))))))
