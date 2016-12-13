package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import errors.ErrorMessage;

public class ErrorMsgBuilder {

  public static JsonNode build(int status, String title, String detail) {
    ErrorMessage msg = new ErrorMessage(status, title, detail);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.valueToTree(msg);
  }

}
