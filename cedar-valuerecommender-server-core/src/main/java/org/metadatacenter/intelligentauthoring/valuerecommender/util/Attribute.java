package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import java.util.List;

/**
 * Stores the path to a particular metadata instance field
 */
public class Attribute {

  // List of nodes from the root to the attribute of interest
  private List<Node> path;

  // constructor
  public Attribute(List<Node> path) {
    this.path = path;
  }

  // getter
  public List<Node> getPath() {
    return path;
  }

  /**
   * @return The field path using Weka's attribute format (e.g., 'Address.Zip Code')
   */
  public String toWekaAttributeFormat() {
    String result = "";
    for (Node node : path) {
      if (result.trim().length() > 0) {
        result = result.concat(".");
      }
      result = result.concat(node.getKey());
    }
    return "'" + result + "'";
  }

  /**
   * @return The field path using JSON Path format (e.g., $['Address'].['Zip Code']')
   */
  public String toJsonPathFormat() {
    String result = "$";
    for (Node node : path) {
      result = result.concat("['" + node.getKey() + "']");
      if (node.getInstanceNodetype() == Node.NodeType.ARRAY) {
        result = result.concat("[0:]");
      }
    }
    return result;
  }

  /**
   * Checks if the query will generate an array of results or just an object
   */
  public boolean generatesArrayResult() {
    for (Node node : path) {
      if (node.getInstanceNodetype().equals(Node.NodeType.ARRAY)) {
        return true;
      }
    }
    return false;
  }

}
