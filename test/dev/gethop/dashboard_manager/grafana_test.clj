(ns dev.gethop.dashboard-manager.grafana-test
  (:require
   [clojure.test :refer :all]
   [integrant.core :as ig]
   [dev.gethop.dashboard-manager.grafana :as dm-grafana]
   [dev.gethop.dashboard-manager.core :as dm-core]
   [clojure.string :as str])
  (:import [dev.gethop.dashboard_manager.grafana Grafana]
           [java.util UUID]))

(def ^:const test-config
  {:uri (System/getenv "GRAFANA_TEST_URI")
   :credentials [(System/getenv "GRAFANA_TEST_USERNAME") (System/getenv "GRAFANA_TEST_PASSWORD")]
   :timeout 300
   :max-retries 5
   :backoff-ms [10 500]})

(def ^:const test-datasource
  {:name "Name"
   :type "postgres"
   :url "postgres:5432"
   :access "proxy"
   :password "pass"
   :user "postgres"
   :database "hydrogen"
   :isDefault true
   :jsonData {:postgresVersion 906
              :sslmode "disable"}})

(defn contains-org? [orgs org-name]
  (some #(= org-name (:name %)) orgs))

(defn contains-many? [m & ks]
  (every? #(contains? m %) ks))

(defn random-user-data []
  {:name "user"
   :email (str (UUID/randomUUID) "@email.com")
   :login (str (UUID/randomUUID))
   :password "password"})

(def ^:const default-org-id
  1)

(def ^:const provisioned-dashboard-uid
  "9h9Z01jiz")

(def ^:const provisioned-test-panels
  "Automatically provisioned panels, from test data"
  [{:id 4, :title "Heatmap" :ds-url "/d/9h9Z01jiz/tests-dashboard" :ds-id "9h9Z01jiz"}
   {:id 2, :title "Timeseries" :ds-url "/d/9h9Z01jiz/tests-dashboard" :ds-id "9h9Z01jiz"}
   {:id 6, :title "Singlestat" :ds-url "/d/9h9Z01jiz/tests-dashboard" :ds-id "9h9Z01jiz"}])

(def ^:const provisioned-test-dashboards
  "Automatically provisioned dashboards, from test data"
  [{:uid "9h9Z01jiz"
    :title "Tests Dashboard"
    :url "/d/9h9Z01jiz/tests-dashboard"
    :panels provisioned-test-panels}])

(deftest protocol-test
  (is (instance? Grafana
                 (ig/init-key :dev.gethop.dashboard-manager/grafana test-config))))

(deftest ^:integration IDMDashboard
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`get-ds-panels` test"
      (let [result (dm-core/get-ds-panels gf-boundary default-org-id provisioned-dashboard-uid)]
        (is (= {:status :ok, :panels provisioned-test-panels}
               result))))
    (testing "`get-org-panels` test"
      (let [result (dm-core/get-org-panels gf-boundary default-org-id)]
        (is (= {:status :ok, :panels provisioned-test-panels}
               result))))
    (testing "`get-org-dashboards` test"
      (let [result (dm-core/get-org-dashboards gf-boundary default-org-id)]
        (is (= {:status :ok, :dashboards provisioned-test-dashboards}
               result))))
    (testing "`get-dashboard` test"
      (let [result (dm-core/get-dashboard gf-boundary default-org-id provisioned-dashboard-uid)]
        (is (= :ok (:status result)))
        (is (map? (:meta result)))
        (is (map? (:dashboard result)))))))

(deftest ^:integration update-or-create-dashboard-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        dashboard
        (:dashboard (dm-core/get-dashboard gf-boundary default-org-id provisioned-dashboard-uid))
        new-dashboard (-> dashboard
                          (dissoc :id :uid)
                          (assoc :title (str gensym)))]
    (testing "Create dashboard"
      (let [result (dm-core/update-or-create-dashboard gf-boundary default-org-id new-dashboard)]
        (is (= :ok (:status result)))
        (is (= 1 (:version result)))))
    (testing "Update dashboard"
      (let [updated-dashboard (-> new-dashboard
                                  (assoc :editable false))
            result (dm-core/update-or-create-dashboard
                    gf-boundary default-org-id updated-dashboard {:overwrite true})]
        (is (= :ok (:status result)))
        (is (= (inc (:version dashboard)) (:version result)))))))

