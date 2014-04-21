(defproject dakait "0.1.2"
  :description "A tool to download files from your FTP/SFTP servers in an organized way."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/verma/dakait"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/core.match "0.2.1"]
                 [org.clojure/tools.cli "0.3.1"]
                 [compojure "1.1.6"]
                 [clj-ssh "0.5.7"]
                 [ring/ring-devel "1.2.2"]
                 [ring/ring-core "1.2.2"]
                 [http-kit "2.1.18"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [org.clojure/data.json "0.2.4"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [javax.jmdns/jmdns "3.4.1"]
                 [me.raynes/conch "0.5.1"]
                 [om "0.5.3"]
                 [jayq "2.5.0"]]
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-ancient "0.5.5"]]

  :main dakait.main

  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}
   :uberjar {:main dakait.main, :aot :all
             :dependencies [[javax.servlet/servlet-api "2.5"]]}}
  :hooks [leiningen.cljsbuild]

  :cljsbuild {
    :builds [
             {:id "rel"
              :source-paths ["src-cljs/dakait"] 
              :compiler {:output-to "resources/public/js/index.js"
                         :optimizations :advanced
                         :preamble ["libs/react/react.min.js"
                                    "libs/jquery/jquery-1.10.2.min.js"
                                    "libs/bootstrap/bootstrap.js"]
                         :externs ["libs/react/react.externs.js"
                                   "libs/jquery/jquery-1.10.2.min.js"
                                   "libs/bootstrap/bootstrap.js"]
                         :closure-warnings {:externs-validation :off
                                            :non-standard-jsdoc :off}
                         :pretty-print false}}
             {:id "dev"
              :source-paths ["src-cljs/dakait"] 
              :compiler {:output-to "resources/public/js/index.js"
                         :output-dir "resources/public/js/out"
                         :source-map true
                         :optimizations :none
                         :pretty-print true}}
             ]})
