package org.metadatacenter.cedar.valuerecommender.health;

import com.codahale.metrics.health.HealthCheck;

public class ValueRecommenderServerHealthCheck extends HealthCheck {

  public ValueRecommenderServerHealthCheck() {
  }

  @Override
  protected Result check() throws Exception {
    if (2 * 2 == 5) {
      return Result.unhealthy("Unhealthy, because 2 * 2 == 5");
    }
    return Result.healthy();
  }
}