package dev.ward.infrastructure.cli.licenses;

import dev.ward.domain.config.WardException;
import dev.ward.domain.dependency.DependencyScope;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import picocli.CommandLine.Option;

/** Options of the commands that write a document from the dependency inventory. */
public class DocumentOptions {

  @Option(
      names = {"-o", "--output"},
      paramLabel = "<file>",
      description = "Write the document to <file> instead of standard output.")
  private Path output;

  @Option(
      names = "--scopes",
      split = ",",
      paramLabel = "<scope>",
      description =
          "Only include packages of these scopes: runtime, provided, test, development"
              + " (default: every scope).")
  private final Set<String> scopes = new LinkedHashSet<>();

  /** Returns the {@code --output} file, or {@code null} for standard output. */
  public Path output() {
    return output;
  }

  /**
   * Returns the lower-case scope names, empty for every scope.
   *
   * @throws WardException if a name is not an inventory scope
   */
  public Set<String> scopes() {
    final Set<String> valid = new LinkedHashSet<>();
    Arrays.stream(DependencyScope.values())
        .forEach(scope -> valid.add(scope.name().toLowerCase(Locale.ROOT)));
    final Set<String> chosen = new LinkedHashSet<>();
    for (final String scope : scopes) {
      final String name = scope.strip().toLowerCase(Locale.ROOT);
      if (!valid.contains(name)) {
        throw new WardException(
            "--scopes must name scopes from " + valid + ", not '" + scope + "'");
      }
      chosen.add(name);
    }
    return chosen;
  }
}
