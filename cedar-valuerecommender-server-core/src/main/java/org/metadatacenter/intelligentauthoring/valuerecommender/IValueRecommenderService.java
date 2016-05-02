package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;

import java.net.UnknownHostException;
import java.util.List;

public interface IValueRecommenderService {

  boolean hasInstances(String templateId) throws UnknownHostException;
  Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField) throws
      UnknownHostException;

}
