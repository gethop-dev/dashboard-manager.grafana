;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.dashboard-manager.grafana
  (:require
   [clojure.data.json :as json]
   [diehard.core :as dh]
   [magnet.dashboard-manager.core :as core]
   [integrant.core :as ig]
   [org.httpkit.client :as http]))

(def ^:const default-timeout
  "Default timeout value for an connection attempt with grafana."
  200)

(def ^:const default-max-retries
  "Default limit of attempts for grafana request."
  10)

(def ^:const default-initial-delay
  "Initial delay for retries, specified in milliseconds."
  500)

(def ^:const default-max-delay
  "Maximun delay for a connection retry, specified in milliseconds. We
  are using truncated binary exponential backoff, with `max-delay` as
  the ceiling for the retry delay."
  1000)

(def ^:const default-backoff-ms
  [default-initial-delay default-max-delay 2.0])

(def ^:const gateway-timeout
  "504 Gateway timeout The server, while acting as a gateway or proxy,
  did not receive a timely response from the upstream server specified
  by the URI (e.g. HTTP, FTP, LDAP) or some other auxiliary
  server (e.g. DNS) it needed to access in attempting to complete the
  request."
  504)

(def ^:const bad-gateway
  "502 Bad gateway The server, while acting as a gateway or proxy,
  received an invalid response from the upstream server it accessed in
  attempting to fulfill the request."
  502)

(defn- fallback [value exception]
  (let [status (condp instance? exception
                 ;; Socket layer related exceptions
                 java.net.UnknownHostException :unknown-host
                 java.net.ConnectException :connection-refused
                 ;; HTTP layer related exceptions
                 org.httpkit.client.TimeoutException gateway-timeout
                 org.httpkit.client.AbortException bad-gateway)]
    {:status status}))

(defn- retry-policy [max-retries backoff-ms]
  (dh/retry-policy-from-config
   {:max-retries max-retries
    :backoff-ms backoff-ms
    :retry-on [org.httpkit.client.TimeoutException
               org.httpkit.client.AbortException]}))

(defn- default-status-codes [code]
  (cond
    (keyword? code) code
    (and (>= code 200) (< code 300)) :ok
    (or (= code 401) (= code 403)) :access-denied
    (= code 404) :not-found
    :else :error))

(defn do-request [{:keys [uri credentials timeout max-retries backoff-ms]} req-args]
  (let [req (assoc req-args
                   :url (str uri (:url req-args))
                   :basic-auth credentials
                   :timeout timeout)]
    (dh/with-retry {:policy (retry-policy max-retries backoff-ms)
                    :fallback fallback}
      (let [{:keys [status body error] :as resp} @(http/request req)]
        (when error
          (throw error))
        (try
          {:status status
           :body (json/read-str body :key-fn keyword :eof-error? false)}
          (catch Exception e
            {:status bad-gateway}))))))

(defn switch-org [gf-record org-id]
  (let [{:keys [status body]} (do-request gf-record {:url (str "/api/user/using/" org-id)
                                                     :method :post
                                                     :headers {"Content-Type" "application/json"}})]
    {:status (case status
               401 :not-found
               (default-status-codes status))}))

(defn gf-get-current-ds-panels [gf-record dashboard-uid]
  (let [{:keys [status body]} (do-request gf-record  {:url (str "/api/dashboards/uid/" dashboard-uid)})
        panels (-> body :dashboard :panels)
        ds-url (-> body :meta :url)]
    {:status (default-status-codes status)
     :panels (map #(-> %
                       (select-keys [:id :title])
                       (assoc :ds-url ds-url))
                  panels)}))

