package errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * This class represents an error returned by the application. It is a simplification of the model suggested at
 * http://jsonapi
 * .org/format/#errors
 */
// Ignore null values
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorMessage {

  public int status;
  public String title;
  public String detail;

  /**
   * @param status The HTTP status code applicable to this problem, expressed as a string value.
   * @param title  A short, human-readable summary of the problem that SHOULD NOT change from occurrence to
   *               occurrence of the problem, except for purposes of localization.
   * @param detail A human-readable explanation specific to this occurrence of the problem. Like title, this field's
   *               value can be localized.
   */
  public ErrorMessage(int status, String title, String detail) {
    this.status = status;
    this.title = title;
    this.detail = detail;
  }

  // The default constructor is used by Jackson for deserialization
  public ErrorMessage() {
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }
}
