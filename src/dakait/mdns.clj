(ns dakait.mdns
  (:import (javax.jmdns JmDNS
                        ServiceInfo)))

(def ^{:private true} service (atom nil))

(defn publish-service
  "Register a mDNS service with given information"
  [port]
  (let [service-info (ServiceInfo/create
                       "_dakait._tcp.local."
                       "Dakait"
                       port 
                       "SFTP File download and management utility")
        mdns (JmDNS/create)]
    (.registerService mdns service-info)
    (reset! service mdns)))

