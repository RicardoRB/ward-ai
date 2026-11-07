package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each member of a class or interface body that follows an inner type. Fields,
 * constructors, methods and initializer blocks come before the inner types, which go last.
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
   * @param message the message, reported at each member that follows an inner type
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
    boolean seenInnerType = false;
    for (final SyntaxNode wrapper : body.children()) {
      if (BODY_WRAPPERS.contains(wrapper.kind()) && !SEMICOLON.equals(SyntaxWalk.text(wrapper))) {
        final Optional<SyntaxNode> inner = innerType(wrapper);
        if (inner.isPresent()) {
          seenInnerType = true;
        } else if (seenInnerType) {
          matches.add(CheckSupport.at(wrapper.span(), CheckSupport.fill(message, "", "")));
        }
      }
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
