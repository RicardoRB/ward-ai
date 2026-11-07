package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each inner type that a later member of its body follows. Fields, constructors, methods
 * and initializer blocks come before the inner types of a class or interface.
 */
public final class InnerTypeLastCheck implements RuleCheck {

  private static final Set<String> BODY_WRAPPERS =
      Set.of("classBodyDeclaration", "interfaceBodyDeclaration");
  private static final Set<String> HOLDERS =
      Set.of("memberDeclaration", "interfaceMemberDeclaration");
  private static final String SEMICOLON = ";";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the name of the inner type
   */
  public InnerTypeLastCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      DesignMembers.body(visit.node()).ifPresent(body -> misplaced(body, matches));
    }
    return List.copyOf(matches);
  }

  private void misplaced(final SyntaxNode body, final List<RuleMatch> matches) {
    final List<SyntaxNode> wrappers =
        body.children().stream()
            .filter(child -> BODY_WRAPPERS.contains(child.kind()))
            .filter(child -> !SEMICOLON.equals(SyntaxWalk.text(child)))
            .toList();
    int lastMember = -1;
    for (int index = 0; index < wrappers.size(); index++) {
      if (innerType(wrappers.get(index)).isEmpty()) {
        lastMember = index;
      }
    }
    for (int index = 0; index < lastMember; index++) {
      innerType(wrappers.get(index))
          .ifPresent(
              type ->
                  matches.add(
                      CheckSupport.at(
                          type.span(), CheckSupport.fill(message, JUnitMembers.name(type), ""))));
    }
  }

  /** Returns the declaration of an inner type that a member wrapper holds, when it holds one. */
  private static Optional<SyntaxNode> innerType(final SyntaxNode wrapper) {
    return wrapper.children().stream()
        .filter(child -> HOLDERS.contains(child.kind()))
        .flatMap(holder -> holder.children().stream())
        .filter(declaration -> DesignMembers.TYPE_KINDS.contains(declaration.kind()))
        .findFirst();
  }
}
