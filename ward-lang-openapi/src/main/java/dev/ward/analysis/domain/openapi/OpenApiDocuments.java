package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;
import java.util.Optional;

/**
 * Loads an {@link OpenApiDocument}: parses the entry file and every file its {@code $ref}s name,
 * resolves each reference, and returns the document or the errors that make it unusable. A
 * reference that cannot be read is an error, never a pass: an unreadable file, a path outside the
 * project root, an {@code http(s)} URL (never fetched), a missing pointer, a syntax error.
 *
 * <p>This is the entry point of the document model. A lint rule gets its document from {@link
 * #fromTrees}, and the diff of two documents loads each one from a project-file reader (the base
 * from a Git revision) with {@link #load}.
 */
public final class OpenApiDocuments {

  private OpenApiDocuments() {}

  /**
   * Loads the document whose entry file is {@code entry}, reading files through {@code reader}.
   *
   * @param entry the project-relative path of the entry file
   * @param reader reads the entry file and the files that references name
   */
  public static LoadResult load(final String entry, final ProjectFileReader reader) {
    return new OpenApiLoader(path -> parsed(path, reader)).load(entry);
  }

  /**
   * Loads the document whose entry file is {@code entry} from syntax trees that {@link YamlParser}
   * already built, such as the units of an analysis run; a file that is not among them cannot be
   * read.
   *
   * @param entry the project-relative path of the entry file
   * @param trees the syntax tree of each file, by project-relative path
   */
  public static LoadResult fromTrees(final String entry, final Map<String, SyntaxNode> trees) {
    return new OpenApiLoader(
            path ->
                Optional.ofNullable(trees.get(path))
                    .map(tree -> new OpenApiLoader.Parsed(DocNodes.build(path, tree), null)))
        .load(entry);
  }

  private static Optional<OpenApiLoader.Parsed> parsed(
      final String path, final ProjectFileReader reader) {
    return reader
        .read(path)
        .map(
            text -> {
              final YamlResult result = YamlParser.parse(text);
              return result.valid()
                  ? new OpenApiLoader.Parsed(DocNodes.build(path, result.tree()), null)
                  : new OpenApiLoader.Parsed(null, result.diagnostics().getFirst());
            });
  }
}
