package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each class member that comes out of the order Checkstyle's DeclarationOrder asks for:
 * static variables, then instance variables, then constructors, then methods; within the variables
 * of each kind, public before protected before package before private. Inner types are not checked.
 */
public final class DeclarationOrderCheck implements UnitCheck {

  private static final Set<String> FIELD = Set.of("fieldDeclaration");
  private static final Set<String> CONSTRUCTOR =
      Set.of("constructorDeclaration", "genericConstructorDeclaration");
  private static final Set<String> METHOD = Set.of("methodDeclaration", "genericMethodDeclaration");
  private static final Set<String> ACCESS = Set.of("public", "protected", "private");
  private static final int STATIC_VARIABLE = 0;
  private static final int INSTANCE_VARIABLE = 1;
  private static final int CONSTRUCTOR_GROUP = 2;
  private static final int METHOD_GROUP = 3;
  private static final int PACKAGE_ACCESS = 2;
  private static final int PRIVATE_ACCESS = 3;
  private static final int NONE = -1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public DeclarationOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** The position of a member: its group, its access rank and where it starts. */
  private record Member(int group, int access, SyntaxNode start) {}

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classBody"))) {
      checkBody(visit.node(), matches);
    }
    return List.copyOf(matches);
  }

  private void checkBody(final SyntaxNode body, final List<RuleMatch> matches) {
    int furthest = STATIC_VARIABLE;
    final int[] lastAccess = {NONE, NONE};
    for (final SyntaxNode declaration : body.children()) {
      final Optional<Member> member = member(declaration);
      if (member.isEmpty()) {
        continue;
      }
      final Member current = member.get();
      final boolean wrong =
          current.group() < furthest
              || (current.group() <= INSTANCE_VARIABLE
                  && lastAccess[current.group()] != NONE
                  && current.access() < lastAccess[current.group()]);
      if (wrong) {
        matches.add(CheckSupport.at(current.start().span(), message));
      }
      furthest = Math.max(furthest, current.group());
      if (current.group() <= INSTANCE_VARIABLE) {
        lastAccess[current.group()] = current.access();
      }
    }
  }

  private static Optional<Member> member(final SyntaxNode declaration) {
    if (!"classBodyDeclaration".equals(declaration.kind())) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> member =
        declaration.children().stream()
            .filter(child -> "memberDeclaration".equals(child.kind()))
            .findFirst();
    if (member.isEmpty() || member.get().children().isEmpty()) {
      return Optional.empty();
    }
    final String kind = member.get().children().get(0).kind();
    final int group = group(kind, declaration, member.get());
    if (group < 0) {
      return Optional.empty();
    }
    final int memberStart = SyntaxWalk.leaves(member.get()).get(0).span().startOffset();
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(declaration);
    final List<String> modifiers =
        leaves.stream()
            .filter(leaf -> leaf.span().startOffset() < memberStart)
            .map(SyntaxWalk::text)
            .toList();
    return Optional.of(new Member(group, access(modifiers), leaves.get(0)));
  }

  private static int group(
      final String kind, final SyntaxNode declaration, final SyntaxNode member) {
    if (FIELD.contains(kind)) {
      return isStatic(declaration, member) ? STATIC_VARIABLE : INSTANCE_VARIABLE;
    }
    if (CONSTRUCTOR.contains(kind)) {
      return CONSTRUCTOR_GROUP;
    }
    return METHOD.contains(kind) ? METHOD_GROUP : -1;
  }

  private static boolean isStatic(final SyntaxNode declaration, final SyntaxNode member) {
    return SyntaxWalk.leaves(declaration).stream()
        .takeWhile(leaf -> leaf.span().startOffset() < member.span().startOffset())
        .anyMatch(leaf -> "static".equals(SyntaxWalk.text(leaf)));
  }

  private static int access(final List<String> modifiers) {
    return modifiers.stream()
        .filter(ACCESS::contains)
        .findFirst()
        .map(DeclarationOrderCheck::rank)
        .orElse(PACKAGE_ACCESS);
  }

  private static int rank(final String access) {
    return switch (access) {
      case "public" -> 0;
      case "protected" -> 1;
      default -> PRIVATE_ACCESS;
    };
  }
}
