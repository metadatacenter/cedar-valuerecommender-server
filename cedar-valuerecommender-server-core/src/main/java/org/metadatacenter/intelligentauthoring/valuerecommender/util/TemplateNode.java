package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import org.metadatacenter.model.CedarResourceType;

import java.util.List;
import java.util.Optional;

/**
 * Stores path and some basic characteristics of template elements and fields
 */
public class TemplateNode {

  private String id; // resource identifier (i.e., @id field)
  private String key; // json key
  private List<String> path; // List of json keys from the root (it includes the key of the current node)
  private CedarResourceType type; // Node type (e.g. field)
  private Optional<String> instanceType; // Instance type. It is the type of the field defined using an ontology term
  private boolean isArray;
  private Boolean isValueRecommendationEnabled;

  public TemplateNode(String id, String key, List<String> path, CedarResourceType type, Optional<String> instanceType,
                      boolean isArray, Boolean isValueRecommendationEnabled) {
    this.id = id;
    this.key = key;
    this.path = path;
    this.type = type;
    this.instanceType = instanceType;
    this.isArray = isArray;
    this.isValueRecommendationEnabled = isValueRecommendationEnabled;
  }

  public String getId() { return id;}

  public String getKey() {
    return key;
  }

  public List<String> getPath() { return path;}

  public CedarResourceType getType() {
    return type;
  }

  public Optional<String> getInstanceType() { return instanceType; }

  public boolean isArray() {
    return isArray;
  }

  public boolean isValueRecommendationEnabled() { return isValueRecommendationEnabled;}

  public String generatePathDotNotation() {
    return String.join(".", path);
  }

}
