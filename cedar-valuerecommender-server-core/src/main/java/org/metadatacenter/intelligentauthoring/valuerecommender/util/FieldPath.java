package org.metadatacenter.intelligentauthoring.valuerecommender.util;

public class FieldPath {

  private String path;
  private String pathSquareBrackets;

  public FieldPath(String path, String pathSquareBrackets) {
    this.path = path;
    this.pathSquareBrackets = pathSquareBrackets;
  }

  public String getPath() {
    return path;
  }

  public String getPathSquareBrackets() {
    return pathSquareBrackets;
  }
}
