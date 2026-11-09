package dev.ward.analysis.application.node;

import dev.ward.analysis.domain.rule.check.AriaRoleTable;
import dev.ward.analysis.domain.rule.check.AriaTableParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads the mirrored ARIA tables of Biome 2.5.15 from the module's resources. */
final class AriaTables {

  private static final String ROLES = "/aria/aria-roles.tsv";
  private static final String IMPLICIT = "/aria/aria-implicit-roles.tsv";
  private static final String ATTRIBUTES = "/aria/aria-attributes.tsv";

  private AriaTables() {}

  /** The ARIA role table: the role rows and the implicit element-role rows. */
  static AriaRoleTable load() {
    return new AriaRoleTable(
        AriaTableParser.parseRoles(read(ROLES)),
        AriaTableParser.parseImplicit(read(IMPLICIT)),
        AriaTableParser.parseAttributes(read(ATTRIBUTES)));
  }

  private static List<String> read(final String path) {
    try (InputStream in = AriaTables.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing ARIA table " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
