package org.metadatacenter.intelligentauthoring.valuerecommender.util;

public class InstanceNode {

  private String key;
  private NodeType type;
  public enum NodeType {OBJECT, ARRAY}

  public InstanceNode(String key, NodeType nodetype) {
    this.key = key;
    this.type = nodetype;
  }

  public String getKey() {
    return key;
  }

  public NodeType getInstanceNodetype() {
    return type;
  }

}
