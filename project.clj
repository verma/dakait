(defproject dakait "0.1.0-SNAPSHOT"
  :description "A tool to download files from your FTP/SFTP servers in an organized way."
  :url "https://github.com/verma/dakait"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [org.clojure/data.json "0.2.4"]]
  :plugins [[lein-ring "0.8.10"]]
  :ring {:handler dakait.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [clj-ssh "0.5.7"]
                        [ring-mock "0.1.5"]]}})
