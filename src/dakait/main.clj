(ns dakait.main
  (:gen-class)
  (:use dakait.handler
        [org.httpkit.server :only (run-server)]))

(defn -main [& args]
  "Application entry point"
  (apply do-init args)
  (println "Initialization complete...")
  (run-server app {:port 3000}))

