package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a constructor that leaves a field annotated never to be null unset. The field is a
 * non-final instance field of reference type with no initializer, and no initializer block or
 * assignment in the constructor sets it. A constructor that calls {@code this(...)} is left to the
 * one it calls, and a class with no constructor is reported at its declaration.
 */
public final class NonNullFieldInitCheck implements RuleCheck {

  private static final String CLASS_BODY_DECLARATION = "classBodyDeclaration";
  private static final String MEMBER = "memberDeclaration";
  private static final String BLOCK = "block";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the constructor
   */
  public NonNullFieldInitCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      found.addAll(inClass(visit.node()));
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> inClass(final SyntaxNode type) {
    final List<SyntaxNode> entries = entries(type);
    final Set<String> unset = unset(entries);
    final List<RuleMatch> found = new ArrayList<>();
    if (unset.isEmpty()) {
      return found;
    }
    final List<SyntaxNode> constructors = constructors(entries);
    if (constructors.isEmpty()) {
      found.add(CheckSupport.at(type.span(), message));
    }
    for (final SyntaxNode constructor : constructors) {
      if (!delegates(constructor) && !assignsAll(constructor, unset)) {
        found.add(CheckSupport.at(constructor.span(), message));
      }
    }
    return found;
  }

  private static List<SyntaxNode> entries(final SyntaxNode type) {
    return type.children().stream()
        .filter(child -> "classBody".equals(child.kind()))
        .flatMap(body -> body.children().stream())
        .filter(child -> CLASS_BODY_DECLARATION.equals(child.kind()))
        .toList();
  }

  /** The names of the never-null instance fields that no initializer or block sets. */
  private static Set<String> unset(final List<SyntaxNode> entries) {
    final Set<String> names = new HashSet<>();
    final Set<String> set = new HashSet<>();
    for (final SyntaxNode entry : entries) {
      final Set<String> modifiers = modifiers(entry);
      for (final SyntaxNode field : fields(entry)) {
        collect(entry, modifiers, field, names, set);
      }
    }
    final List<SyntaxNode> blocks = instanceBlocks(entries);
    return names.stream()
        .filter(name -> !set.contains(name))
        .filter(name -> blocks.stream().noneMatch(b -> NullFlow.assigns(b, name, Set.of())))
        .collect(Collectors.toSet());
  }

  private static void collect(
      final SyntaxNode entry,
      final Set<String> modifiers,
      final SyntaxNode field,
      final Set<String> names,
      final Set<String> set) {
    final boolean nonNull =
        NullSyntax.annotations(entry).stream()
            .map(NullSyntax::simpleName)
            .anyMatch(NullScope.NON_NULL::contains);
    final boolean primitive =
        NullSyntax.first(field, "typeType").map(NullSyntax::isPrimitive).orElse(false);
    if (!nonNull || primitive || modifiers.contains("static") || modifiers.contains("final")) {
      return;
    }
    SyntaxWalk.descendants(field).stream()
        .filter(node -> "variableDeclarator".equals(node.kind()))
        .forEach(
            declarator -> {
              final String name =
                  NullSyntax.first(declarator, "variableDeclaratorId")
                      .map(SyntaxWalk::text)
                      .orElse("");
              final boolean initialized =
                  declarator.children().stream()
                      .anyMatch(part -> "=".equals(SyntaxWalk.text(part)));
              (initialized ? set : names).add(name);
            });
  }

  private static List<SyntaxNode> fields(final SyntaxNode entry) {
    return entry.children().stream()
        .filter(child -> MEMBER.equals(child.kind()))
        .flatMap(member -> member.children().stream())
        .filter(child -> "fieldDeclaration".equals(child.kind()))
        .toList();
  }

  private static boolean isInstanceBlock(final SyntaxNode entry) {
    return entry.children().stream().anyMatch(child -> BLOCK.equals(child.kind()))
        && !entry.children().stream().anyMatch(child -> "static".equals(SyntaxWalk.text(child)));
  }

  private static List<SyntaxNode> instanceBlocks(final List<SyntaxNode> entries) {
    return entries.stream()
        .filter(entry -> isInstanceBlock(entry))
        .flatMap(entry -> entry.children().stream())
        .filter(child -> BLOCK.equals(child.kind()))
        .toList();
  }

  private static Set<String> modifiers(final SyntaxNode entry) {
    return entry.children().stream()
        .filter(child -> NullSyntax.HOLDERS.contains(child.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.toSet());
  }

  private static List<SyntaxNode> constructors(final List<SyntaxNode> entries) {
    return entries.stream()
        .flatMap(entry -> entry.children().stream())
        .filter(child -> MEMBER.equals(child.kind()))
        .flatMap(member -> member.children().stream())
        .filter(child -> "constructorDeclaration".equals(child.kind()))
        .toList();
  }

  private static boolean delegates(final SyntaxNode constructor) {
    return NullSyntax.first(constructor, BLOCK)
        .map(block -> SyntaxWalk.text(block).startsWith("{this("))
        .orElse(false);
  }

  private static boolean assignsAll(final SyntaxNode constructor, final Set<String> names) {
    final Set<String> shadowed =
        SyntaxWalk.descendants(constructor).stream()
            .filter(node -> "formalParameter".equals(node.kind()))
            .flatMap(param -> NullSyntax.first(param, "variableDeclaratorId").stream())
            .map(SyntaxWalk::text)
            .collect(Collectors.toSet());
    final SyntaxNode body = NullSyntax.first(constructor, BLOCK).orElse(null);
    return names.stream().allMatch(name -> NullFlow.assigns(body, name, shadowed));
  }
}
