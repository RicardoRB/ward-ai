package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The keywords that need no type of data: const, enum, not, anyOf, oneOf, allOf and if. */
final class Combinators {

  private static final int EXACTLY_ONE = 1;

  private Combinators() {}

  /** Runs them in the order ajv does. */
  static boolean check(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    return Chain.run(
        validator,
        () -> constant(validator, schema, data, path),
        () -> enumeration(validator, schema, data, path),
        () -> not(validator, schema, data, path),
        () -> anyOf(validator, schema, data, path, evaluated),
        () -> oneOf(validator, schema, data, path, evaluated),
        () -> allOf(validator, schema, data, path, evaluated),
        () -> conditional(validator, schema, data, path, evaluated));
  }

  private static boolean stop(final JsonSchemaValidator validator, final boolean valid) {
    return !valid && !validator.isAllErrors();
  }

  private static boolean constant(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> expected = schema.get("const");
    if (expected.isEmpty() || JsonValues.equal(expected.get(), data)) {
      return true;
    }
    validator.getErrors().add(SchemaError.of("const", path, "must be equal to constant"));
    return false;
  }

  private static boolean enumeration(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> allowed = schema.get("enum");
    if (allowed.isEmpty()
        || allowed.get().kind() != DocNode.Kind.ARRAY
        || allowed.get().items().stream().anyMatch(item -> JsonValues.equal(item, data))) {
      return true;
    }
    validator
        .getErrors()
        .add(
            new SchemaError(
                "enum",
                path,
                "must be equal to one of the allowed values",
                "",
                allowed.get().items(),
                false));
    return false;
  }

  private static boolean not(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path) {
    final Optional<DocNode> inner = schema.get("not");
    if (inner.isEmpty()) {
      return true;
    }
    final int before = validator.getErrors().size();
    validator.changeNotDepth(1);
    final boolean passes = validator.check(inner.get(), data, path, validator.scratch());
    validator.changeNotDepth(-1);
    truncate(validator, before);
    if (!passes) {
      return true;
    }
    validator.getErrors().add(SchemaError.of("not", path, "must NOT be valid"));
    return false;
  }

  private static void truncate(final JsonSchemaValidator validator, final int size) {
    final List<SchemaError> errors = validator.getErrors();
    while (errors.size() > size) {
      errors.removeLast();
    }
  }

  private static boolean anyOf(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> branches = schema.get("anyOf");
    if (branches.isEmpty() || branches.get().kind() != DocNode.Kind.ARRAY) {
      return true;
    }
    final int before = validator.getErrors().size();
    boolean passed = false;
    for (final DocNode branch : branches.get().items()) {
      final Set<String> own = validator.scratch();
      if (validator.check(branch, data, path, own)) {
        passed = true;
        evaluated.addAll(own);
        if (!validator.isAllErrors()) {
          break;
        }
      }
    }
    if (passed) {
      truncate(validator, before);
      return true;
    }
    validator
        .getErrors()
        .add(new SchemaError("anyOf", path, "must match a schema in anyOf", "", List.of(), true));
    return false;
  }

  private static boolean oneOf(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> branches = schema.get("oneOf");
    if (branches.isEmpty() || branches.get().kind() != DocNode.Kind.ARRAY) {
      return true;
    }
    final int before = validator.getErrors().size();
    final List<Set<String>> passing = new ArrayList<>();
    for (final DocNode branch : branches.get().items()) {
      final Set<String> own = validator.scratch();
      if (validator.check(branch, data, path, own)) {
        passing.add(own);
      }
    }
    if (passing.size() == EXACTLY_ONE) {
      truncate(validator, before);
      evaluated.addAll(passing.getFirst());
      return true;
    }
    validator
        .getErrors()
        .add(
            new SchemaError(
                "oneOf",
                path,
                "must match exactly one schema in oneOf",
                "",
                List.of(),
                passing.isEmpty()));
    return false;
  }

  private static boolean allOf(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> branches = schema.get("allOf");
    if (branches.isEmpty() || branches.get().kind() != DocNode.Kind.ARRAY) {
      return true;
    }
    boolean valid = true;
    for (final DocNode branch : branches.get().items()) {
      valid &= validator.check(branch, data, path, evaluated);
      if (stop(validator, valid)) {
        return false;
      }
    }
    return valid;
  }

  private static boolean conditional(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final DocNode data,
      final String path,
      final Set<String> evaluated) {
    final Optional<DocNode> condition = schema.get("if");
    if (condition.isEmpty()) {
      return true;
    }
    final int before = validator.getErrors().size();
    final Set<String> own = validator.scratch();
    final boolean holds = validator.check(condition.get(), data, path, own);
    truncate(validator, before);
    final String branch = holds ? "then" : "else";
    if (holds) {
      evaluated.addAll(own);
    }
    final Optional<DocNode> chosen = schema.get(branch);
    if (chosen.isEmpty()) {
      return true;
    }
    final Set<String> read = validator.scratch();
    if (validator.check(chosen.get(), data, path, read)) {
      evaluated.addAll(read);
      return true;
    }
    validator.getErrors().add(SchemaError.of("if", path, "must match \"" + branch + "\" schema"));
    return false;
  }
}
