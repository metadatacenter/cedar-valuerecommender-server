package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules.elasticsearch;

import java.util.List;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

/**
 * An association rule
 */
public class EsRule {

  private List<EsRuleItem> premise;
  private List<EsRuleItem> consequence;
  private double support;
  private double confidence;
  private double lift;
  private double leverage;
  private double conviction;

  public EsRule(List<EsRuleItem> premise, List<EsRuleItem> consequence, double support, double confidence, double lift,
                double leverage, double conviction) {
    this.premise = premise;
    this.consequence = consequence;
    this.support = support;
    this.confidence = confidence;
    this.lift = lift;
    this.leverage = leverage;
    this.conviction = conviction;
  }

  public List<EsRuleItem> getPremise() {
    return premise;
  }

  public List<EsRuleItem> getConsequence() {
    return consequence;
  }

  public double getSupport() {
    return support;
  }

  public double getConfidence() {
    return confidence;
  }

  public double getLift() {
    return lift;
  }

  public double getLeverage() {
    return leverage;
  }

  public double getConviction() {
    return conviction;
  }

  @Override
  public String toString() {

    String premiseString = "";
    for (EsRuleItem ruleItem : premise) {
      premiseString += "[" + ruleItem.getFieldName() + "=" + ruleItem.getFieldValue() + "] ";
    }

    String consequenceString = "";
    for (EsRuleItem ruleItem : consequence) {
      consequenceString += "[" + ruleItem.getFieldName() + "=" + ruleItem.getFieldValue() + "] ";
    }

    return "\n" + premiseString + " ==> " + consequenceString + "(" +
        SUPPORT_METRIC_NAME + "=" + support + "," +
        CONFIDENCE_METRIC_NAME + "=" + confidence + "," +
        LIFT_METRIC_NAME + "=" + lift + "," +
        LEVERAGE_METRIC_NAME + "=" + leverage + "," +
        CONVICTION_METRIC_NAME + "=" + conviction + ")";
  }

}
