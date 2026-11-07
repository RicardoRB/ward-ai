package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a parameter declared as {@code Iterable<Path>}, because a {@code Path} is itself an
 * {@code Iterable<Path>} and a path passed by mistake is then accepted, as Error Prone's
 * IterablePathParameter does. A simple {@code Path} counts only when the file imports {@code
 * java.nio.file.Path}.
 */
public final class JavaIterablePathCheck implements RuleCheck {

  private static final String PATH = "java.nio.file.Path";
  private static final Pattern SIMPLE = Pattern.compile("^(?:java\\.lang\\.)?Iterable<Path>$");
  private static final Pattern QUALIFIED =
      Pattern.compile("^(?:java\\.lang\\.)?Iterable<java\\.nio\\.file\\.Path>$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaIterablePathCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final boolean imported = JavaAnnotations.resolves(JavaAnnotations.imports(root), "Path", PATH);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("formalParameter"))) {
      JavaNodes.kid(visit.node(), "typeType")
          .filter(type -> declaresPaths(SyntaxWalk.text(type), imported))
          .ifPresent(type -> matches.add(CheckSupport.at(type.span(), message)));
    }
    return List.copyOf(matches);
  }

  private static boolean declaresPaths(final String type, final boolean imported) {
    return QUALIFIED.matcher(type).matches() || imported && SIMPLE.matcher(type).matches();
  }
}