(deftest ^:integration delete-dashboard-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        base-dashboard
        (:dashboard (dm-core/get-dashboard gf-boundary default-org-id provisioned-dashboard-uid))
        new-dashboard (-> base-dashboard (dissoc :id :uid) (assoc :title (str (gensym))))
        new-dashboard-uid
        (:uid (dm-core/update-or-create-dashboard gf-boundary default-org-id new-dashboard))]
    (testing "Delete dashboard"
      (let [result (dm-core/delete-dashboard gf-boundary default-org-id new-dashboard-uid)]
        (is (= :ok (:status result)))))
    (testing "Delete dashboard that doesn't exist"
      (let [result (dm-core/delete-dashboard gf-boundary default-org-id new-dashboard-uid)]
        (is (= :not-found (:status result)))))))

(deftest ^:integration create-org-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        org-name (str (UUID/randomUUID))]
    (testing "`create-org` test"
      (let [result (dm-core/create-org gf-boundary org-name)]
        (is (number? (:id result)))
        (is (= :ok (:status result)))))
    (testing "Create an already existing org"
      (dm-core/create-org gf-boundary org-name)
      (let [result (dm-core/create-org gf-boundary org-name)]
        (is (= :already-exists (:status result)))))))

(deftest ^:integration get-orgs-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        org-name (str (UUID/randomUUID))]
    (testing "`get-orgs` test"
      (dm-core/create-org gf-boundary org-name)
      (let [result (dm-core/get-orgs gf-boundary)
            orgs (:orgs result)]
        (is (= :ok (:status result)))
        (is (vector? orgs))
        (is (contains-org? orgs org-name))))))

(deftest ^:integration update-org-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        org-name (str (UUID/randomUUID))]
    (testing "`update-org` test"
      (let [org (dm-core/create-org gf-boundary (str (UUID/randomUUID))) ;; FIXME: passing plain UUID object will throw an exception.
            new-name (str (UUID/randomUUID))
            result (dm-core/update-org gf-boundary (:id org) new-name)
            orgs (:orgs (dm-core/get-orgs gf-boundary))]
        (is (= :ok (:status result)))
        (is (contains-org? orgs new-name))))
    (testing "update non existing orgs"
      (let [result (dm-core/update-org gf-boundary 1213242434 "foo")]
        (is (= :error (:status result)))))))

(deftest ^:integration delete-org-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        org-name (str (UUID/randomUUID))]
    (testing "`delete-org` test"
      (let [org-id (:id (dm-core/create-org gf-boundary (str (UUID/randomUUID))))
            result (dm-core/delete-org gf-boundary org-id)]
        (is (= :ok (:status result)))))
    (testing "delete non existing orgs"
      (let [result (dm-core/delete-org gf-boundary 187239732)]
        (is (= :not-found (:status result)))))))

(deftest ^:integration create-user-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        user-data (random-user-data)]
    (testing "`create-user` test"
      (let [result (dm-core/create-user gf-boundary user-data)]
        (is (= :ok (:status result)))
        (is (number? (:id result)))))
    (testing "create an existing user" ;; TODO: check `gf-create-user` for its returning status code.
      (let [result (dm-core/create-user gf-boundary user-data)]
        (is (= :error (:status result)))))))

