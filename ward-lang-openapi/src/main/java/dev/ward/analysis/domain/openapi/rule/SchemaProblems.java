package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.schema.BundledSchemas;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Dialect;
import dev.ward.analysis.domain.openapi.schema.SchemaError;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/** What ajv says about a schema before it uses it: that it is not a schema, or cannot compile. */
final class SchemaProblems {

  private final OpenApiDocument document;

  SchemaProblems(final OpenApiDocument document) {
    this.document = document;
  }

  /** The message of the first problem of {@code schema}, with every reference followed. */
  Optional<String> of(final DocNode schema) {
    final DocNode compiled = inlined(schema);
    final String invalid = invalid(compiled);
    if (!invalid.isEmpty()) {
      return Optional.of("schema is invalid: " + invalid);
    }
    return compileProblem(compiled);
  }

  /** The schema with every reference replaced by its target, which is what Spectral validates. */
  private DocNode inlined(final DocNode node) {
    final DocNode resolved = document.resolve(node);
    return new DocNode(
        resolved.kind(),
        resolved.text(),
        resolved.file(),
        resolved.pointer(),
        resolved.span(),
        resolved.keySpan(),
        resolved.entries().stream().map(entry -> entry.withValue(inlined(entry.value()))).toList(),
        resolved.items().stream().map(this::inlined).toList());
  }

  /** What ajv throws while compiling a schema whose keyword has a value it cannot use. */
  private static Optional<String> compileProblem(final DocNode schema) {
    for (final Nodes.Located located : Nodes.raw(schema)) {
      final Optional<DocNode> format = located.node().get("format");
      if (format.isPresent() && format.get().kind() != DocNode.Kind.STRING) {
        return Optional.of("format value must be [\"string\"]");
      }
      final Optional<String> pattern =
          located
              .node()
              .get("pattern")
              .filter(p -> p.kind() == DocNode.Kind.STRING)
              .map(DocNode::text);
      if (pattern.isPresent()) {
        try {
          Pattern.compile(pattern.get());
        } catch (final PatternSyntaxException exception) {
          return Optional.of(
              "Invalid regular expression: /" + pattern.get() + "/: " + describe(exception));
        }
      }
    }
    return Optional.empty();
  }

  private static String describe(final PatternSyntaxException exception) {
    final String text = exception.getDescription();
    if (text.startsWith("Unclosed group")) {
      return "Unterminated group";
    }
    if (text.startsWith("Dangling meta character")) {
      return "Nothing to repeat";
    }
    if (text.startsWith("Unmatched closing")) {
      return "Unmatched ')'";
    }
    return text.startsWith("Unclosed character class") ? "Unterminated character class" : text;
  }

  /** What ajv says when the schema is not a valid schema of the dialect; empty when it is. */
  private String invalid(final DocNode schema) {
    final boolean current = document.isVersion31();
    final List<SchemaError> problems =
        new JsonSchemaValidator(
                current ? Dialect.DRAFT2020 : Dialect.DRAFT4, false, BundledSchemas.resolver())
            .withoutFormats()
            .validate(
                current ? BundledSchemas.jsonSchema2020() : BundledSchemas.jsonSchemaDraft4(),
                schema);
    return problems.stream()
        .map(problem -> "data" + problem.instancePath() + " " + problem.message())
        .collect(Collectors.joining(", "));
  }
}
