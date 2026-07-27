package org.metadatacenter.cedar.valuerecommender.config;

import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.AbstractCedarConfigTest;

public class CedarConfigValuerecommenderTest extends AbstractCedarConfigTest {

  @Override
  protected SystemComponent getSystemComponent() {
    return SystemComponent.SERVER_VALUERECOMMENDER;
  }

}
