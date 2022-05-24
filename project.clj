(defproject dev.gethop/dashboard-manager.grafana "0.2.7-SNAPSHOT"
  :description "A Duct library for managing dashboards and associated users and organizations in Grafana"
  :url "https://github.com/gethop-dev/dashboard-manager.grafana"
  :min-lein-version "2.9.0"
  :license {:name "Mozilla Public License 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [integrant "0.8.0"]
                 [duct/logger "0.3.0"]
                 [http-kit "2.3.0"]
                 [diehard "0.9.4"]
                 [org.clojure/data.json "1.0.0"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/CLOJARS_USERNAME
                                      :password :env/CLOJARS_PASSWORD
                                      :sign-releases false}]]
  :aliases {"lint-and-test" ["do" ["cljfmt" "check"] "eastwood" ["test" ":all"]]}
  :profiles {:dev [:project/dev :profiles/dev]
             :profiles/dev {}
             :project/dev {:plugins [[jonase/eastwood "0.3.11"]
                                     [lein-cljfmt "0.6.7"]]}
             :repl {:repl-options {:init-ns dev.gethop.dashboard-manager.grafana
                                   :host "0.0.0.0"
                                   :port 4001}}})