(deftest ^:integration update-user-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`update-user` test"
      (let [login-name (str (UUID/randomUUID))
            user-data (assoc (random-user-data) :login login-name)
            changes {:name "Bar" :login login-name}
            user-id (:id (dm-core/create-user gf-boundary user-data))
            result (dm-core/update-user gf-boundary user-id changes)
            user (:user (dm-core/get-user gf-boundary login-name))]
        (is (= :ok (:status result)))
        (is (= (:name changes) (:name user)))))
    (testing "update user with missing `login` parameter"
      (let [user-id (:id (dm-core/create-user gf-boundary (random-user-data)))
            changes {:name "far"}
            result (dm-core/update-user gf-boundary user-id changes)]
        (is (= :missing-mandatory-data (:status result)))))
    ;; FIXME: this test returns an unexpected `:ok` status.
    ;; (testing "update a non existing user"
    ;;   (let [result (dm-core/update-user gf-boundary 1212324 {:name "some-name" :login "some-login"})]
    ;;     (is (= :not-found (:status result)))))
    ))

(deftest ^:integration get-user-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`get-user` test"
      (let [user-data (random-user-data)
            _ (dm-core/create-user gf-boundary user-data)
            result (dm-core/get-user gf-boundary (:login user-data))]
        (is (= :ok (:status result)))
        (is (contains-many? (:user result) :login :name :email))))
    (testing "get non existing user"
      (let [result (dm-core/get-user gf-boundary (str (UUID/randomUUID)))]
        (is (= :not-found (:status result)))))))

(deftest ^:integration get-user-orgs-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`get-user-orgs` test"
      (let [user-id (:id (dm-core/create-user gf-boundary (random-user-data)))
            result (dm-core/get-user-orgs gf-boundary user-id)]
        (is (= :ok (:status result)))
        (is (vector? (:orgs result)))
        (is (contains-many? (first (:orgs result)) :name :orgId :role))))
    (testing "get organizations for a non existing user"
      (let [result (dm-core/get-user-orgs gf-boundary 1243434143)]
        (is (= :not-found (:status result)))))))

(deftest ^:integration delete-user
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`delete-user` test"
      (let [user-data (random-user-data)
            user-id (:id (dm-core/create-user gf-boundary user-data))
            result (dm-core/delete-user gf-boundary user-id)]
        (is (= :ok (:status result)))
        (is (= :not-found (:status (dm-core/get-user gf-boundary (:login user-data)))))))))

(deftest ^:integration add-org-user-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`add-org-user` test"
      (let [org-name (str (UUID/randomUUID))
            org-id (:id (dm-core/create-org gf-boundary org-name))
            user-data (random-user-data)
            user-id (:id (dm-core/create-user gf-boundary user-data))
            result (dm-core/add-org-user gf-boundary org-id (:login user-data) "Viewer")
            user-orgs (:orgs (dm-core/get-user-orgs gf-boundary user-id))]
        (is (= :ok (:status result)))
        (is (contains-org? user-orgs org-name))))
    (testing "`add-org-user` with a non existing role"
      (let [org-name (str (UUID/randomUUID))
            org-id (:id (dm-core/create-org gf-boundary org-name))
            user-data (random-user-data)
            user-id (:id (dm-core/create-user gf-boundary user-data))
            result (dm-core/add-org-user gf-boundary org-id (:login user-data) "NonExistingRole")]
        (is (= :role-not-found (:status result)))))
    (testing "Add an non existing organization to a user"
      (let [user-data (random-user-data)
            user-id (:id (dm-core/create-user gf-boundary user-data))
            result (dm-core/add-org-user gf-boundary 9999 (:login user-data) "Viewer")]
        (is (= :error (:status result)))))
    (testing "Add a organization to a non existing user"
      (let [result (dm-core/add-org-user gf-boundary 9999 (str (UUID/randomUUID)) "Viewer")]
        (is (= :user-not-found (:status result)))))))

(deftest ^:integration get-org-users
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`get-org-users` test"
      (let [org-id (:id (dm-core/create-org gf-boundary (str (UUID/randomUUID))))
            user-data (random-user-data)
            _ (dm-core/create-user gf-boundary user-data)
            _ (dm-core/add-org-user gf-boundary org-id (:login user-data) "Viewer")
            result (dm-core/get-org-users gf-boundary org-id)]
        (is (= :ok (:status result)))
        (is (vector? (:users result)))
        (is (>= (count (:users result)) 2))))
    (testing "Get users of a non existing organization"
      (let [result (dm-core/get-org-users gf-boundary 9999)]
        (is (= :not-found (:status result)))))))

