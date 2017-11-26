package org.metadatacenter.intelligentauthoring.valuerecommender.associationrules;

import java.util.ArrayList;
import java.util.List;

public class ArffInstance {

  private List<String> values;

  public ArffInstance(List<String> values) {
    this.values = values;
  }

  public ArffInstance() {
    this.values = new ArrayList<>();
  }

  public List<String> getValues() {
    return values;
  }

  public void addValue(String value) {
    values.add(value);
  }

  public String toArffFormat() {
    String result = "";
    for (int i=0; i<values.size(); i++) {
      String value = "'" + values.get(i) + "'";
      if (i==0)
        result = result.concat(value);
      else
        result = result.concat(",").concat(value);
    }
    return result;
  }

}
