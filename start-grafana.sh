docker run \
  --rm \
  --name ${GRAFANA_TEST_CONTAINER_NAME} \
  -d \
  -p 3000:3000 \
  -v "$(pwd)/grafana/datasources":/etc/grafana/provisioning/datasources \
  -v "$(pwd)/grafana/dashboards":/etc/grafana/provisioning/dashboards \
  grafana/grafana:${GRAFANA_TEST_VERSION}
