(ns dakait.mobile
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ajax.core :refer [GET]]
            [cljs.core.async :refer [put! <! chan]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(def app-state (atom {:files [] :path "."}))

(defn log [& args]
  (->> args
       (map str)
       (interpose " ")
       (apply str)
       (.log js/console)))

(defn update-files [resp app]
  (om/transact! app :files (fn [f] resp)))

(defn file-item [file owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [comm]}]
      (let [name (get file "name")]
        (dom/li #js {:className "table-view-cell"}
          (dom/a #js {:className "navigate-right"}
                 name
                 (dom/button #js {:className "btn btn-link"
                                  :onClick (fn [e] 
                                             (.preventDefault e)
                                             (.stopPropagation e)
                                             (put! comm {:type :tag
                                                               :value name}))}
                    (dom/span #js {:className "icon icon-edit"}))))))))

(defn handle-event [type app value]
  (log "Type: " type ", Value: " value))

(defn file-browser [{:keys [files path] :as app} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [{:keys [type value]} (<! comm)]
                (handle-event type app value)))))
      (GET "/a/files" {:params {:path path}
                       :handler (fn [r]
                                  (update-files r app))}))
    om/IRenderState
    (render-state [_ state]
      (if (seq files)
        (apply dom/ul #js {:className "table-view"}
               (om/build-all file-item (:files app)
                             {:init-state state}))
        (dom/div nil
                 (str "No Files"))))))

(log "Starting om root")
(log "Target: " (.getElementById js/document "file-listing"))
(om/root file-browser app-state
  {:target (.getElementById js/document "file-listing")})

