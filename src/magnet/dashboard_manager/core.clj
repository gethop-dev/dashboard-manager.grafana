;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.dashboard-manager.core)

(defprotocol IDMDashboard
  (get-ds-panels [this org-id ds-uid])
  (get-org-panels [this org-id])
  (get-org-dashboards [this org-id]))

(defprotocol IDMOrganization
  (create-org [this org-name])
  (get-orgs [this])
  (update-org [this org-id new-org-name])
  (delete-org [this org-id])
  (add-org-user [this org-id user-login role])
  (get-org-users [this org-id]))

(defprotocol IDMUser
  (create-user [this user-data])
  (update-user [this id changes])
  (get-user [this login-name])
  (get-user-orgs [this user-id]))

(defprotocol IDMDatasource
  (create-datasource [this org-id data])
  (delete-datasource [this org-id id])
  (update-datasource [this org-id id changes])
  (get-datasource [this org-id id])
  (get-datasources [this org-id]))
