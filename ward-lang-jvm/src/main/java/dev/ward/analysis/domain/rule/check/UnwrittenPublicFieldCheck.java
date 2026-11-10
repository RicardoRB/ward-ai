package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports a member selected from a public or protected field to which no file of the project ever
 * writes a non-null value. A name written anywhere counts as written for every field of that name,
 * and an annotated field is left alone, since a framework may set it. Inside the declaring class
 * the field is found through its scope; elsewhere only the qualified form {@code
 * object.field.member} is read, so an inherited field used by simple name in a subclass is never
 * reported.
 */
public final class UnwrittenPublicFieldCheck implements RuleCheck, ProjectCheck {

  private static final Set<String> PRIMITIVES =
      Set.of("int", "long", "short", "byte", "char", "boolean", "float", "double");
  private static final Set<String> COMPOUND_ENDS = Set.of("++", "--");
  private static final String DOT = ".";
  private static final String NULL = "null";
  private static final String THIS = "this";
  private static final int AFTER = 1;
  private static final int AFTER_NEXT = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field name
   */
  public UnwrittenPublicFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    final Map<String, List<SyntaxNode>> tokens = new LinkedHashMap<>();
    trees.forEach((path, tree) -> tokens.put(path, NullTokens.tokens(List.of(tree))));
    final Set<String> written = new HashSet<>();
    tokens.values().forEach(list -> written.addAll(writtenNames(list)));
    final Set<String> candidates = new HashSet<>();
    final Map<String, ProjectMatch> found = new LinkedHashMap<>();
    trees.forEach((path, tree) -> declared(path, tree, written, candidates, found));
    tokens.forEach((path, list) -> qualified(path, list, candidates, found));
    return List.copyOf(found.values());
  }

  /** Reads the candidate fields of the file and the uses of them inside their own class. */
  private void declared(
      final String path,
      final SyntaxNode tree,
      final Set<String> written,
      final Set<String> candidates,
      final Map<String, ProjectMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(tree, Set.of("classDeclaration"))) {
      final FieldAccesses accesses = FieldAccesses.of(visit.node());
      for (final String name : candidateNames(visit.node())) {
        if (!written.contains(name)) {
          candidates.add(name);
          accesses.accessesTo(name).stream()
              .filter(access -> access.dereferenced() && access.use() == FieldAccesses.Use.READ)
              .forEach(access -> add(found, path, access.node(), name));
        }
      }
    }
  }

  private static List<String> candidateNames(final SyntaxNode clazz) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode member : ClassMembers.fieldMembers(clazz)) {
      final List<String> modifiers = MemberDeclarations.modifiers(member);
      final boolean visible =
          modifiers.contains(MemberDeclarations.PUBLIC)
              || modifiers.contains(MemberDeclarations.PROTECTED);
      final boolean reference =
          ClassMembers.typeText(ClassMembers.declarationOf(member))
              .filter(type -> !PRIMITIVES.contains(type))
              .isPresent();
      if (visible && reference && !annotated(member)) {
        names.addAll(ClassMembers.fieldNames(ClassMembers.declarationOf(member)));
      }
    }
    return names;
  }

  private static boolean annotated(final SyntaxNode member) {
    return member.children().stream().anyMatch(child -> SyntaxWalk.text(child).startsWith("@"));
  }

  private void qualified(
      final String path,
      final List<SyntaxNode> tokens,
      final Set<String> candidates,
      final Map<String, ProjectMatch> found) {
    for (int index = 2; index + AFTER_NEXT < tokens.size(); index++) {
      final String name = text(tokens, index);
      final boolean selected =
          candidates.contains(name)
              && DOT.equals(text(tokens, index - AFTER))
              && !THIS.equals(text(tokens, index - AFTER_NEXT))
              && DOT.equals(text(tokens, index + AFTER));
      if (selected) {
        add(found, path, tokens.get(index), name);
      }
    }
  }

  private void add(
      final Map<String, ProjectMatch> found,
      final String path,
      final SyntaxNode node,
      final String name) {
    final String key = path + ":" + node.span().startLine() + ":" + node.span().startColumn();
    found.putIfAbsent(
        key,
        new ProjectMatch(path, new RuleMatch(node.span(), CheckSupport.fill(message, name, ""))));
  }

  /** The names that some statement of the tokens assigns a non-null value to or updates. */
  private static Set<String> writtenNames(final List<SyntaxNode> tokens) {
    final Set<String> names = new HashSet<>();
    for (int index = 0; index < tokens.size(); index++) {
      final String next = text(tokens, index + AFTER);
      final boolean update =
          COMPOUND_ENDS.contains(next) || COMPOUND_ENDS.contains(text(tokens, index - AFTER));
      final boolean plain = "=".equals(next) && !NULL.equals(text(tokens, index + AFTER_NEXT));
      final boolean compound = next.length() > 1 && next.endsWith("=") && isOperator(next);
      if (update || plain || compound) {
        names.add(text(tokens, index));
      }
    }
    return names;
  }

  private static boolean isOperator(final String token) {
    return !"==".equals(token) && !"!=".equals(token) && !"<=".equals(token) && !">=".equals(token);
  }

  private static String text(final List<SyntaxNode> tokens, final int index) {
    return index >= 0 && index < tokens.size() ? SyntaxWalk.text(tokens.get(index)) : "";
  }
}
