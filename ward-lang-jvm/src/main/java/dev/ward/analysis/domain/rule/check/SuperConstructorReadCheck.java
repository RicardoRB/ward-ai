package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a method of a subclass that reads a field of the subclass before writing it, when the
 * constructor of its superclass, declared in the same file, calls a method of that name: the call
 * runs before the subclass has initialized anything, so the field still has its default value.
 */
public final class SuperConstructorReadCheck implements UnitCheck {
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CLASS_BODY = "classBody";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String IDENTIFIER = "identifier";
  private static final String EXTENDS = "'extends'";
  private static final String TYPE_TYPE = "typeType";
  private static final int WITH_RECEIVER = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each read; {@code {value}} is the field name
   */
  public SuperConstructorReadCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final Map<String, SyntaxNode> byName = new HashMap<>();
    final List<SyntaxNode> classes = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION))) {
      classes.add(visit.node());
      name(visit.node()).ifPresent(name -> byName.putIfAbsent(name, visit.node()));
    }
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode type : classes) {
      final SyntaxNode parent = extended(type).map(byName::get).orElse(null);
      if (parent != null && !parent.equals(type)) {
        final Set<String> called = constructorCalls(parent);
        if (!called.isEmpty()) {
          reads(type, called, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private void reads(final SyntaxNode type, final Set<String> called, final List<RuleMatch> out) {
    final FieldAccesses accesses = FieldAccesses.of(type);
    final Set<String> setUp = new HashSet<>();
    accesses.members().stream()
        .filter(member -> member.kind() == FieldAccesses.MemberKind.INITIALIZER)
        .flatMap(member -> member.accesses().stream())
        .filter(access -> access.use() != FieldAccesses.Use.READ)
        .forEach(access -> setUp.add(access.field()));
    final Set<String> candidates = new HashSet<>();
    accesses.fields().stream()
        .filter(field -> !field.initialized() && !field.isStatic() && !field.annotated())
        .filter(field -> !setUp.contains(field.name()))
        .forEach(field -> candidates.add(field.name()));
    for (final FieldAccesses.Member member : accesses.members()) {
      if (called.contains(member.name())) {
        firstReads(member, candidates, out);
      }
    }
  }

  private void firstReads(
      final FieldAccesses.Member method, final Set<String> candidates, final List<RuleMatch> out) {
    final Set<String> seen = new HashSet<>();
    for (final FieldAccesses.Access access : method.accesses()) {
      if (candidates.contains(access.field())
          && seen.add(access.field())
          && access.use() == FieldAccesses.Use.READ) {
        out.add(
            CheckSupport.at(access.node().span(), CheckSupport.fill(message, access.field(), "")));
      }
    }
  }

  /** Returns the names of the methods that the constructors of {@code type} call on this. */
  private static Set<String> constructorCalls(final SyntaxNode type) {
    final Set<String> names = new HashSet<>();
    constructors(type).stream()
        .flatMap(constructor -> SyntaxWalk.descendants(constructor).stream())
        .filter(node -> EXPRESSION.equals(node.kind()))
        .forEach(node -> callOnThis(node).ifPresent(names::add));
    return names;
  }

  private static List<SyntaxNode> constructors(final SyntaxNode type) {
    return type.children().stream()
        .filter(child -> CLASS_BODY.equals(child.kind()))
        .flatMap(body -> JUnitMembers.members(body).stream())
        .flatMap(member -> SyntaxWalk.descendants(member).stream())
        .filter(node -> CONSTRUCTOR.equals(node.kind()))
        .toList();
  }

  /** Returns the method called by {@code m(...)} or {@code this.m(...)}, if {@code node} is one. */
  private static Optional<String> callOnThis(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean bare = parts.size() == 1 && METHOD_CALL.equals(parts.get(0).kind());
    final boolean onThis =
        parts.size() == WITH_RECEIVER
            && "this".equals(SyntaxWalk.text(parts.get(0)))
            && METHOD_CALL.equals(parts.get(2).kind());
    if (!bare && !onThis) {
      return Optional.empty();
    }
    return name(parts.get(parts.size() - 1));
  }

  private static Optional<String> name(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text);
  }

  private static Optional<String> extended(final SyntaxNode type) {
    final List<SyntaxNode> parts = type.children();
    for (int i = 0; i + 1 < parts.size(); i++) {
      if (EXTENDS.equals(parts.get(i).kind()) && TYPE_TYPE.equals(parts.get(i + 1).kind())) {
        return Optional.of(simpleName(SyntaxWalk.text(parts.get(i + 1))));
      }
    }
    return Optional.empty();
  }

  /** Returns the simple name of a type written in source, without its type arguments. */
  private static String simpleName(final String written) {
    final int arguments = written.indexOf('<');
    final String erased = arguments < 0 ? written : written.substring(0, arguments);
    return erased.substring(erased.lastIndexOf('.') + 1);
  }
}
