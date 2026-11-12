package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The {@code type} keyword, with {@code nullable} of OpenAPI 3.0. */
final class Types {

  private Types() {}

  /** Checks the type of {@code data}; {@code nullable: true} also lets null through. */
  static boolean check(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final List<String> types = declared(schema);
    if (types.isEmpty()) {
      return true;
    }
    final boolean nullable =
        data.kind() == DocNode.Kind.NULL && JsonValues.isTrue(schema.get("nullable"));
    if (nullable || types.stream().anyMatch(type -> matches(type, data))) {
      return true;
    }
    final List<String> shown = new ArrayList<>(types);
    if (JsonValues.isTrue(schema.get("nullable")) && !shown.contains("null")) {
      shown.add("null");
    }
    validator.getErrors().add(SchemaError.of("type", path, "must be " + String.join(",", shown)));
    return false;
  }

  /** The type names a schema declares, in order. */
  static List<String> declared(final DocNode schema) {
    final Optional<DocNode> type = schema.get("type");
    final List<String> types = new ArrayList<>();
    if (type.isPresent() && type.get().kind() == DocNode.Kind.STRING) {
      types.add(type.get().text());
    } else if (type.isPresent() && type.get().kind() == DocNode.Kind.ARRAY) {
      type.get().items().forEach(item -> types.add(item.text()));
    }
    return types;
  }

  private static boolean matches(final String type, final DocNode data) {
    return switch (type) {
      case "string" -> data.kind() == DocNode.Kind.STRING;
      case "number" -> data.kind() == DocNode.Kind.NUMBER;
      case "integer" -> data.kind() == DocNode.Kind.NUMBER && JsonValues.isInteger(data);
      case "boolean" -> data.kind() == DocNode.Kind.BOOLEAN;
      case "null" -> data.kind() == DocNode.Kind.NULL;
      case "array" -> data.kind() == DocNode.Kind.ARRAY;
      case "object" -> data.kind() == DocNode.Kind.OBJECT;
      default -> false;
    };
  }
}
