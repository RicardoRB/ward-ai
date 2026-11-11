package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a private, non-static, non-final, non-volatile field that could be final, as PMD's {@code
 * ImmutableField} does: a field with an initializer that nothing assigns, or a field that every
 * constructor assigns exactly once with a statement of its body and nothing else assigns. A blank
 * field that nothing assigns is not reported, and neither is a field that a framework fills in:
 * annotated with picocli's {@code @Option} or {@code @Parameters} or Lombok's {@code @Setter}, or
 * in a class annotated with Lombok's {@code @Builder}, {@code @Data}, {@code @Setter} or
 * {@code @Value}.
 *
 * <p>shortcut: PMD's data flow is approximated by "exactly one top-level assignment per
 * constructor"; a field assigned conditionally is not reported.
 */
public final class ImmutableFieldCheck implements UnitCheck {

  private static final String IDENTIFIER = "identifier";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String EXPRESSION = "expression";
  private static final String VOLATILE = "volatile";
  private static final String ASSIGNMENT_TEMPLATE =
      "^([\\w$]+\\.)?%s(=(?!=)|[-+*/%%&|^]=|>>>?=|<<=|\\+\\+|--)|^(\\+\\+|--)([\\w$]+\\.)?%s$";
  private static final Set<String> FIELD_ANNOTATIONS = Set.of("Option", "Parameters", "Setter");
  private static final Set<String> CLASS_ANNOTATIONS = Set.of("Builder", "Data", "Setter", "Value");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field declaration
   */
  public ImmutableFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .filter(visit -> !injectedClass(visit.ancestors()))
        .flatMap(visit -> MemberDeclarations.body(visit.node()).stream())
        .flatMap(body -> reportOf(body).stream())
        .toList();
  }

  private static boolean injectedClass(final List<SyntaxNode> ancestors) {
    return MemberDeclarations.wrapper(ancestors)
        .filter(
            wrapper -> !Collections.disjoint(JUnitMembers.annotations(wrapper), CLASS_ANNOTATIONS))
        .isPresent();
  }

  private List<RuleMatch> reportOf(final SyntaxNode body) {
    final List<SyntaxNode> members = MemberDeclarations.members(body);
    final List<SyntaxNode> constructors =
        members.stream()
            .flatMap(member -> MemberDeclarations.declaration(member).stream())
            .filter(declaration -> MemberDeclarations.CONSTRUCTOR.equals(declaration.kind()))
            .toList();
    return members.stream()
        .filter(ImmutableFieldCheck::isCandidate)
        .flatMap(field -> MemberDeclarations.declaration(field).stream())
        .filter(
            field -> declarators(field).stream().anyMatch(d -> couldBeFinal(d, body, constructors)))
        .map(field -> CheckSupport.at(field.span(), message))
        .toList();
  }

  private static boolean isCandidate(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return modifiers.contains(MemberDeclarations.PRIVATE)
        && !modifiers.contains(MemberDeclarations.FINAL)
        && !modifiers.contains(MemberDeclarations.STATIC)
        && !modifiers.contains(VOLATILE)
        && Collections.disjoint(JUnitMembers.annotations(member), FIELD_ANNOTATIONS)
        && MemberDeclarations.declaration(member)
            .map(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
            .orElse(false);
  }

  private static List<SyntaxNode> declarators(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(node -> DECLARATOR.equals(node.kind()))
        .toList();
  }

  private static boolean couldBeFinal(
      final SyntaxNode declarator, final SyntaxNode body, final List<SyntaxNode> constructors) {
    final Optional<String> name = name(declarator);
    if (name.isEmpty()) {
      return false;
    }
    final Pattern assignment = assignment(name.get());
    final List<SyntaxWalk.Visit> writes =
        SyntaxWalk.visits(body, Set.of(EXPRESSION)).stream()
            .filter(visit -> assignment.matcher(SyntaxWalk.text(visit.node())).find())
            .toList();
    if (writes.isEmpty()) {
      return declarator.children().size() > 1;
    }
    return writes.stream().allMatch(visit -> ConstructorWrites.topLevel(visit.ancestors()))
        && ConstructorWrites.eachAssignsOnce(writes, constructors);
  }

  private static Optional<String> name(final SyntaxNode declarator) {
    return SyntaxWalk.descendants(declarator).stream()
        .filter(node -> DECLARATOR_ID.equals(node.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .findFirst();
  }

  private static Pattern assignment(final String name) {
    return Pattern.compile(
        String.format(ASSIGNMENT_TEMPLATE, Pattern.quote(name), Pattern.quote(name)));
  }
}
