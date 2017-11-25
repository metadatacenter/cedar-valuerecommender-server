package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import org.metadatacenter.model.CedarNodeType;

import java.util.List;

/**
 * Stores path and some basic characteristics of template elements and fields
 */
public class TemplateNode {

  private String key; // json key
  private List<String> path; // List of json keys from the root (it includes the key of the current node)
  private CedarNodeType type;
  private boolean isArray;

  public TemplateNode(String key, List<String> path, CedarNodeType type, boolean isArray) {
    this.key = key;
    this.path = path;
    this.type = type;
    this.isArray = isArray;
  }

  public String getKey() {
    return key;
  }

  public List<String> getPath() {
    return path;
  }

  public CedarNodeType getType() {
    return type;
  }

  public boolean isArray() {
    return isArray;
  }

  public String generatePath() {
    return String.join(".", path);
  }

  /**
   * @return The field path using JSON Path format (e.g., $['Address'].['Zip Code']')
   */
//  public String toJsonPathFormat() {
//    String result = "$";
//    for (String key : path) {
//      result = result.concat("['" + key + "']");
//      if (node.getInstanceNodetype() == Node.NodeType.ARRAY) {
//        result = result.concat("[0:]");
//      }
//    }
//    return result;
//  }

}
