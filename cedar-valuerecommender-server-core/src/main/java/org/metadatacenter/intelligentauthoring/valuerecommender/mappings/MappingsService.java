package org.metadatacenter.intelligentauthoring.valuerecommender.mappings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.intelligentauthoring.valuerecommender.util.Constants.MAPPINGS_FILE_PATH;


public class MappingsService {

  private final static Logger logger = LoggerFactory.getLogger(MappingsService.class);

  private static Map<String, List<String>> mappings;

  static {
    try {
      JsonNode mappingsJson = JsonMapper.MAPPER.readTree(MappingsService.class.getClassLoader().getResourceAsStream
          (MAPPINGS_FILE_PATH));
      mappings = new ObjectMapper().convertValue(mappingsJson, Map.class);

    } catch (IOException e) {
      logger.error(e.getMessage());
    }
  }

  public static List<String> getMappings(String uri, boolean includeCurrentUri) {
    List<String> result = new ArrayList<>();
    if (includeCurrentUri) {
      result.add(uri);
    }
    if (mappings.containsKey(uri)) {
      result.addAll(mappings.get(uri));
    }
    return result;
  }

  public static boolean isSameConcept(String uri1, String uri2) {
    if (uri1.toLowerCase().equals(uri2.toLowerCase())) {
      return true;
    }
    else {
     return false;
    }
  }

}



