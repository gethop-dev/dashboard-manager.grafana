(ns magnet.dashboard-manager.grafana-test
  (:require
   [clojure.test :refer :all]
   [integrant.core :as ig]
   [magnet.dashboard-manager.grafana :as dm-grafana]
   [magnet.dashboard-manager.core :as dm-core]
   [clojure.string :as str])
  (:import [magnet.dashboard_manager.grafana Grafana]
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

(deftest protocol-test
  (is (instance? Grafana
                 (ig/init-key :magnet.dashboard-manager/grafana test-config))))

(deftest ^:integration create-org-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
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
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
        org-name (str (UUID/randomUUID))]
    (testing "`get-orgs` test"
      (dm-core/create-org gf-boundary org-name)
      (let [result (dm-core/get-orgs gf-boundary)
            orgs (:orgs result)]
        (is (= :ok (:status result)))
        (is (vector? orgs))
        (is (contains-org? orgs org-name))))))

(deftest ^:integration update-org-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
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
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
        org-name (str (UUID/randomUUID))]
    (testing "`delete-org` test"
      (let [org-id (:id (dm-core/create-org gf-boundary (str (UUID/randomUUID))))
            result (dm-core/delete-org gf-boundary org-id)]
        (is (= :ok (:status result)))))
    (testing "delete non existing orgs"
      (let [result (dm-core/delete-org gf-boundary 187239732)]
        (is (= :not-found (:status result)))))))

(deftest ^:integration create-user-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
        user-data (random-user-data)]
    (testing "`create-user` test"
      (let [result (dm-core/create-user gf-boundary user-data)]
        (is (= :ok (:status result)))
        (is (number? (:id result)))))
    (testing "create an existing user" ;; TODO: check `gf-create-user` for its returning status code.
      (let [result (dm-core/create-user gf-boundary user-data)]
        (is (= :error (:status result)))))))

(deftest ^:integration update-user-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)]
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
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)]
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
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)]
    (testing "`get-user-orgs` test"
      (let [user-id (:id (dm-core/create-user gf-boundary (random-user-data)))
            result (dm-core/get-user-orgs gf-boundary user-id)]
        (is (= :ok (:status result)))
        (is (vector? (:orgs result)))
        (is (contains-many? (first (:orgs result)) :name :orgId :role))))
    (testing "get organizations for a non existing user"
      (let [result (dm-core/get-user-orgs gf-boundary 1243434143)]
        (is (= :not-found (:status result)))))))

(deftest ^:integration add-org-user-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)]
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
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)]
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

(deftest ^:integration create-datasource-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
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
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
        name (str (UUID/randomUUID))
        datasource (assoc test-datasource :name name)]
    (testing "`get-datasource` test"
      (let [id (:id (dm-core/create-datasource gf-boundary 1 datasource))
            result (dm-core/get-datasource gf-boundary 1 id)]
        (is (= :ok (:status result)))
        (is (map? datasource))
        (is (every? #(= (second %) ((first %) (:datasource result))) datasource))))))

(deftest ^:integration get-datasources-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
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
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
        datasource (assoc test-datasource :name (str (UUID/randomUUID)))
        datasource2 (assoc datasource :name (str (UUID/randomUUID)))]
    (testing "`update-datasource` test"
      (let [id (:id (dm-core/create-datasource gf-boundary 1 datasource))
            result (dm-core/update-datasource gf-boundary 1 id datasource2)]
        (is (= :ok (:status result)))
        (is (= (-> result :datasource :name) (:name datasource2)))))))

(deftest ^:integration delete-datasource-test
  (let [gf-boundary (ig/init-key :magnet.dashboard-manager/grafana test-config)
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
