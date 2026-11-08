package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an instance field of a servlet or a Struts action. The container shares one instance of
 * such a class between threads, so state kept in it is a race. A class counts as a servlet or an
 * action only when the file imports the framework or names the superclass in full. In {@code
 * mutable} mode a field of a mutable type is reported, in {@code instance} mode any other non-final
 * field is.
 */
public final class ServletFieldCheck implements RuleCheck {

  private static final Set<String> SERVLETS = Set.of("HttpServlet", "GenericServlet");
  private static final Set<String> ACTIONS =
      Set.of("Action", "DispatchAction", "LookupDispatchAction", "MappingDispatchAction");
  private static final Set<String> MUTABLE =
      Set.of(
          "List",
          "ArrayList",
          "LinkedList",
          "Map",
          "HashMap",
          "TreeMap",
          "LinkedHashMap",
          "Set",
          "HashSet",
          "TreeSet",
          "LinkedHashSet",
          "Collection",
          "Queue",
          "Deque",
          "ArrayDeque",
          "Vector",
          "Hashtable",
          "Date",
          "Calendar",
          "StringBuilder",
          "StringBuffer");
  private static final String IMPORT = "importDeclaration";

  private final boolean struts;
  private final boolean mutable;
  private final String message;

  /**
   * Creates the check.
   *
   * @param struts whether the class is a Struts action, not a servlet
   * @param mutable whether fields of a mutable type are reported, not the other fields
   * @param message the message; {@code {value}} is the field name
   */
  public ServletFieldCheck(final boolean struts, final boolean mutable, final String message) {
    this.struts = struts;
    this.mutable = mutable;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final boolean imported = imports(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode clazz = visit.node();
      if (isFramework(clazz, imported)) {
        for (final SyntaxNode field : ClassMembers.fieldMembers(clazz)) {
          if (reported(field)) {
            final String name =
                String.join(",", ClassMembers.fieldNames(ClassMembers.declarationOf(field)));
            matches.add(CheckSupport.at(field.span(), CheckSupport.fill(message, name, "")));
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private boolean imports(final SyntaxNode root) {
    final String prefix = struts ? "importorg.apache.struts" : "import(?:javax|jakarta).servlet";
    return SyntaxWalk.visits(root, Set.of(IMPORT)).stream()
        .anyMatch(visit -> SyntaxWalk.text(visit.node()).matches("^" + prefix + ".*"));
  }

  private boolean isFramework(final SyntaxNode clazz, final boolean imported) {
    final String superclass = ClassHeaders.extendsType(clazz).orElse("");
    final String simple = ClassHeaders.simple(superclass);
    final boolean named = struts ? ACTIONS.contains(simple) : SERVLETS.contains(simple);
    final boolean qualified =
        struts ? superclass.startsWith("org.apache.struts.") : superclass.contains("servlet.");
    return named && (imported || qualified);
  }

  private boolean reported(final SyntaxNode field) {
    final List<String> modifiers = MemberDeclarations.modifiers(field);
    if (modifiers.contains(MemberDeclarations.STATIC)) {
      return false;
    }
    final boolean isMutable =
        MUTABLE.contains(
            ClassHeaders.simple(
                ClassMembers.typeText(ClassMembers.declarationOf(field)).orElse("")));
    return mutable ? isMutable : !isMutable && !modifiers.contains(MemberDeclarations.FINAL);
  }
}
