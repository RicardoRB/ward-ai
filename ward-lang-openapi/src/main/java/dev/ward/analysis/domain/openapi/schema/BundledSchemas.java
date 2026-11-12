package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.DocNodeParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The JSON Schemas of OpenAPI documents that ship with Ward, read once and found by their id. */
public final class BundledSchemas {

  private static final String DIRECTORY = "/openapi/schemas/";
  private static final String OAS30 = "oas-3.0.json";
  private static final String OAS31 = "oas-3.1.json";
  private static final List<String> FILES =
      List.of(
          OAS30,
          OAS31,
          "oas-3.1-dialect.json",
          "oas-3.1-meta.json",
          "stoplight-2020-12.json",
          "stoplight-2020-12-validation.json",
          "json-schema-2020-12.json",
          "json-schema-2020-12-core.json",
          "json-schema-2020-12-validation.json",
          "json-schema-draft-04.json",
          "json-schema-2020-12-applicator.json",
          "json-schema-2020-12-unevaluated.json",
          "json-schema-2020-12-meta-data.json",
          "json-schema-2020-12-format-annotation.json",
          "json-schema-2020-12-content.json");

  private BundledSchemas() {}

  /** The schema of OpenAPI 3.0 documents. */
  public static DocNode oas30() {
    return Registry.BY_FILE.get(DIRECTORY + OAS30);
  }

  /** The schema of OpenAPI 3.1 documents. */
  public static DocNode oas31() {
    return Registry.BY_FILE.get(DIRECTORY + OAS31);
  }

  /** The JSON Schema 2020-12 meta-schema, which a schema of OpenAPI 3.1 must satisfy. */
  public static DocNode jsonSchema2020() {
    return Registry.BY_FILE.get(DIRECTORY + "json-schema-2020-12.json");
  }

  /** The JSON Schema draft 4 meta-schema, which a schema of OpenAPI 3.0 must satisfy. */
  public static DocNode jsonSchemaDraft4() {
    return Registry.BY_FILE.get(DIRECTORY + "json-schema-draft-04.json");
  }

  /** Follows {@code $ref} and {@code $dynamicRef} between the bundled schemas. */
  public static JsonSchemaValidator.RefResolver resolver() {
    return new Resolver();
  }

  /** Finds a reference by the id of a resource and a pointer into it. */
  private static final class Resolver implements JsonSchemaValidator.RefResolver {

    @Override
    public Optional<DocNode> resolve(final DocNode holder) {
      return holder.get("$ref").map(DocNode::text).flatMap(ref -> find(holder.file(), ref));
    }

    @Override
    public Optional<DocNode> dynamic(final String anchor, final List<String> scope) {
      for (final String file : scope) {
        final DocNode root = Registry.BY_FILE.get(file);
        if (root != null
            && root.get("$dynamicAnchor").filter(name -> anchor.equals(name.text())).isPresent()) {
          return Optional.of(root);
        }
      }
      return Optional.empty();
    }

    /** Resolves a relative reference against the id of the resource it is written in. */
    private static String absolute(final String file, final String id) {
      final DocNode here = Registry.BY_FILE.get(file);
      final Optional<String> base =
          here == null ? Optional.empty() : here.string("$id").or(() -> here.string("id"));
      return id.contains(":") || base.isEmpty()
          ? id
          : URI.create(base.get()).resolve(id).toString();
    }

    private static Optional<DocNode> find(final String file, final String ref) {
      final int hash = ref.indexOf('#');
      final String id = hash < 0 ? ref : ref.substring(0, hash);
      final String pointer = hash < 0 ? "" : ref.substring(hash + 1);
      final DocNode root =
          id.isEmpty() ? Registry.BY_FILE.get(file) : Registry.BY_ID.get(absolute(file, id));
      if (root == null) {
        return Optional.empty();
      }
      return pointer.startsWith("/")
          ? Pointers.at(root, URLDecoder.decode(pointer, StandardCharsets.UTF_8))
          : Optional.of(root);
    }
  }

  /** The parsed schemas, created when first used. */
  private static final class Registry {
    private static final Map<String, DocNode> BY_FILE = new HashMap<>();
    private static final Map<String, DocNode> BY_ID = new HashMap<>();

    static {
      for (final String name : FILES) {
        final DocNode root = read(DIRECTORY + name);
        BY_FILE.put(DIRECTORY + name, root);
        root.string("$id").or(() -> root.string("id")).ifPresent(id -> BY_ID.put(id, root));
      }
    }
  }

  private static DocNode read(final String resource) {
    try (InputStream stream = BundledSchemas.class.getResourceAsStream(resource)) {
      return DocNodeParser.parse(
          resource,
          new String(
              Optional.ofNullable(stream).orElseThrow().readAllBytes(), StandardCharsets.UTF_8));
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
