package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each method with a given name and no parameters, as Checkstyle's NoClone and NoFinalizer
 * do; with {@code requireSuperCall} it reports only a method with a body that never calls the same
 * method on {@code super}, as SuperClone and SuperFinalize do.
 *
 * <p>Shortcut: the call is found by the text {@code super.name(}, so a call that spells the
 * receiver differently, or sits in a comment-free string, is not told apart.
 */
public final class ObjectMethodCheck implements UnitCheck {

  private static final Set<String> NAME_SKIPPED =
      Set.of("typeTypeOrVoid", "typeType", "typeParameters", "formalParameters", "annotation");
  private static final String METHOD = "methodDeclaration";
  private static final String PARAMETER = "formalParameter";
  private static final String BLOCK = "block";

  private final String methodName;
  private final boolean requireSuperCall;
  private final String message;

  /**
   * Creates the check.
   *
   * @param methodName the name of the method with no parameters, such as {@code clone}
   * @param requireSuperCall whether only a method that does not call {@code super} is reported
   * @param message the message of each finding
   */
  public ObjectMethodCheck(
      final String methodName, final boolean requireSuperCall, final String message) {
    this.methodName = Objects.requireNonNull(methodName, "methodName");
    this.requireSuperCall = requireSuperCall;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD))) {
      final SyntaxNode method = visit.node();
      final Optional<SyntaxNode> name =
          SyntaxWalk.firstDescendant(method, "identifier", NAME_SKIPPED);
      if (name.isPresent() && methodName.equals(SyntaxWalk.text(name.get())) && flagged(method)) {
        matches.add(CheckSupport.at(name.get().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean flagged(final SyntaxNode method) {
    final boolean noParameters =
        SyntaxWalk.descendants(method).stream().noneMatch(node -> PARAMETER.equals(node.kind()));
    return noParameters && (!requireSuperCall || missingSuperCall(method));
  }

  private boolean missingSuperCall(final SyntaxNode method) {
    return SyntaxWalk.firstDescendant(method, BLOCK, Set.of()).isPresent()
        && !SyntaxWalk.text(method).contains("super." + methodName + "(");
  }
}
