package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;
import java.util.Optional;

/** The keywords that tie properties to each other: dependencies and the names of properties. */
final class DependencyKeywords {

  private DependencyKeywords() {}

  static boolean dependentRequired(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> rules = schema.get("dependentRequired");
    if (!appliesDependentRequired(validator, rules)) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry rule : rules.get().entries()) {
      if (!ruleHolds(validator, data, rule, path)) {
        if (!validator.isAllErrors()) {
          return false;
        }
        valid = false;
      }
    }
    return valid;
  }

  private static boolean appliesDependentRequired(
      final JsonSchemaValidator validator, final Optional<DocNode> rules) {
    return rules.isPresent()
        && rules.get().kind() == DocNode.Kind.OBJECT
        && validator.getDialect() == JsonSchemaValidator.Dialect.DRAFT2020;
  }

  /** Whether the properties a present property needs are present; stops at the first miss. */
  private static boolean ruleHolds(
      final JsonSchemaValidator validator,
      final DocNode data,
      final DocNode.Entry rule,
      final String path) {
    if (data.get(rule.key()).isEmpty()) {
      return true;
    }
    boolean valid = true;
    for (final DocNode needed : rule.value().items()) {
      if (data.get(needed.text()).isEmpty()) {
        validator.getErrors().add(neededProperty(path, rule.key(), needed.text()));
        valid = false;
        if (!validator.isAllErrors()) {
          return false;
        }
      }
    }
    return valid;
  }

  private static SchemaError neededProperty(
      final String path, final String present, final String needed) {
    return SchemaError.about(
        "dependentRequired",
        path,
        "must have property " + needed + " when property " + present + " is present",
        needed);
  }

  static boolean dependentSchemas(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> rules = schema.get("dependentSchemas");
    if (rules.isEmpty()
        || rules.get().kind() != DocNode.Kind.OBJECT
        || validator.getDialect() != JsonSchemaValidator.Dialect.DRAFT2020) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry rule : rules.get().entries()) {
      if (data.get(rule.key()).isPresent()) {
        valid &= validator.check(rule.value(), data, path, validator.scratch());
        if (!valid && !validator.isAllErrors()) {
          return false;
        }
      }
    }
    return valid;
  }

  static boolean propertyNames(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> rule = schema.get("propertyNames");
    if (rule.isEmpty() || validator.getDialect() != JsonSchemaValidator.Dialect.DRAFT2020) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry entry : data.entries()) {
      if (!validator.check(rule.get(), name(entry), path, validator.scratch())) {
        validator.getErrors().add(invalidName(path, entry.key()));
        valid = false;
        if (!validator.isAllErrors()) {
          return false;
        }
      }
    }
    return valid;
  }

  private static SchemaError invalidName(final String path, final String key) {
    return SchemaError.about("propertyNames", path, "property name '" + key + "' is invalid", key);
  }

  /** The key of an entry as the string node a {@code propertyNames} schema is checked against. */
  private static DocNode name(final DocNode.Entry entry) {
    return new DocNode(
        DocNode.Kind.STRING,
        entry.key(),
        entry.value().file(),
        entry.value().pointer(),
        entry.keySpan(),
        Optional.empty(),
        List.of(),
        List.of());
  }

  /** Draft 4 {@code dependencies}: a property list or a schema that applies when a key is there. */
  static boolean dependencies(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> rules = schema.get("dependencies");
    if (rules.isEmpty()
        || rules.get().kind() != DocNode.Kind.OBJECT
        || validator.getDialect() != JsonSchemaValidator.Dialect.DRAFT4) {
      return true;
    }
    boolean valid = true;
    for (final DocNode.Entry rule : rules.get().entries()) {
      if (data.get(rule.key()).isEmpty()) {
        continue;
      }
      if (rule.value().kind() == DocNode.Kind.ARRAY) {
        valid &= needed(validator, rule, data, path);
      } else {
        valid &= validator.check(rule.value(), data, path, validator.scratch());
      }
      if (!valid && !validator.isAllErrors()) {
        return false;
      }
    }
    return valid;
  }

  private static boolean needed(
      final JsonSchemaValidator validator,
      final DocNode.Entry rule,
      final DocNode data,
      final String path) {
    final List<String> missing =
        rule.value().items().stream()
            .map(DocNode::text)
            .filter(name -> data.get(name).isEmpty())
            .toList();
    if (missing.isEmpty()) {
      return true;
    }
    final String noun = rule.value().items().size() == 1 ? "property" : "properties";
    final String all =
        rule.value().items().stream()
            .map(DocNode::text)
            .collect(java.util.stream.Collectors.joining(", "));
    validator
        .getErrors()
        .add(
            SchemaError.about(
                "dependencies",
                path,
                "must have " + noun + " " + all + " when property " + rule.key() + " is present",
                missing.getFirst()));
    return false;
  }
}
