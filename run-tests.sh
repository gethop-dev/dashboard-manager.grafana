export GRAFANA_TEST_USERNAME=admin
export GRAFANA_TEST_PASSWORD=admin
export GRAFANA_TEST_URI=http://localhost:3000
export GRAFANA_TEST_CONTAINER_NAME=grafana-container

export GRAFANA_TEST_VERSION=6.2.5
./start-grafana.sh
lein lint-and-test
./stop-grafana.sh

export GRAFANA_TEST_VERSION=8.5.3
./start-grafana.sh
lein lint-and-test
./stop-grafana.sh
