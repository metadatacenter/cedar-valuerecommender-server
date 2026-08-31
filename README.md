# cedar-valuerecommender-server

[![CI](https://github.com/metadatacenter/cedar-valuerecommender-server/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/metadatacenter/cedar-valuerecommender-server/actions/workflows/ci.yml)

A service that provides metadata recommendations for CEDAR template fields.

The deployable Dropwizard service is in `cedar-valuerecommender-server-application`.

## Development

CEDAR backend development uses Java 17. Infrastructure versions and startup order are managed for the
whole local CEDAR stack rather than per repository.

From a configured CEDAR workspace:

```bash
export CEDAR_HOME="$HOME/CEDAR"
source "$CEDAR_HOME/cedar-profile-native-develop.sh"
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw test
```

Use `cedar-development/ops/cedar-services.sh` to start, stop, and inspect the service as part of the
native stack. The canonical setup, build, test, and runtime instructions are in the
[CEDAR backend runbook](https://github.com/metadatacenter/cedar-development/blob/develop/ops/BACKEND-RUNBOOK.md).
General CEDAR documentation is at [metadatacenter.org](https://metadatacenter.org/).

#### Questions

If you have questions about this repository, please subscribe to the [CEDAR Developer Support
mailing list](https://mailman.stanford.edu/mailman/listinfo/cedar-developers).
After subscribing, send messages to cedar-developers at lists.stanford.edu.
