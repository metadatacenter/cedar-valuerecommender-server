# CEDAR ValueRecommender Server

To run the server

    java \
      -Dkeycloak.config.path="$CEDAR_HOME/keycloak.json" \
      -jar $CEDAR_HOME/cedar-valuerecommender-server/cedar-valuerecommender-server-application/target/cedar-valuerecommender-server-application-*.jar \
      server \
      "$CEDAR_HOME/cedar-valuerecommender-server/cedar-valuerecommender-server-application/config.yml"

To access the application:

[http://localhost:9006/]()

To access the admin port:

[http://localhost:9106/]()