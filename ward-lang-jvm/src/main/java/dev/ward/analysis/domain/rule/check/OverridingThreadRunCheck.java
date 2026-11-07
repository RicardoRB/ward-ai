package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each {@code run} method with no parameters that a class extending {@code Thread}
 * declares, as PMD's OverridingThreadRun does.
 *
 * <p>Shortcut: an anonymous subclass of Thread is not checked, and a superclass is recognized only
 * by the name {@code Thread} or {@code java.lang.Thread} as written.
 */
public final class OverridingThreadRunCheck implements UnitCheck {

  private static final String CLASS = "classDeclaration";
  private static final String METHOD = "methodDeclaration";
  private static final String PARAMETER = "formalParameter";
  private static final String RUN = "run";
  private static final String EXTENDS = "'extends'";
  private static final Set<String> THREAD = Set.of("Thread", "java.lang.Thread");
  private static final Set<String> NAME_SKIPPED =
      Set.of("typeTypeOrVoid", "typeType", "typeParameters", "formalParameters", "annotation");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public OverridingThreadRunCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD))) {
      final SyntaxNode method = visit.node();
      if (isRunWithoutParameters(method) && extendsThread(visit.ancestors())) {
        matches.add(CheckSupport.at(method.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isRunWithoutParameters(final SyntaxNode method) {
    final Optional<SyntaxNode> name =
        SyntaxWalk.firstDescendant(method, "identifier", NAME_SKIPPED);
    final boolean noParameters =
        SyntaxWalk.descendants(method).stream().noneMatch(node -> PARAMETER.equals(node.kind()));
    return noParameters && name.isPresent() && RUN.equals(SyntaxWalk.text(name.get()));
  }

  /** Tells whether the nearest class above the method extends Thread; ancestors are root first. */
  private static boolean extendsThread(final List<SyntaxNode> ancestors) {
    final Optional<SyntaxNode> owner =
        ancestors.stream().filter(node -> CLASS.equals(node.kind())).reduce((a, b) -> b);
    return owner.isPresent() && namesThread(owner.get().children());
  }

  private static boolean namesThread(final List<SyntaxNode> children) {
    for (int index = 0; index + 1 < children.size(); index++) {
      if (EXTENDS.equals(children.get(index).kind())) {
        return THREAD.contains(SyntaxWalk.text(children.get(index + 1)));
      }
    }
    return false;
  }
}
