package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.CedarNodeType;

import java.util.*;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.*;

public class CedarUtils {



  /**
   * Returns the value of a given field.
   *
   * @param node
   * @return
   */
  public static Optional<String> getValueOfField(Map node) {
    if (node.containsKey(VALUE_FIELD_NAME)) {
      return Optional.of(node.get(VALUE_FIELD_NAME).toString());
    } else if (node.containsKey(ID_FIELD_NAME)) {
      return Optional.of(node.get(ID_FIELD_NAME).toString());
    } else {
      return Optional.empty();
    }
  }

}