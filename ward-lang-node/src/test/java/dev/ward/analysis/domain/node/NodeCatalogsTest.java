package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.node.NodeCatalogs.CatalogEntry;
import org.junit.jupiter.api.Test;

/** The pnpm-workspace.yaml catalog reader. */
final class NodeCatalogsTest {

  private static final int REACT_LINE = 4;
  private static final int LODASH_ONLY_LINE = 3;
  private static final int NAMED_REACT_LINE = 4;
  private static final int LODASH_LINE = 5;
  private static final int REACT17_LINE = 8;
  private static final int REACT_DOM_LINE = 9;
  private static final int ENTRY_COLUMN = 3;
  private static final int NAMED_ENTRY_COLUMN = 5;
  private static final String WORKSPACE =
      """
      packages:
        - "packages/*"
      catalog:
        react: ^18.2.0
        'lodash': ^4.17.21 # pinned
      catalogs:
        react17:
          react: ^17.0.2
          react-dom: ^17.0.2
      """;

  @Test
  void readsTheDefaultCatalogAndNamedCatalogsWithPositions() {
    assertThat(NodeCatalogs.parse(WORKSPACE))
        .containsExactly(
            new CatalogEntry("default", "react", "^18.2.0", REACT_LINE, ENTRY_COLUMN),
            new CatalogEntry("default", "lodash", "^4.17.21", LODASH_LINE, ENTRY_COLUMN),
            new CatalogEntry("react17", "react", "^17.0.2", REACT17_LINE, NAMED_ENTRY_COLUMN),
            new CatalogEntry(
                "react17", "react-dom", "^17.0.2", REACT_DOM_LINE, NAMED_ENTRY_COLUMN));
  }

  @Test
  void ignoresOtherSectionsAndComments() {
    final String text = "# top comment\npackages:\n  - a\n  x: y\n";

    assertThat(NodeCatalogs.parse(text)).isEmpty();
  }

  @Test
  void readsNothingFromAnEmptyFile() {
    assertThat(NodeCatalogs.parse("")).isEmpty();
  }

  @Test
  void skipsEntriesWithoutAVersion() {
    final String text = "catalog:\n  react:\n  lodash: ^4\n";

    assertThat(NodeCatalogs.parse(text))
        .containsExactly(
            new CatalogEntry("default", "lodash", "^4", LODASH_ONLY_LINE, ENTRY_COLUMN));
  }

  @Test
  void ignoresAHeaderWithoutAChildOrAStrayEntryUnderCatalogs() {
    final String text = "catalogs:\n  wrong: no-colon-here\n  named:\n    react: ^17\n";

    assertThat(NodeCatalogs.parse(text))
        .containsExactly(
            new CatalogEntry("named", "react", "^17", NAMED_REACT_LINE, NAMED_ENTRY_COLUMN));
  }
}
