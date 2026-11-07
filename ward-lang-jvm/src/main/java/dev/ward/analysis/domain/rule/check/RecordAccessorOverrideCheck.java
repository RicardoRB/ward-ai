package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each explicitly declared record accessor, a method named after a component with no
 * parameters, that lacks {@code @Override}.
 */
public final class RecordAccessorOverrideCheck implements RuleCheck {

  private static final String RECORD = "recordDeclaration";
  private static final String RECORD_BODY = "recordBody";
  private static final String RECORD_HEADER = "recordHeader";
  private static final String IDENTIFIER = "identifier";
  private static final String OVERRIDE = "@Override";
  private static final String NO_PARAMETERS = "()";
  private static final Set<String> MODIFIER_KINDS = Set.of("modifier", "classOrInterfaceModifier");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public RecordAccessorOverrideCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(RECORD))) {
      final SyntaxNode record = visit.node();
      final Set<String> components = componentNames(record);
      for (final SyntaxNode body : record.children()) {
        if (RECORD_BODY.equals(body.kind())) {
          body.children().forEach(member -> checkMember(member, components, matches));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static Set<String> componentNames(final SyntaxNode record) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode header : record.children()) {
      if (RECORD_HEADER.equals(header.kind())) {
        header.children().stream()
            .flatMap(list -> list.children().stream())
            .filter(component -> "recordComponent".equals(component.kind()))
            .forEach(component -> names.add(lastIdentifier(component)));
      }
    }
    return Set.copyOf(names);
  }

  private static String lastIdentifier(final SyntaxNode node) {
    final List<SyntaxNode> identifiers =
        node.children().stream().filter(child -> IDENTIFIER.equals(child.kind())).toList();
    return identifiers.isEmpty() ? "" : SyntaxWalk.text(identifiers.get(identifiers.size() - 1));
  }

  private void checkMember(
      final SyntaxNode member, final Set<String> components, final List<RuleMatch> matches) {
    if (!"classBodyDeclaration".equals(member.kind())) {
      return;
    }
    member.children().stream()
        .filter(child -> "memberDeclaration".equals(child.kind()))
        .flatMap(child -> child.children().stream())
        .filter(child -> "methodDeclaration".equals(child.kind()))
        .filter(method -> isAccessor(method, components))
        .filter(method -> !hasOverride(member))
        .forEach(method -> matches.add(CheckSupport.at(method.span(), message)));
  }

  private static boolean isAccessor(final SyntaxNode method, final Set<String> components) {
    final boolean named =
        method.children().stream()
            .filter(child -> IDENTIFIER.equals(child.kind()))
            .anyMatch(child -> components.contains(SyntaxWalk.text(child)));
    final boolean noParameters =
        method.children().stream()
            .filter(child -> "formalParameters".equals(child.kind()))
            .allMatch(child -> NO_PARAMETERS.equals(SyntaxWalk.text(child)));
    return named && noParameters;
  }

  private static boolean hasOverride(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> MODIFIER_KINDS.contains(child.kind()))
        .anyMatch(child -> OVERRIDE.equals(SyntaxWalk.text(child).trim()));
  }
}
