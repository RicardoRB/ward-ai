package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds fake {@link UnitTypeFacts} from the {@code types.json} next to a rule fixture, so
 * type-aware rules are tested without a resolver, a classpath or {@code node_modules}.
 *
 * <p>The file is one object whose keys are {@code line:column} (every span starting there) or
 * {@code line:column-line:column} (that exact span), and whose values are fact objects:
 *
 * <pre>{@code
 * {
 *   "3:12": {
 *     "type": {"name": "java.lang.String"},
 *     "call": {"declaring": {"name": "java.lang.String"}, "name": "equals",
 *              "params": [{"name": "java.lang.Object"}], "returns": {"name": "boolean", "primitive": true}},
 *     "annotations": ["org.jspecify.annotations.Nullable"],
 *     "scopes": ["org.jspecify.annotations.NullMarked"],
 *     "shape": {"kind": "class", "final": true, "supertypes": [{"name": "java.lang.Object"}]},
 *     "overrides": [], "thrown": [{"name": "java.io.IOException"}], "constant": "text"
 *   }
 * }
 * }</pre>
 *
 * <p>A fact left out is unknown. Columns are 1-based, as in {@code Span}.
 */
public final class TypeFactsFixture {
  private TypeFactsFixture() {}

  /**
   * Reads {@code typesJson}.
   *
   * @throws IllegalArgumentException if the file is not well-formed facts
   * @throws UncheckedIOException if the file cannot be read
   */
  public static UnitTypeFacts read(final Path typesJson) {
    try {
      return parse(Files.readString(typesJson));
    } catch (IOException unreadable) {
      throw new UncheckedIOException("Could not read " + typesJson, unreadable);
    }
  }

  /**
   * Parses the content of a {@code types.json}.
   *
   * @throws IllegalArgumentException if it is not well-formed facts
   */
  public static UnitTypeFacts parse(final String json) {
    final Object root = new TypeFactsJsonReader(json).read();
    if (!(root instanceof Map<?, ?> object)) {
      throw new IllegalArgumentException("types.json must be an object");
    }
    final Map<String, SpanFacts> facts = TypeFactsJson.spanFacts(object);
    return new UnitTypeFacts(facts);
  }
}
