package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the members and ancestors of a class from the member facts: a method that looks like an
 * override but takes parameter types of the same names from other packages, and a singleton that is
 * cloneable only through a superclass. It reports nothing when the member facts are unknown.
 */
public final class InheritedSignatureCheck implements RuleCheck {

  /** What the check looks for. */
  public enum Mode {
    /**
     * A method whose parameter types have the simple names but not the packages of an inherited
     * one.
     */
    WRONG_PACKAGE,
    /** Like {@link #WRONG_PACKAGE}, but the class also declares the real override. */
    WRONG_PACKAGE_INTENTIONAL,
    /** A singleton whose superclass, not the class itself, implements Cloneable. */
    INDIRECT_CLONEABLE
  }

  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CLONEABLE = "java.lang.Cloneable";
  private static final Pattern SINGLETON =
      Pattern.compile(
          "^class(\\w+)(?=[\\s\\S]*private\\1\\()"
              + "(?=[\\s\\S]*static(?:final|volatile)*\\1\\w+[=;])");
  private static final FactNeed MEMBERS =
      new FactNeed(Set.of(CLASS_DECLARATION), Set.of(FactKind.MEMBERS));
  private static final FactNeed WITH_TYPES =
      new FactNeed(Set.of(CLASS_DECLARATION, "typeType"), Set.of(FactKind.MEMBERS, FactKind.TYPE));

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at each finding
   */
  public InheritedSignatureCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return mode == Mode.INDIRECT_CLONEABLE ? MEMBERS : WITH_TYPES;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION))) {
      types
          .membersOf(visit.node().span())
          .ifPresent(
              members -> {
                if (mode == Mode.INDIRECT_CLONEABLE) {
                  indirectCloneable(visit.node(), members, matches);
                } else {
                  wrongPackage(visit.node(), members, types, matches);
                }
              });
    }
    return List.copyOf(matches);
  }

  private void wrongPackage(
      final SyntaxNode type,
      final TypeMembers members,
      final UnitTypeFacts types,
      final List<RuleMatch> out) {
    final boolean intentional = mode == Mode.WRONG_PACKAGE_INTENTIONAL;
    for (final DeclaredMethods.Method method : DeclaredMethods.of(type)) {
      final List<CallTarget> candidates =
          members.inheritedMethods().stream()
              .filter(m -> m.name().equals(method.name()))
              .filter(m -> m.parameters().size() == method.parameterNodes().size())
              .toList();
      final List<Optional<String>> declared =
          method.parameterNodes().stream()
              .map(node -> types.typeOf(node.span()).map(TypeRef::name))
              .toList();
      if (!candidates.isEmpty()
          && declared.stream().allMatch(Optional::isPresent)
          && candidates.stream().noneMatch(c -> sameTypes(c, declared))
          && candidates.stream().anyMatch(c -> sameSimpleNames(c, declared))
          && intentional == declaresOverride(type, method, candidates, types)) {
        out.add(CheckSupport.at(method.node().span(), message));
      }
    }
  }

  /** Tells whether the class has a second method of the name that does override a candidate. */
  private static boolean declaresOverride(
      final SyntaxNode type,
      final DeclaredMethods.Method method,
      final List<CallTarget> candidates,
      final UnitTypeFacts types) {
    return DeclaredMethods.of(type).stream()
        .filter(other -> !other.node().span().equals(method.node().span()))
        .filter(other -> other.name().equals(method.name()))
        .filter(other -> other.parameterNodes().size() == method.parameterNodes().size())
        .map(
            other ->
                other.parameterNodes().stream()
                    .map(node -> types.typeOf(node.span()).map(TypeRef::name))
                    .toList())
        .anyMatch(
            declared ->
                declared.stream().allMatch(Optional::isPresent)
                    && candidates.stream().anyMatch(c -> sameTypes(c, declared)));
  }

  private static boolean sameTypes(final CallTarget target, final List<Optional<String>> declared) {
    for (int i = 0; i < declared.size(); i++) {
      if (!target.parameters().get(i).name().equals(declared.get(i).orElse(""))) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameSimpleNames(
      final CallTarget target, final List<Optional<String>> declared) {
    for (int i = 0; i < declared.size(); i++) {
      if (!simple(target.parameters().get(i).name()).equals(simple(declared.get(i).orElse("")))) {
        return false;
      }
    }
    return true;
  }

  private static String simple(final String name) {
    return name.substring(name.lastIndexOf('.') + 1);
  }

  private void indirectCloneable(
      final SyntaxNode type, final TypeMembers members, final List<RuleMatch> out) {
    final String text = SyntaxWalk.text(type);
    final String header = text.substring(0, Math.max(0, text.indexOf('{')));
    final boolean inherits = members.ancestors().stream().anyMatch(a -> CLONEABLE.equals(a.name()));
    if (inherits && !header.contains("Cloneable") && SINGLETON.matcher(text).find()) {
      out.add(CheckSupport.at(type.span(), message));
    }
  }
}
