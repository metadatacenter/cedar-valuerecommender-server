package utils;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.intelligentauthoring.valuerecommender.ValueRecommenderService;

public class DataServices {

  private static DataServices instance = new DataServices();
  private static ValueRecommenderService valuerecommenderService;
  private static CedarConfig cedarConfig;

  public static DataServices getInstance() {
    return instance;
  }

  private DataServices() {
    cedarConfig = CedarConfig.getInstance();
    ElasticsearchConfig esc = cedarConfig.getElasticsearchConfig();
    valuerecommenderService = new ValueRecommenderService(esc.getCluster(), esc.getHost(), esc.getIndex(), esc
        .getType(), esc
        .getTransportPort(), esc.getSize());
  }

  public static ValueRecommenderService getValueRecommenderService() {
    return valuerecommenderService;
  }
}
