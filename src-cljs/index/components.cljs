(ns dakait.components
  (:use [dakait.util :only [format-file-size format-date]]
        [clojure.string :only [join split]]
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
  [file-list owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/div #js {:className "listing"}
             (map (fn [l]
                    (let [item-class (str "list-item " (:type l))
                          item-modified (format-date (:modified l))
                          item-size (if (= (:type l) "dir") "" (format-file-size (:size l)))]
                      (dom/div #js {:className item-class}
                        (dom/div #js {:className "row"}
                          (dom/div #js {:className "col-sm-10"}
                            (dom/div #js {:className "row"}
                              (dom/div #js {:className "col-sm-10 list-item-name"}
                                (if (= (:type l) "dir")
                                  (dom/a #js {:className "target-list"} (:name l))
                                  (dom/span nil (:name l))))
                              (dom/div #js {:className "col-sm-2 list-item-size"} item-size))
                            (dom/div #js {:className "row subitem"}
                              (dom/div #js {:className "col-sm-6 list-item-tag-button"} "")
                              (dom/div #js {:className "col-sm-6 list-item-modified"} item-modified)))
                          (dom/div #js {:className "col-sm-2 tag-button-container"}
                            (dom/button #js {:className "btn btn-default btn-lg tag-item-action"} "Tag"))))))
                  file-list)))))


(defn sort-order
  "Manages sort order and indicates changes over a channel"
  [order owner {:keys [sort-order-chan] :as opts}]
  (let [[key asc] order
        gen-handler (fn [k]
                      (fn []
                        (log "clicked")
                        (go (>! sort-order-chan [k (if (= k key) (not asc) asc)]))))
        grp-button (fn [k title]
                     (dom/a #js {:className "btn btn-default" :role "button" :onClick (gen-handler k)}
                            title
                            " "
                            (when (= k key)
                              (dom/span #js {:className (str "glyphicon glyphicon-chevron-" (if asc "up" "down"))} ""))))]
    (dom/div #js {:className "btn-group btn-group-justified"}
             (grp-button :name "Name")
             (grp-button :size "Size")
             (grp-button :modified "Modified"))))

                    
