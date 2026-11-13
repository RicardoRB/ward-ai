package dev.ward.analysis.domain.openapi;

/** Parses a YAML or JSON text into the nodes of {@link DocNode}, for texts that are not specs. */
public final class DocNodeParser {

  private DocNodeParser() {}

  /**
   * Parses {@code text}.
   *
   * @param file the name the nodes report as their file
   * @param text the YAML or JSON text
   * @return the root node
   * @throws IllegalArgumentException if the text is not valid YAML or JSON
   */
  public static DocNode parse(final String file, final String text) {
    final YamlResult result = YamlParser.parse(text);
    if (result.tree() == null) {
      throw new IllegalArgumentException(file + " is not valid YAML or JSON");
    }
    return DocNodes.build(file, result.tree());
  }
}
