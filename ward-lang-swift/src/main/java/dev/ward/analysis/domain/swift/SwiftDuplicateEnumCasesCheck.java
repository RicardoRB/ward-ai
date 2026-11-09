package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports every case of an enum whose name is declared more than once in the same enum, like
 * SwiftLint's {@code duplicate_enum_cases}. Cases inside a conditional compilation block are not
 * direct members, so they are never compared.
 */
public final class SwiftDuplicateEnumCasesCheck implements RuleCheck {

  private static final String ENUM = "enum_declaration";
  private static final String UNION_ENUM = "union_style_enum";
  private static final String MEMBERS = "union_style_enum_members";
  private static final String MEMBER = "union_style_enum_member";
  private static final String CASE_CLAUSE = "union_style_enum_case_clause";
  private static final String CASE_LIST = "union_style_enum_case_list";
  private static final String CASE = "union_style_enum_case";
  private static final String CASE_NAME = "enum_case_name";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftDuplicateEnumCasesCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final List<SyntaxNode> enums = SwiftTrees.descendants(root, ENUM);
    for (final SyntaxNode enumeration : enums) {
      duplicates(enumeration).forEach(name -> matches.add(SwiftTrees.at(name, message)));
    }
    return List.copyOf(matches);
  }

  /** Returns the case name nodes of {@code enumeration} whose name is declared more than once. */
  private static List<SyntaxNode> duplicates(final SyntaxNode enumeration) {
    final Map<String, List<SyntaxNode>> byName = new LinkedHashMap<>();
    caseNames(enumeration).forEach(name -> addName(byName, name));
    final List<SyntaxNode> out = new ArrayList<>();
    byName.values().stream().filter(names -> names.size() > 1).forEach(out::addAll);
    return out;
  }

  private static void addName(final Map<String, List<SyntaxNode>> byName, final SyntaxNode name) {
    byName.computeIfAbsent(SwiftTrees.compact(name), key -> new ArrayList<>()).add(name);
  }

  private static List<SyntaxNode> caseNames(final SyntaxNode enumeration) {
    final List<SyntaxNode> names = new ArrayList<>();
    SwiftTrees.child(enumeration, UNION_ENUM)
        .flatMap(union -> SwiftTrees.child(union, MEMBERS))
        .ifPresent(members -> addMembers(members, names));
    return names;
  }

  /** The members of a list are nested: each member is followed by the rest of the list. */
  private static void addMembers(final SyntaxNode members, final List<SyntaxNode> names) {
    for (final SyntaxNode child : members.children()) {
      if (MEMBER.equals(child.kind())) {
        addMember(child, names);
      } else if (MEMBERS.equals(child.kind())) {
        addMembers(child, names);
      }
    }
  }

  private static void addMember(final SyntaxNode member, final List<SyntaxNode> names) {
    SwiftTrees.child(member, CASE_CLAUSE)
        .flatMap(clause -> SwiftTrees.child(clause, CASE_LIST))
        .ifPresent(list -> addCases(list, names));
  }

  private static void addCases(final SyntaxNode list, final List<SyntaxNode> names) {
    for (final SyntaxNode child : list.children()) {
      if (CASE.equals(child.kind())) {
        SwiftTrees.child(child, CASE_NAME).ifPresent(names::add);
      } else if (CASE_LIST.equals(child.kind())) {
        addCases(child, names);
      }
    }
  }
}
