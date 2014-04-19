(ns dakait.downloads
  (:require [cljs.core.async :refer [>!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn start-listening-for-downloads
  "Starts the downloads notifications listener and notifies over the given chan"
  [post-chan]
  (let [uri (str "ws://" (.-host (.-location js/window)) "/ws/downloads")
        ws (js/WebSocket. uri)]
    (doto ws
      (aset "onmessage"
            (fn [data]
              (let [json-obj (.parse js/JSON (.-data data))
                    edn-obj (js->clj json-obj :keywordize-keys true)]
                (go (>! post-chan edn-obj))))))))
