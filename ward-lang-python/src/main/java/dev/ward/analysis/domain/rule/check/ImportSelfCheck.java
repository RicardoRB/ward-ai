package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports a module that imports itself by its dotted path, like Pylint W0406. */
public final class ImportSelfCheck implements UnitCheck {

  private static final Set<String> IMPORTS = Set.of("import_name", "import_from");
  private static final Set<String> DOTTED = Set.of("dotted_name");
  private static final String PACKAGE_INIT = ".__init__";

  private final String message;

  /** Creates the check with the message of each finding. */
  public ImportSelfCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final String module = module(file.path());
    final List<RuleMatch> found = new ArrayList<>();
    if (module.isEmpty()) {
      return found;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, IMPORTS)) {
      for (final SyntaxNode name : FunctionBodies.own(visit.node(), DOTTED, Set.of())) {
        if (module.equals(SyntaxWalk.text(name))) {
          found.add(new RuleMatch(name.span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** The dotted module name of a project-relative path such as {@code pkg/mod.py}. */
  private static String module(final String path) {
    if (path.isEmpty()) {
      return "";
    }
    final String dotted = path.replaceAll("\\.py$", "").replace('/', '.');
    return dotted.endsWith(PACKAGE_INIT)
        ? dotted.substring(0, dotted.length() - PACKAGE_INIT.length())
        : dotted;
  }
}
