package org.metadatacenter.cedar.valuerecommender.resources.swaggermodel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Documentation-only model for the value-recommender recommendation input.
 *
 * <p>The {@code /recommend} method reads the request body as raw JSON, so this thin bean exists
 * purely to reproduce the recommendation input schema that the hand-authored spec exposed. It
 * mirrors that schema exactly: a template identifier, a list of populated fields, a target field,
 * and the {@code strictMatch} / {@code includeDetails} flags.</p>
 */
@Schema(name = "RecommendationInput", description = "The input used to request metadata recommendations for a "
    + "target field.")
public class RecommendationInput {

  @Schema(description = "Example: https://repo.metadatacenter.org/templates/"
      + "8bc64av5-df6b-48c8-8c61-6c016245918e")
  private String templateId;

  @Schema(description = "The fields that have already been populated in the metadata.")
  private List<PopulatedField> populatedFields;

  @Schema(description = "The field for which recommendations are requested.")
  private TargetField targetField;

  @Schema(description = "If set to true, only exact matches are considered.")
  private Boolean strictMatch;

  @Schema(description = "If set to true, it returns details about how the recommendations were generated.")
  private Boolean includeDetails;

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public List<PopulatedField> getPopulatedFields() {
    return populatedFields;
  }

  public void setPopulatedFields(List<PopulatedField> populatedFields) {
    this.populatedFields = populatedFields;
  }

  public TargetField getTargetField() {
    return targetField;
  }

  public void setTargetField(TargetField targetField) {
    this.targetField = targetField;
  }

  public Boolean getStrictMatch() {
    return strictMatch;
  }

  public void setStrictMatch(Boolean strictMatch) {
    this.strictMatch = strictMatch;
  }

  public Boolean getIncludeDetails() {
    return includeDetails;
  }

  public void setIncludeDetails(Boolean includeDetails) {
    this.includeDetails = includeDetails;
  }

  @Schema(name = "PopulatedField", description = "A field that has already been populated in the metadata.")
  public static class PopulatedField {

    @Schema(description = "Example: disease", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldPath;

    @Schema(description = "Example: atopic dermatitis", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldValueLabel;

    @Schema(description = "Example: atopic dermatitis")
    private String fieldValueType;

    public String getFieldPath() {
      return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
      this.fieldPath = fieldPath;
    }

    public String getFieldValueLabel() {
      return fieldValueLabel;
    }

    public void setFieldValueLabel(String fieldValueLabel) {
      this.fieldValueLabel = fieldValueLabel;
    }

    public String getFieldValueType() {
      return fieldValueType;
    }

    public void setFieldValueType(String fieldValueType) {
      this.fieldValueType = fieldValueType;
    }
  }

  @Schema(name = "TargetField", description = "The field for which recommendations are requested.")
  public static class TargetField {

    @Schema(description = "Example: tissue")
    private String fieldPath;

    public String getFieldPath() {
      return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
      this.fieldPath = fieldPath;
    }
  }
}
