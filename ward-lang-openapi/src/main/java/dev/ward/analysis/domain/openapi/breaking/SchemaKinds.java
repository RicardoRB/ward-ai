package dev.ward.analysis.domain.openapi.breaking;

import java.util.Arrays;
import java.util.List;

/** The kinds of change that have a check at each place of a schema. */
final class SchemaKinds {

  /** The kinds of request-body checks. */
  private static final String REQUEST_BODY =
      """
      all-of-added became-enum became-not-nullable const-added const-changed contains-added
      content-encoding-changed content-media-type-changed content-schema-added
      dependent-required-added dependent-required-changed dependent-schema-added else-added
      enum-value-removed exclusive-max-decreased exclusive-max-set exclusive-min-increased
      exclusive-min-set if-added max-contains-decreased max-contains-set max-decreased
      max-items-decreased max-items-set max-length-decreased max-length-set
      max-properties-decreased max-properties-set max-set min-contains-increased min-contains-set
      min-increased min-items-increased min-items-set min-length-increased min-length-set
      min-properties-increased min-properties-set min-set multiple-of-changed multiple-of-set
      pattern-property-added property-names-added schema-became-false then-added type-changed
      unique-items-set
      """;

  /** The kinds of request-property checks. */
  private static final String REQUEST_PROPERTY =
      """
      all-of-added became-enum became-not-nullable became-required const-added const-changed
      contains-added content-encoding-changed content-media-type-changed content-schema-added
      dependent-required-added dependent-required-changed dependent-schema-added else-added
      enum-value-removed exclusive-max-decreased exclusive-max-set exclusive-min-increased
      exclusive-min-set if-added max-contains-decreased max-contains-set max-decreased
      max-items-decreased max-items-set max-length-decreased max-length-set
      max-properties-decreased max-properties-set max-set min-contains-increased min-contains-set
      min-increased min-items-increased min-items-set min-length-increased min-length-set
      min-properties-increased min-properties-set min-set multiple-of-changed multiple-of-set
      pattern-added pattern-property-added property-names-added schema-became-false then-added
      type-changed unique-items-set
      x-extensible-enum-value-removed
      """;

  /** The kinds of request-parameter checks. */
  private static final String REQUEST_PARAMETER =
      """
      became-enum became-not-nullable enum-value-removed exclusive-max-decreased
      exclusive-max-set exclusive-min-increased exclusive-min-set max-contains-decreased
      max-contains-set max-decreased max-items-decreased max-items-set max-length-decreased
      max-length-set max-properties-decreased max-properties-set max-set min-contains-increased
      min-contains-set min-increased min-items-increased min-items-set min-length-increased
      min-length-set min-properties-increased min-properties-set min-set multiple-of-set
      pattern-added schema-became-false type-changed
      x-extensible-enum-value-removed
      """;

  /** The kinds of response-body checks. */
  private static final String RESPONSE_BODY =
      """
      became-nullable const-changed const-removed contains-removed content-encoding-changed
      content-media-type-changed content-schema-removed dependent-required-changed
      dependent-required-removed dependent-schema-removed else-removed exclusive-max-increased
      exclusive-max-unset exclusive-min-decreased exclusive-min-unset if-removed
      max-contains-increased max-contains-unset max-increased max-items-increased max-items-unset
      max-length-increased max-length-unset max-properties-increased max-properties-unset
      max-unset min-contains-decreased min-contains-unset min-decreased min-items-decreased
      min-items-unset min-length-decreased min-length-unset min-properties-decreased
      min-properties-unset min-unset multiple-of-changed multiple-of-unset
      pattern-property-removed property-names-removed then-removed type-changed type-generalized
      unique-items-unset
      schema-became-false
      """;

  /** The kinds of response-property checks. */
  private static final String RESPONSE_PROPERTY =
      """
      became-not-enum became-nullable became-optional const-changed const-removed
      contains-removed content-encoding-changed content-media-type-changed content-schema-removed
      dependent-required-changed dependent-required-removed dependent-schema-removed else-removed
      enum-value-added exclusive-max-increased exclusive-max-unset exclusive-min-decreased
      exclusive-min-unset if-removed max-contains-increased max-contains-unset max-increased
      max-items-increased max-items-unset max-length-increased max-length-unset
      max-properties-increased max-properties-unset max-unset min-contains-decreased
      min-contains-unset min-decreased min-items-decreased min-items-unset min-length-decreased
      min-length-unset min-properties-decreased min-properties-unset min-unset
      multiple-of-changed multiple-of-unset pattern-property-removed pattern-removed
      property-names-removed schema-became-not-false then-removed type-changed type-generalized
      unique-items-unset
      """;

  /** The kinds of response-header checks. */
  private static final String RESPONSE_HEADER =
      """
      became-nullable exclusive-max-increased exclusive-max-unset exclusive-min-decreased
      exclusive-min-unset max-contains-increased max-contains-unset max-increased
      max-items-increased max-items-unset max-length-increased max-length-unset
      max-properties-increased max-properties-unset max-unset min-contains-decreased
      min-contains-unset min-decreased min-items-decreased min-items-unset min-length-decreased
      min-length-unset min-properties-decreased min-properties-unset min-unset multiple-of-unset
      schema-became-not-false type-generalized
      """;

  private static final String REQUEST_PARAMETER_PROPERTY =
      "became-not-nullable enum-value-removed schema-became-false";

  private static final String REQUEST_HEADER_PROPERTY = "became-enum became-required";

  private SchemaKinds() {}

  /** The kinds with a check at the place whose check ids start with {@code prefix}. */
  static List<String> of(final String prefix) {
    final String kinds =
        switch (prefix) {
          case "request-body" -> REQUEST_BODY;
          case "request-property" -> REQUEST_PROPERTY;
          case "request-parameter" -> REQUEST_PARAMETER;
          case "response-body" -> RESPONSE_BODY;
          case "response-property" -> RESPONSE_PROPERTY;
          case "response-header" -> RESPONSE_HEADER;
          case "request-parameter-property" -> REQUEST_PARAMETER_PROPERTY;
          case "request-header-property" -> REQUEST_HEADER_PROPERTY;
          default -> "";
        };
    return Arrays.asList(kinds.split("\\s+"));
  }
}
