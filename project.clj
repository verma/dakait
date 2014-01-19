(defproject dakait "0.1.0-SNAPSHOT"
  :description "A tool to download files from your FTP/SFTP servers in an organized way."
  :url "https://github.com/verma/dakait"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [org.clojure/data.json "0.2.4"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [jayq "2.5.0"]]
  :plugins [[lein-ring "0.8.10"]
            [lein-cljsbuild "1.0.1"]]
  :ring {:handler dakait.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [clj-ssh "0.5.7"]
                        [ring-mock "0.1.5"]]}}
  :hooks [leiningen.cljsbuild]

  :cljsbuild {
    :builds [{:source-paths ["src-cljs"] 
              :compiler {:output-to "resources/public/js/main.js"
                         :optimizations :whitespace
                         :pretty-print true}}]})
