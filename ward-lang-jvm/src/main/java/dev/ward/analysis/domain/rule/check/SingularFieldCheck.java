package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each private field that only one method uses, and that method assigns before it reads:
 * the field could be a local variable. A field a framework fills, annotated with picocli's
 * {@code @Option}, {@code @Parameters}, {@code @Spec}, {@code @Mixin}, {@code @ParentCommand} or
 * {@code @ArgGroup}, is not reported.
 *
 * <p>Shortcut: "assigns before it reads" means the first mention of the field in the method is the
 * target of a plain {@code =} assignment whose right side does not mention it; a read that comes
 * first on another branch is not seen. A field that the file mentions outside its class, as the
 * enclosing class of a nested one may, is not reported.
 */
public final class SingularFieldCheck implements UnitCheck {
  private static final String IDENTIFIER = "identifier";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String EXPRESSION = "expression";
  private static final String ASSIGNMENT_TEMPLATE = "^(this\\.)?%s=(?!=)";
  private static final Set<String> SKIPPABLE =
      Set.of(
          "lambdaExpression",
          "switchLabeledRule",
          "switchBlockStatementGroup",
          "catchClause",
          "finallyBlock",
          "classBody"
      );
  private static final Set<String> BRANCH_TOKENS =
      Set.of("'if'", "'while'", "'for'", "'do'", "'switch'", "'try'");
  private static final int THIS_PREFIX = "this.".length();
  private static final Set<String> FRAMEWORK_ANNOTATIONS =
      Set.of("Option", "Parameters", "Spec", "Mixin", "ParentCommand", "ArgGroup");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field declaration
   */
  public SingularFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> MemberDeclarations.body(clazz).stream())
        .flatMap(body -> reportOf(body, root).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode body, final SyntaxNode root) {
    final List<SyntaxNode> members = MemberDeclarations.members(body);
    return members.stream()
        .filter(SingularFieldCheck::isPrivateField)
        .flatMap(field -> MemberDeclarations.declaration(field).stream())
        .filter(
            field ->
                names(field).stream()
                    .anyMatch(
                        name -> isSingular(name, field, members) && !usedOutside(name, body, root)
                    )
        )
        .map(field -> CheckSupport.at(field.span(), message))
        .toList();
  }

  private static boolean isPrivateField(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).contains(MemberDeclarations.PRIVATE)
        && Collections.disjoint(JUnitMembers.annotations(member), FRAMEWORK_ANNOTATIONS)
        && MemberDeclarations.declaration(member)
            .map(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
            .orElse(false);
  }

  private static boolean isSingular(
      final String name,
      final SyntaxNode field,
      final List<SyntaxNode> members
  ) {
    final List<SyntaxNode> users =
        members.stream()
            .flatMap(member -> MemberDeclarations.declaration(member).stream())
            .filter(declaration -> !declaration.equals(field))
            .filter(declaration -> usesName(declaration, name))
            .toList();
    return users.size() == 1
        && MemberDeclarations.METHOD.equals(users.get(0).kind())
        && assigns(users.get(0), name);
  }

  /**
   * Whether code of the file outside the class body mentions the name, as an enclosing class may.
   */
  private static boolean usedOutside(
      final String name,
      final SyntaxNode body,
      final SyntaxNode root
  ) {
    return SyntaxWalk.descendants(root).stream()
        .filter(node -> IDENTIFIER.equals(node.kind()) && name.equals(SyntaxWalk.text(node)))
        .anyMatch(
            node ->
                node.span().startOffset() < body.span().startOffset()
                    || node.span().endOffset() > body.span().endOffset()
        );
  }

  private static boolean usesName(final SyntaxNode declaration, final String name) {
    return SyntaxWalk.descendants(declaration).stream()
        .anyMatch(node -> IDENTIFIER.equals(node.kind()) && name.equals(SyntaxWalk.text(node)));
  }

  private static boolean assigns(final SyntaxNode method, final String name) {
    final Pattern assignment =
        Pattern.compile(String.format(ASSIGNMENT_TEMPLATE, Pattern.quote(name)));
    final Optional<Span> first =
        SyntaxWalk.visits(method, Set.of(EXPRESSION)).stream()
            .filter(visit -> assignment.matcher(SyntaxWalk.text(visit.node())).find())
            .filter(visit -> !conditional(visit.ancestors()))
            .map(visit -> visit.node().span())
            .min(Comparator.comparingInt(Span::startOffset));
    if (first.isEmpty()) {
      return false;
    }
    final List<Integer> mentions =
        SyntaxWalk.descendants(method).stream()
            .filter(node -> IDENTIFIER.equals(node.kind()) && name.equals(SyntaxWalk.text(node)))
            .map(node -> node.span().startOffset())
            .sorted()
            .toList();
    final Span target = first.get();
    return !mentions.isEmpty()
        && mentions.get(0) <= target.startOffset() + THIS_PREFIX
        && mentions.stream()
            .filter(at -> at >= target.startOffset() && at < target.endOffset())
            .count()
        == 1;
  }

  /** Whether the assignment may be skipped: it sits in a branch, loop, handler or lambda. */
  private static boolean conditional(final List<SyntaxNode> ancestors) {
    return ancestors.stream().anyMatch(SingularFieldCheck::branches);
  }

  private static boolean branches(final SyntaxNode node) {
    if (SKIPPABLE.contains(node.kind())) {
      return true;
    }
    return ("statement".equals(node.kind()) || EXPRESSION.equals(node.kind()))
        && !node.children().isEmpty()
        && BRANCH_TOKENS.contains(node.children().get(0).kind())
        || EXPRESSION.equals(node.kind())
        && node.children().stream().anyMatch(child -> "'?'".equals(child.kind()));
  }

  private static List<String> names(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(node -> DECLARATOR_ID.equals(node.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }
}