(defn gf-get-current-org-panels [gf-record]
  (let [{:keys [status body]} (do-request gf-record  {:url "/api/search?"})]
    {:status (default-status-codes status)
     :panels (->> body
                  (map #(select-keys % [:url :uid]))
                  (reduce (fn [reduced {:keys [uid url]}]
                            (let [panels (:panels (gf-get-current-ds-panels gf-record uid))]
                              (concat reduced panels)))
                          []))}))

(defn gf-get-current-dashboards [gf-record]
  (let [{:keys [status body]} (do-request gf-record {:url "/api/search?"})]
    {:status (default-status-codes status)
     :dashboards (->> body
                      (map #(select-keys % [:uid :title :url]))
                      (map #(assoc % :panels (:panels (gf-get-current-ds-panels gf-record (:uid %))))))}))

(defn with-org [gf-record org-id f & args]
  (let [{:keys [status]} (switch-org gf-record org-id)]
    (if (= :ok status)
      (apply f gf-record args)
      {:status status})))

(defn gf-get-orgs [gf-record]
  (let [{:keys [status body]} (do-request gf-record  {:method :get
                                                      :url "/api/orgs"})]
    {:status (default-status-codes status)
     :orgs body}))


(defn gf-create-org [gf-record org-name]
  (let [{:keys [status body]} (do-request gf-record  {:method :post
                                                      :url "/api/orgs"
                                                      :headers {"Content-Type" "application/json"}
                                                      :body (json/write-str {:name org-name})})]
    {:status (case status
               409 :already-exists
               (default-status-codes status))
     :id (:orgId body)}))

(defn gf-delete-org [gf-record org-id]
  (let [{:keys [status body]} (do-request gf-record  {:method :delete
                                                      :url (str "/api/orgs/" org-id)})]
    {:status (default-status-codes status)}))

(defn gf-update-org [gf-record org-id new-org-name]
  (let [{:keys [status body]} (do-request gf-record  {:method :put
                                                      :url (str "/api/orgs/" org-id)
                                                      :headers {"Content-Type" "application/json"}
                                                      :body (json/write-str {:name new-org-name})})]
    {:status (case status
               400 :already-exists
               (default-status-codes status))}))

(defn gf-add-org-user [gf-record org-id login-name role]
  (let [{:keys [status body]} (do-request gf-record  {:method :post
                                                      :url (str "/api/orgs/" org-id "/users")
                                                      :headers {"Content-Type" "application/json"}
                                                      :body (json/write-str {:loginOrEmail login-name
                                                                             :role role})})]
    {:status (case status
               400 :role-not-found
               404 :user-not-found
               409 :already-exists
               (default-status-codes status))}))

(defn gf-get-org-users [gf-record org-id]
  (let [{:keys [status body]} (do-request gf-record  {:method :get
                                                      :url (str "/api/orgs/" org-id "/users")})]
    {:status (if (and (= 200 status) (empty? body))
               :not-found
               (default-status-codes status))
     :users body}))

(defn gf-create-user [gf-record user-data]
  (let [{:keys [status body]} (do-request gf-record  {:method :post
                                                      :url "/api/admin/users/"
                                                      :headers {"Content-Type" "application/json"}
                                                      :body (json/write-str user-data)})]
    {:status (case status
               409 :already-exists
               400 :invalid-data
               (default-status-codes status))
     :id (:id body)}))

(defn gf-update-user [gf-record id changes]
  (let [{:keys [status body]} (do-request gf-record  {:method :put
                                                      :url (str "/api/users/" id)
                                                      :headers {"Content-Type" "application/json"}
                                                      :body (json/write-str changes)})]
    {:status (case status
               409 :already-exists
               (400 422) :missing-mandatory-data
               (default-status-codes status))}))

(defn gf-get-user [gf-record login-name]
  (let [{:keys [status body]} (do-request gf-record  {:method :get
                                                      :url (str "/api/users/lookup?loginOrEmail=" login-name)})]
    {:status (default-status-codes status)
     :user body}))

;; NOTE: Grafana will always return 200 HTTP code even if the user doesn't exists.
(defn gf-get-user-orgs [gf-record user-id]
  (let [{:keys [status body]} (do-request gf-record  {:method :get
                                                      :url (str "/api/users/" user-id "/orgs")})]
    {:status (if (and (= 200 status) (empty? body))
               :not-found
               (default-status-codes status))
     :orgs body}))

(defn gf-create-datasource [gf-record data]
  (let [{:keys [status body]} (do-request gf-record {:method :post
                                                     :url (str "/api/datasources")
                                                     :headers {"Content-Type" "application/json"}
                                                     :body (json/write-str data)})]
    {:status (case status
               409 :already-exists
               (default-status-codes status))
     :id (:id body)}))

(defn gf-delete-datasource [gf-record id]
  (let [{:keys [status body]} (do-request gf-record {:method :delete
                                                     :url (str "/api/datasources/" id)})]
    {:status (default-status-codes status)}))

(defn gf-update-datasource [gf-record id changes]
  (let [{:keys [status body]} (do-request gf-record {:method :put
                                                     :url (str "/api/datasources/" id)
                                                     :headers {"Content-Type" "application/json"}
                                                     :body (json/write-str changes)})]
    {:status (default-status-codes status)
     :datasource (:datasource body)}))

(defn gf-get-datasource [gf-record id]
  (let [{:keys [status body]} (do-request gf-record {:method :get
                                                     :url (str "/api/datasources/" id)})]
    {:status (default-status-codes status)
     :datasource body}))

(defn gf-get-datasources [gf-record]
  (let [{:keys [status body]} (do-request gf-record {:method :get
                                                     :url (str "/api/datasources/")})]
    {:status (default-status-codes status)
     :datasources body}))

(defrecord Grafana [uri credentials timeout max-retries backoff-ms]
  core/IDMDashboard
  (get-ds-panels [this org-id ds-uid]
    (with-org this org-id gf-get-current-ds-panels ds-uid))
  (get-org-panels [this org-id]
    (with-org this org-id gf-get-current-org-panels))
  (get-org-dashboards [this org-id]
    (with-org this org-id gf-get-current-dashboards))

  core/IDMOrganization
  (create-org [this org-name]
    (gf-create-org this org-name))
  (get-orgs [this]
    (gf-get-orgs this))
  (update-org [this org-id new-org-name]
    (gf-update-org this org-id new-org-name))
  (delete-org [this org-id]
    (gf-delete-org this org-id))
  (add-org-user [this org-id login-name role]
    (gf-add-org-user this org-id login-name role))
  (get-org-users [this org-id]
    (gf-get-org-users this org-id))

  core/IDMUser
  (create-user [this user-data]
    (gf-create-user this user-data))
  (update-user [this id changes]
    (gf-update-user this id changes))
  (get-user [this login-name]
    (gf-get-user this login-name))
  (get-user-orgs [this user-id]
    (gf-get-user-orgs this user-id))

  core/IDMDatasource
  (create-datasource [this org-id data]
    (with-org this org-id gf-create-datasource data))
  (delete-datasource [this org-id id]
    (with-org this org-id gf-delete-datasource id))
  (update-datasource [this org-id id changes]
    (with-org this org-id gf-update-datasource id changes))
  (get-datasource [this org-id id]
    (with-org this org-id gf-get-datasource id))
  (get-datasources [this org-id]
    (with-org this org-id gf-get-datasources)))

(defmethod ig/init-key :magnet.dashboard-manager/grafana [_ {:keys [uri credentials timeout max-retries backoff-ms]
                                                             :or {timeout default-timeout
                                                                  max-retries default-max-retries
                                                                  backoff-ms default-backoff-ms}}]
  (->Grafana uri credentials timeout max-retries backoff-ms))
