package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import java.util.List;

/**
 * Stores the path to a particular metadata instance field
 */
public class FieldPath {

  // List of nodes from the root to the field of interest
  private List<InstanceNode> nodeList;

  // constructor
  public FieldPath(List<InstanceNode> nodeList) {
    this.nodeList = nodeList;
  }

  // getter
  public List<InstanceNode> getNodeList() {
    return nodeList;
  }

  /**
   * @return The field path using Weka's attribute format (e.g., 'Address.Zip Code')
   */
  public String toWekaAttributeFormat() {
    String path = "";
    for (InstanceNode node : nodeList) {
      if (path.trim().length() > 0) {
        path = path.concat(".");
      }
      path = path.concat(node.getKey());
    }
    return "'" + path + "'";
  }

  /**
   * @return The field path using JSON Path format (e.g., $['Address'].['Zip Code']')
   */
  public String toJsonPathFormat() {
    String path = "$";
    for (InstanceNode node : nodeList) {
      path = path.concat("['" + node.getKey() + "']");
      if (node.getInstanceNodetype() == InstanceNode.NodeType.ARRAY) {
        path = path.concat("[0:]");
      }
    }
    return path;
  }

  /**
   * Checks if the query will generate an array of results or just an object
   */
  public boolean generatesArrayResult() {
    for (InstanceNode node : nodeList) {
      if (node.getInstanceNodetype().equals(InstanceNode.NodeType.ARRAY)) {
        return true;
      }
    }
    return false;
  }

}