(deftest ^:integration delete-org-user
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)]
    (testing "`delete-org-user` test"
      (let [org-id (:id (dm-core/create-org gf-boundary (str (UUID/randomUUID))))
            user-data (random-user-data)
            _ (dm-core/create-user gf-boundary user-data)
            _ (dm-core/add-org-user gf-boundary org-id (:login user-data) "Viewer")
            user-id (-> (dm-core/get-user gf-boundary (:login user-data)) :user :id)
            result (dm-core/delete-org-user gf-boundary org-id user-id)]
        (is (= :ok (:status result)))))
    (testing "deleting an org user that doesn't exists"
      (let [result (dm-core/delete-org-user gf-boundary 1 23354)]
        (is (= :error (:status result)))))))

(deftest ^:integration create-datasource-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        data (assoc test-datasource :name (str (UUID/randomUUID)))]
    (testing "`create-datasource` test"
      (let [result (dm-core/create-datasource gf-boundary 1 data)]
        (is (number? (:id result)))
        (is (= :ok (:status result)))))
    (testing "Create an already existing datasource"
      (dm-core/create-datasource gf-boundary 1 data)
      (let [result (dm-core/create-datasource gf-boundary 1 data)]
        (is (= :already-exists (:status result)))))))

(deftest ^:integration get-datasource-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        name (str (UUID/randomUUID))
        datasource (assoc test-datasource :name name)]
    (testing "`get-datasource` test"
      (let [id (:id (dm-core/create-datasource gf-boundary 1 datasource))
            result (dm-core/get-datasource gf-boundary 1 id)]
        (is (= :ok (:status result)))
        (is (map? datasource))
        (is (every? #(= (second %) ((first %) (:datasource result))) datasource))))))

(deftest ^:integration get-datasources-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        name (str (UUID/randomUUID))
        datasource (assoc test-datasource :name name)]
    (testing "`get-datasources` test"
      (dm-core/create-datasource gf-boundary 1 datasource)
      (let [result (dm-core/get-datasources gf-boundary 1)
            datasources (:datasources result)]
        (is (= :ok (:status result)))
        (is (vector? datasources))
        (is (some #(= (:name %) name) datasources))))))

(deftest ^:integration update-datasource-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        datasource (assoc test-datasource :name (str (UUID/randomUUID)))
        datasource2 (assoc datasource :name (str (UUID/randomUUID)))]
    (testing "`update-datasource` test"
      (let [id (:id (dm-core/create-datasource gf-boundary 1 datasource))
            result (dm-core/update-datasource gf-boundary 1 id datasource2)]
        (is (= :ok (:status result)))
        (is (= (-> result :datasource :name) (:name datasource2)))))))

(deftest ^:integration delete-datasource-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana test-config)
        datasource (assoc test-datasource :name (str (UUID/randomUUID)))]
    (testing "`delete-datasource` test"
      (let [id (:id (dm-core/create-datasource gf-boundary 1 datasource))
            result (dm-core/delete-datasource gf-boundary 1 id)
            get-deleted (:status (dm-core/get-datasource gf-boundary 1 id))]
        (is (= :ok (:status result)))
        (is (= :not-found get-deleted))))
    (testing "delete non existing datasource"
      (let [result (dm-core/delete-datasource gf-boundary 1 (rand-int 1000))]
        (is (= :error (:status result)))))))

(deftest ^:integration regular-login-test
  (let [gf-boundary (ig/init-key :dev.gethop.dashboard-manager/grafana (assoc test-config :auth-method :regular-login))]
    (testing "regular login should yield the session cookie"
      (is (str/starts-with? (:session-cookie gf-boundary) dm-grafana/grafana-session-cookie)))
    (testing "logged in user should be able to get orgs that he belongs to"
      (let [{:keys [status orgs]} (dm-grafana/gf-get-current-user-orgs gf-boundary)]
        (is (= :ok status))
        (is (pos? (count orgs)))))))
