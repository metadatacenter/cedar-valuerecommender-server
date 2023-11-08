package org.metadatacenter.intelligentauthoring.valuerecommender.domainobjects;

public record RecommendationDetails(String rule, Double contextMatchingScore, Double ruleConfidence, Double ruleSupport, RecommendedValue.RecommendationType recommendationType) {

}
