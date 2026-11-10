package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rows that the ESLint parity test skips: an approximation line whose reason starts with {@code
 * ESLint parse error} names a row that ESLint cannot run on the fixture.
 */
final class EslintParityApproximations {

  private static final String RESOURCE = "/parity/eslint-approximations.txt";
  private static final String PARSE_ERROR = "ESLint parse error";

  /** The fields of an approximation line: the id, the language and the reason. */
  private static final int FIELDS = 3;

  private EslintParityApproximations() {}

  /** The {@code ward:<language>/<row>} ids of the parse-error approximations. */
  static Set<String> parseErrorRows() {
    final Set<String> rows = new TreeSet<>();
    try (InputStream in = EslintParityApproximations.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        return rows;
      }
      for (final String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        final String[] fields = line.split(" \\| ", FIELDS);
        if (!line.startsWith("#") && fields.length == FIELDS && fields[2].startsWith(PARSE_ERROR)) {
          rows.add(fields[0].trim());
        }
      }
      return rows;
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
