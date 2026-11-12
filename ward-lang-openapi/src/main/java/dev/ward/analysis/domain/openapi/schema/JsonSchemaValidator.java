package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A JSON Schema validator over the nodes of a document that reports the errors the way ajv 8 does:
 * the same keyword order, the same messages, and, unless {@code allErrors} is set, only the first
 * failing keyword of each schema. It reads draft 4 (the schemas of OpenAPI 3.0) and draft 2020-12
 * (OpenAPI 3.1) and needs no library.
 *
 * <p>An instance validates one value at a time and is not thread-safe.
 */
public final class JsonSchemaValidator {

  private final Dialect dialect;
  private final boolean allErrors;
  private final RefResolver refs;
  private final List<SchemaError> errors = new ArrayList<>();
  private Relax relax = Relax.NONE;
  private int notDepth;
  private boolean formats = true;
  private final List<String> scope = new ArrayList<>();

  /**
   * Creates a validator.
   *
   * @param dialect the dialect of the schemas
   * @param allErrors whether to report every failing keyword rather than the first of each schema
   * @param refs how to follow a {@code $ref}
   */
  public JsonSchemaValidator(
      final Dialect dialect, final boolean allErrors, final RefResolver refs) {
    this.dialect = dialect;
    this.allErrors = allErrors;
    this.refs = refs;
  }

  /** The JSON Schema dialects the validator reads. */
  public enum Dialect {
    /** Draft 4, where a {@code $ref} replaces its siblings and {@code nullable} is read. */
    DRAFT4,
    /** Draft 2020-12, where a {@code $ref} has siblings. */
    DRAFT2020
  }

  /** Which properties a {@code required} list may leave out because of how the value is used. */
  public enum Relax {
    /** Every required property is required. */
    NONE,
    /** A request: a {@code readOnly} property is not required. */
    READ_ONLY,
    /** A response: a {@code writeOnly} property is not required. */
    WRITE_ONLY
  }

  /** Finds the schema a {@code $ref} object points at. */
  @FunctionalInterface
  public interface RefResolver {
    /** Returns the target of the reference held by {@code holder}, or empty when there is none. */
    Optional<DocNode> resolve(DocNode holder);

    /**
     * Returns the schema a {@code $dynamicRef} to {@code anchor} reaches: the outermost resource of
     * the dynamic {@code scope} (the files of the schemas being checked, outermost first) that
     * declares the {@code $dynamicAnchor}.
     */
    default Optional<DocNode> dynamic(final String anchor, final List<String> scope) {
      return Optional.empty();
    }
  }

  /** A reference that points nowhere. */
  public static final class UnresolvedReferenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UnresolvedReferenceException(final String reference) {
      super("can't resolve reference " + reference);
    }
  }

  /** Makes {@code format} an annotation that is not checked; returns this validator. */
  public JsonSchemaValidator withoutFormats() {
    this.formats = false;
    return this;
  }

  boolean isAssertingFormats() {
    return formats;
  }

  /** Sets which properties {@code required} may leave out; returns this validator. */
  public JsonSchemaValidator relaxing(final Relax mode) {
    this.relax = mode;
    return this;
  }

  /**
   * Validates {@code data} against {@code schema}.
   *
   * @return the errors, in ajv order; empty when the value is valid
   * @throws UnresolvedReferenceException if a {@code $ref} of the schema points nowhere
   */
  public List<SchemaError> validate(final DocNode schema, final DocNode data) {
    errors.clear();
    check(schema, data, "", scratch());
    return List.copyOf(errors);
  }

  Relax getRelax() {
    return relax;
  }

  boolean isInsideNot() {
    return notDepth > 0;
  }

  void changeNotDepth(final int delta) {
    notDepth += delta;
  }

  Dialect getDialect() {
    return dialect;
  }

  boolean isAllErrors() {
    return allErrors;
  }

  List<SchemaError> getErrors() {
    return errors;
  }

  /** A fresh set for the names of the properties a schema reads. */
  Set<String> scratch() {
    return new HashSet<>();
  }

  /** Validates one value; {@code evaluated} collects the names of the properties a schema read. */
  boolean check(
      final DocNode schema, final DocNode data, final String path, final Set<String> evaluated) {
    if (schema.kind() == DocNode.Kind.BOOLEAN) {
      return booleanSchema(schema, path);
    }
    if (schema.kind() != DocNode.Kind.OBJECT) {
      return true;
    }
    final Optional<DocNode> reference =
        schema.get("$ref").filter(ref -> ref.kind() == DocNode.Kind.STRING);
    if (reference.isPresent() && dialect == Dialect.DRAFT4) {
      return check(target(schema, reference.get()), data, path, evaluated);
    }
    final int before = errors.size();
    final boolean entered = scope.isEmpty() || !scope.getLast().equals(schema.file());
    if (entered) {
      scope.add(schema.file());
    }
    final boolean valid =
        Chain.run(
            this,
            () -> Types.check(this, schema, data, path),
            () ->
                reference.isEmpty()
                    || check(target(schema, reference.get()), data, path, evaluated),
            () -> dynamicRef(schema, data, path, evaluated),
            () -> Combinators.check(this, schema, data, path, evaluated),
            () -> Keywords.check(this, schema, data, path, evaluated));
    if (allErrors) {
      ErrorMessages.apply(this, schema, path, before);
    }
    if (entered) {
      scope.removeLast();
    }
    return valid;
  }

  private boolean booleanSchema(final DocNode schema, final String path) {
    if (JsonValues.isTrue(Optional.of(schema))) {
      return true;
    }
    errors.add(SchemaError.of("false schema", path, "boolean schema is false"));
    return false;
  }

  private boolean dynamicRef(
      final DocNode schema, final DocNode data, final String path, final Set<String> evaluated) {
    final Optional<DocNode> reference =
        schema.get("$dynamicRef").filter(ref -> ref.text().startsWith("#"));
    if (reference.isEmpty() || dialect != Dialect.DRAFT2020) {
      return true;
    }
    final String anchor = reference.get().text().substring(1);
    final Optional<DocNode> target = refs.dynamic(anchor, scope);
    return check(
        target.orElseThrow(() -> new UnresolvedReferenceException(reference.get().text())),
        data,
        path,
        evaluated);
  }

  private DocNode target(final DocNode holder, final DocNode reference) {
    return refs.resolve(holder)
        .orElseThrow(() -> new UnresolvedReferenceException(reference.text()));
  }
}
