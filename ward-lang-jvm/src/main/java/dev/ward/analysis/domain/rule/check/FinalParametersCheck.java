package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a parameter of a method or constructor with a body that is not {@code final} although the
 * body never assigns it, as PMD's {@code MethodArgumentCouldBeFinal} does; Checkstyle's {@code
 * FinalParameters} reports it even when it is assigned. Lambda and catch parameters are not
 * reported.
 */
public final class FinalParametersCheck implements UnitCheck {
  private static final String PARAMETER = "formalParameter";
  private static final String MODIFIER = "variableModifier";
  private static final String FINAL = "'final'";
  private static final String BLOCK = "block";
  private static final String ASSIGNMENT_TEMPLATE =
      "^([\\w$]+\\.)?%s(=(?!=)|[-+*/%%&|^]=|>>>?=|<<=|\\+\\+|--)|^(\\+\\+|--)([\\w$]+\\.)?%s$";
  private static final Set<String> PARAMETER_LISTS =
      Set.of("formalParameters", "formalParameterList");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the parameter name
   */
  public FinalParametersCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(PARAMETER)).stream()
        .filter(visit -> !isFinal(visit.node()))
        .flatMap(visit -> owner(visit.ancestors()).stream().map(o -> report(visit.node(), o)))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<RuleMatch> report(final SyntaxNode parameter, final SyntaxNode owner) {
    final String name = name(parameter);
    return assigned(name, owner) || !used(name, owner)
        ? Optional.empty()
        : Optional.of(CheckSupport.at(parameter.span(), CheckSupport.fill(message, name, "")));
  }

  private static boolean isFinal(final SyntaxNode parameter) {
    return parameter.children().stream()
        .filter(child -> MODIFIER.equals(child.kind()))
        .anyMatch(child -> child.children().stream().anyMatch(c -> FINAL.equals(c.kind())));
  }

  /** The method or constructor that has a body and declares the parameter, if it is one. */
  private static Optional<SyntaxNode> owner(final List<SyntaxNode> up) {
    for (int index = up.size() - 1; index >= 0; index--) {
      final SyntaxNode node = up.get(index);
      if (!PARAMETER_LISTS.contains(node.kind())) {
        final boolean declares =
            MemberDeclarations.METHOD.equals(node.kind())
                || MemberDeclarations.CONSTRUCTOR.equals(node.kind());
        return declares && hasBody(node) ? Optional.of(node) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static boolean hasBody(final SyntaxNode declaration) {
    return SyntaxWalk.descendants(declaration).stream().anyMatch(node -> BLOCK.equals(node.kind()));
  }

  private static String name(final SyntaxNode parameter) {
    return SyntaxWalk.descendants(parameter).stream()
        .filter(node -> "variableDeclaratorId".equals(node.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> "identifier".equals(child.kind()))
        .map(SyntaxWalk::text)
        .findFirst()
        .orElse("");
  }

  private static boolean used(final String name, final SyntaxNode owner) {
    return SyntaxWalk.descendants(owner).stream()
        .filter(node -> BLOCK.equals(node.kind()))
        .flatMap(block -> SyntaxWalk.descendants(block).stream())
        .anyMatch(node -> "identifier".equals(node.kind()) && name.equals(SyntaxWalk.text(node)));
  }

  private static boolean assigned(final String name, final SyntaxNode owner) {
    final Pattern assignment =
        Pattern.compile(
            String.format(ASSIGNMENT_TEMPLATE, Pattern.quote(name), Pattern.quote(name)));
    return SyntaxWalk.descendants(owner).stream()
        .filter(node -> "expression".equals(node.kind()))
        .anyMatch(node -> assignment.matcher(SyntaxWalk.text(node)).find());
  }
}
