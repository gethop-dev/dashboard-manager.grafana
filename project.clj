(defproject magnet/dashboard-manager.grafana "0.2.4"
  :description "A Duct library for managing dashboards and associated users and organizations in Grafana"
  :url "https://github.com/magnetcoop/dashboard-manager.grafana"
  :min-lein-version "2.8.3"
  :license {:name "Mozilla Public License 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [integrant "0.7.0"]
                 [duct/logger "0.3.0"]
                 [http-kit "2.3.0"]
                 [diehard "0.7.2"]
                 [org.clojure/data.json "0.2.6"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]]
  :profiles {:dev {:plugins [[jonase/eastwood "0.3.4"]
                             [lein-cljfmt "0.6.2"]]}
             :repl {:repl-options {:init-ns magnet.dashboard-manager.grafana
                                   :host "0.0.0.0"
                                   :port 4001}
                    :plugins [[cider/cider-nrepl "0.21.0"]]}})
