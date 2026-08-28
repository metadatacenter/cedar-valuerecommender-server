package org.metadatacenter.intelligentauthoring.valuerecommender;

import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Field;
import org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects.Recommendation;
import org.metadatacenter.intelligentauthoring.valuerecommender.io.CanGenerateRecommendationsStatus;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;

import java.io.IOException;
import java.util.List;

/**
 * Value Recommender service interface
 */
public interface IValueRecommenderService {

  void generateRules(List<String> templateId);

  CanGenerateRecommendationsStatus canGenerateRecommendations(String templateId)
      throws CedarDependencyUnavailableException;

  Recommendation getRecommendation(String templateId, List<Field> populatedFields, Field targetField,
                                   boolean strictMatch, boolean filterByRecommendationScore,
                                   boolean filterByConfidence, boolean filterBySupport, boolean useMappings,
                                   boolean includeDetails) throws IOException, CedarDependencyUnavailableException;

  List<RulesGenerationStatus> getRulesGenerationStatus();

  RulesGenerationStatus getRulesGenerationStatus(String templateId) throws CedarProcessingException;

}
