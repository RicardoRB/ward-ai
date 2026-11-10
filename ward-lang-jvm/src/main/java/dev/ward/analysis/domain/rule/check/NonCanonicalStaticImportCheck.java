package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a static import of a method through a class that does not declare it, as Error Prone's
 * NonCanonicalStaticImport does: the import names {@code Sub.method} while the method is declared
 * by {@code Super}. It judges the methods the file calls by their bare name; fields and types, and
 * imports whose uses have unknown targets, are not reported.
 */
public final class NonCanonicalStaticImportCheck extends FactsCheck {

  private static final Pattern IMPORT = Pattern.compile("^importstatic([\\w.]+)\\.(\\w+);?$");
  private static final String IMPORT_KIND = "importDeclaration";
  private static final String EXPRESSION = "expression";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the import; {@code {value}} is the declaring type
   */
  public NonCanonicalStaticImportCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<SyntaxNode> calls =
        SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream().map(SyntaxWalk.Visit::node).toList();
    return SyntaxWalk.visits(root, Set.of(IMPORT_KIND)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(node -> reportOf(node, calls, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(
      final SyntaxNode node, final List<SyntaxNode> calls, final UnitTypeFacts types) {
    final Matcher imported = IMPORT.matcher(SyntaxWalk.text(node));
    if (!imported.matches()) {
      return List.of();
    }
    final String prefix = imported.group(2) + "(";
    return calls.stream()
        .filter(call -> SyntaxWalk.text(call).startsWith(prefix))
        .flatMap(call -> types.callTarget(call.span()).stream())
        .filter(target -> target.isStatic() && imported.group(2).equals(target.name()))
        .filter(target -> !imported.group(1).equals(target.declaringType().name()))
        .findFirst()
        .map(
            target ->
                CheckSupport.at(
                    node.span(), CheckSupport.fill(message, target.declaringType().name(), "")))
        .stream()
        .toList();
  }
}
