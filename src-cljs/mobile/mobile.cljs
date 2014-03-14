(ns dakait.mobile
    (:require [ajax.core :refer [GET]]
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
    om/IRender
    (render [this]
      (dom/li #js {:className "table-view-cell"}
        (dom/a #js {:className "navigate-right"}
               (get file "name")
               (dom/button #js {:className "btn"}
                  (dom/span #js {:className "icon icon-edit"})))))))

(defn file-browser [{:keys [files path] :as app} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (log "Element mounted, calling API")
      (GET "/a/files" {:params {:path path}
                       :handler (fn [r]
                                  (update-files r app))}))
    om/IRenderState
    (render-state [_ _]
      (if (seq files)
        (apply dom/ul #js {:className "table-view"}
               (om/build-all file-item (:files app)))
        (dom/div nil
                 (str "No Files"))))))

(log "Starting om root")
(log "Target: " (.getElementById js/document "file-listing"))
(om/root file-browser app-state
  {:target (.getElementById js/document "file-listing")})

