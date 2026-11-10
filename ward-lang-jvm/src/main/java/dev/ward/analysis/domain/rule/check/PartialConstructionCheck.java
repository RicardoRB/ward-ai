package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports double-checked locking on a volatile field that publishes a partly built object: inside
 * the inner null test, the field is assigned and then used again, by a field store or an array
 * store through it, before the lock is released. Primitive and {@code String} fields are skipped.
 */
public final class PartialConstructionCheck implements RuleCheck {

  private static final String STATEMENT = "statement";
  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String IF = "'if'";
  private static final String SYNCHRONIZED = "'synchronized'";
  private static final String VOLATILE = "volatile";
  private static final int CONDITION_AT = 2;
  private static final int THEN_AT = 4;
  private static final Set<String> NOT_REFERENCES =
      Set.of("int", "long", "short", "byte", "char", "boolean", "float", "double", "String");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field name
   */
  public PartialConstructionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      for (final String field : volatileReferences(visit.node())) {
        inClass(visit.node(), field, matches);
      }
    }
    return List.copyOf(matches);
  }

  private static List<String> volatileReferences(final SyntaxNode clazz) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode member : ClassMembers.fieldMembers(clazz)) {
      final SyntaxNode field = ClassMembers.declarationOf(member);
      final boolean reference =
          ClassMembers.typeText(field).filter(type -> !NOT_REFERENCES.contains(type)).isPresent();
      if (reference && MemberDeclarations.modifiers(member).contains(VOLATILE)) {
        names.addAll(ClassMembers.fieldNames(field));
      }
    }
    return names;
  }

  private void inClass(final SyntaxNode clazz, final String field, final List<RuleMatch> out) {
    final Pattern test = Pattern.compile("^(?:this\\.)?" + field + "==null$");
    for (final SyntaxNode outer : SyntaxWalk.descendants(clazz)) {
      if (testsNull(outer, test)) {
        SyntaxWalk.descendants(outer).stream()
            .filter(node -> STATEMENT.equals(node.kind()) && isSynchronized(node))
            .flatMap(sync -> SyntaxWalk.descendants(sync).stream())
            .filter(inner -> testsNull(inner, test))
            .forEach(inner -> published(inner, field, out));
      }
    }
  }

  private static boolean testsNull(final SyntaxNode node, final Pattern test) {
    final List<SyntaxNode> parts = node.children();
    return STATEMENT.equals(node.kind())
        && parts.size() > CONDITION_AT
        && IF.equals(parts.get(0).kind())
        && test.matcher(SyntaxWalk.text(parts.get(CONDITION_AT))).matches();
  }

  private static boolean isSynchronized(final SyntaxNode statement) {
    return statement.children().stream().anyMatch(part -> SYNCHRONIZED.equals(part.kind()));
  }

  private void published(final SyntaxNode inner, final String field, final List<RuleMatch> out) {
    if (inner.children().size() <= THEN_AT) {
      return;
    }
    final List<SyntaxNode> statements =
        SyntaxWalk.descendants(inner.children().get(THEN_AT)).stream()
            .filter(node -> BLOCK.equals(node.kind()))
            .findFirst()
            .map(
                block ->
                    block.children().stream()
                        .filter(c -> BLOCK_STATEMENT.equals(c.kind()))
                        .toList())
            .orElse(List.of());
    final Pattern assign = Pattern.compile("^(?:this\\.)?" + field + "=[^=]");
    final Pattern store = Pattern.compile("^(?:this\\.)?" + field + "(?:\\.\\w+|\\[.*\\])=[^=]");
    boolean assigned = false;
    for (final SyntaxNode statement : statements) {
      final String text = SyntaxWalk.text(statement);
      if (assigned && store.matcher(text).find()) {
        out.add(CheckSupport.at(statement.span(), CheckSupport.fill(message, field, "")));
      }
      assigned = assigned || assign.matcher(text).find();
    }
  }
}
