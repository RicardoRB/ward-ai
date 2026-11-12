package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.DocNodeParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** The JSON Schemas of OpenAPI documents that ship with Ward, read once. */
public final class BundledSchemas {

  private static final String OAS30 = "/openapi/schemas/oas-3.0.json";

  private BundledSchemas() {}

  /** The schema of OpenAPI 3.0 documents. */
  public static DocNode oas30() {
    return Holder.OAS30_SCHEMA;
  }

  /** Follows a local {@code #/pointer} {@code $ref} inside {@code root}. */
  public static JsonSchemaValidator.RefResolver localRefs(final DocNode root) {
    return holder ->
        holder
            .get("$ref")
            .map(DocNode::text)
            .filter(ref -> ref.startsWith("#"))
            .flatMap(ref -> Pointers.at(root, ref.substring(1)));
  }

  /** Reads a bundled resource. */
  static DocNode read(final String resource) {
    try (InputStream stream = BundledSchemas.class.getResourceAsStream(resource)) {
      return DocNodeParser.parse(
          resource,
          new String(
              Optional.ofNullable(stream).orElseThrow().readAllBytes(), StandardCharsets.UTF_8));
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  /** Holds the parsed schemas, which are created when first used. */
  private static final class Holder {
    private static final DocNode OAS30_SCHEMA = read(OAS30);
  }
}
