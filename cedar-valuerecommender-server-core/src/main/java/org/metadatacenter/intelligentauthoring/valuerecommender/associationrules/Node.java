package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

public class Node {

  private String key;
  private NodeType type;
  public enum NodeType {OBJECT, ARRAY}

  public Node(String key, NodeType nodetype) {
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
