# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/).

## [0.2.6] - 2020-09-04
### Added
- New methods for managing dashboards: `get-dashboard`, `update-or-create-dashboard`, `delete-dashboard` and `get-dashboards-with-tag`

## [0.2.5] - 2020-01-07
### Changed
- Add dashboard uid to returning panels map
- Split user and project profiles in `config.edn`

## [0.2.4] - 2019-09-23
### Added
- Unit tests for core/IDMDashboard protocol implementation

## [0.2.3] - 2019-09-23
### Fixed
- Operations inside `with-org` are performed atomically using `locking` primitive`, to avoid concurrency issues

## [0.2.2] - 2019-09-04

### Added
- Add new `delete-org-user` method for removing users from an organization

## [0.2.1] - 2019-08-30

### Added
- Add new `delete-user` method for user management

## [0.2.0] - 2019-07-30

### Added
- Add new methods to manage datasources

## [0.1.1] - 2019-05-27

### Added
- This CHANGELOG
- Integration tests
- README documentation

### Changed
- Fix [#1 json/read-str can throw exception on invalid JSON content](https://github.com/magnetcoop/dashboard-manager.grafana/issues/1)
- Fix [#2 Exceptions thrown by do-request are not catched anywhere](https://github.com/magnetcoop/dashboard-manager.grafana/issues/2)
- Fix [#5 Wrong status code when setting an invalid role to a user](https://github.com/magnetcoop/dashboard-manager.grafana/issues/5)
- Fix [#6 Get-org-users returns status :ok even if the org doesn't exist](https://github.com/magnetcoop/dashboard-manager.grafana/issues/6)
- Remove logger.

[UNRELEASED]:  https://github.com/magnetcoop/buddy-auth.jwt-oidc/compare/v0.2.4...HEAD
[0.2.4]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.4
[0.2.3]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.3
[0.2.2]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.2
[0.2.1]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.1
[0.2.1]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.1
[0.2.0]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.2.0
[0.1.1]: https://github.com/magnetcoop/buddy-auth.jwt-oidc/releases/tag/v0.1.1
