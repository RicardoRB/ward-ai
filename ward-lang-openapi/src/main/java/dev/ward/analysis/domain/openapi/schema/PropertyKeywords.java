package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** The keywords that read the properties of an object: additional, declared and evaluated. */
final class PropertyKeywords {

  private PropertyKeywords() {}

  static boolean additional(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> rule = schema.get("additionalProperties");
    if (rule.isEmpty()) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry entry : data.entries()) {
      if (!declared(schema, entry.key())) {
        evaluated.add(entry.key());
        valid &= extra(validator, rule.get(), entry, path, "additionalProperties");
        if (!valid && !validator.allErrors()) {
          return false;
        }
      }
    }
    return valid;
  }

  /** Applies the rule of {@code additionalProperties} or {@code unevaluatedProperties}. */
  private static boolean extra(
      final JsonSchemaValidator validator,
      final DocNode rule,
      final DocNode.Entry entry,
      final String path,
      final String keyword) {
    if (rule.kind() == DocNode.Kind.BOOLEAN && !JsonValues.isTrue(Optional.of(rule))) {
      final String message =
          "additionalProperties".equals(keyword)
              ? "must NOT have additional properties"
              : "must NOT have unevaluated properties";
      validator.errors().add(SchemaError.about(keyword, path, message, entry.key()));
      return false;
    }
    return rule.kind() != DocNode.Kind.OBJECT
        || validator.check(rule, entry.value(), child(path, entry.key()), validator.scratch());
  }

  private static String child(final String path, final String key) {
    return path + "/" + JsonValues.escape(key);
  }

  /** Tells whether {@code properties} or {@code patternProperties} names the key. */
  private static boolean declared(final DocNode schema, final String key) {
    return schema.get("properties").filter(node -> node.get(key).isPresent()).isPresent()
        || schema
            .get("patternProperties")
            .filter(node -> node.entries().stream().anyMatch(entry -> matches(entry.key(), key)))
            .isPresent();
  }

  private static boolean matches(final String expression, final String key) {
    try {
      return Pattern.compile(expression).matcher(key).find();
    } catch (final PatternSyntaxException exception) {
      return false;
    }
  }

  static boolean properties(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> declared = schema.get("properties");
    if (declared.isEmpty() || declared.get().kind() != DocNode.Kind.OBJECT) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry property : declared.get().entries()) {
      final Optional<DocNode> value = data.get(property.key());
      if (value.isPresent()) {
        evaluated.add(property.key());
        valid &=
            validator.check(
                property.value(), value.get(), child(path, property.key()), validator.scratch());
        if (!valid && !validator.allErrors()) {
          return false;
        }
      }
    }
    return valid;
  }

  static boolean patterns(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> declared = schema.get("patternProperties");
    if (declared.isEmpty() || declared.get().kind() != DocNode.Kind.OBJECT) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry rule : declared.get().entries()) {
      for (final DocNode.Entry entry : data.entries()) {
        if (matches(rule.key(), entry.key())) {
          evaluated.add(entry.key());
          valid &=
              validator.check(
                  rule.value(), entry.value(), child(path, entry.key()), validator.scratch());
          if (!valid && !validator.allErrors()) {
            return false;
          }
        }
      }
    }
    return valid;
  }

  static boolean unevaluated(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> rule = schema.get("unevaluatedProperties");
    if (rule.isEmpty() || validator.getDialect() != JsonSchemaValidator.Dialect.DRAFT2020) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry entry : data.entries()) {
      if (evaluated.add(entry.key())) {
        valid &= extra(validator, rule.get(), entry, path, "unevaluatedProperties");
        if (!valid && !validator.allErrors()) {
          return false;
        }
      }
    }
    return valid;
  }
}
