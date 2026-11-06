package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks the placement and order of module imports among the other imports. Module imports sit
 * above every type and static import when {@code top}, below them otherwise; they are sorted by
 * name; and with {@code separated} a blank line divides the module block from the other imports.
 */
public final class ModuleImportOrderCheck implements RuleCheck {

  private final boolean top;
  private final boolean separated;
  private final String message;

  /**
   * Creates the check.
   *
   * @param top whether module imports come before the other imports
   * @param separated whether a blank line divides the module imports from the other imports
   * @param message the message; {@code {value}} is the imported module or type name
   */
  public ModuleImportOrderCheck(final boolean top, final boolean separated, final String message) {
    this.top = top;
    this.separated = separated;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return check(root, List.of());
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    return check(root, ImportLines.of(unit));
  }

  private List<RuleMatch> check(final SyntaxNode root, final List<String> lines) {
    final List<RuleMatch> matches = new ArrayList<>();
    final List<ImportDeclaration> all = ImportDeclaration.all(root);
    final int lastType = lastTypeIndex(all);
    ImportDeclaration previous = null;
    ImportDeclaration previousModule = null;
    boolean sawType = false;
    for (int index = 0; index < all.size(); index++) {
      final ImportDeclaration declaration = all.get(index);
      final boolean misplaced = declaration.isModule() && (top ? sawType : index < lastType);
      final boolean unsorted =
          declaration.isModule()
              && previousModule != null
              && previousModule.name().compareTo(declaration.name()) > 0;
      if (misplaced
          || unsorted
          || (previous != null && splitUnseparated(previous, declaration, lines))) {
        matches.add(
            CheckSupport.at(
                declaration.span(), CheckSupport.fill(message, declaration.name(), "")));
      }
      sawType |= !declaration.isModule();
      previousModule = declaration.isModule() ? declaration : previousModule;
      previous = declaration;
    }
    return List.copyOf(matches);
  }

  /** Returns the index of the last type or static import, or -1 when there is none. */
  private static int lastTypeIndex(final List<ImportDeclaration> all) {
    int last = -1;
    for (int index = 0; index < all.size(); index++) {
      if (!all.get(index).isModule()) {
        last = index;
      }
    }
    return last;
  }

  private boolean splitUnseparated(
      final ImportDeclaration previous, final ImportDeclaration current, final List<String> lines) {
    return separated
        && previous.isModule() != current.isModule()
        && !lines.isEmpty()
        && ImportLines.blankBetween(lines, previous.span().endLine(), current.span().startLine())
            == 0;
  }
}
