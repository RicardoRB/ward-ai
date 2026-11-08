package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports code that hands out or takes in mutable state of a class, as SpotBugs' exposure patterns
 * do. In {@code return} mode a method returns a field of a mutable type, or a buffer that shares
 * the array of a field; in {@code store} mode a method stores a mutable parameter, or a buffer
 * around an array parameter, in a field. The fields looked at are the static ones or the instance
 * ones. A name that a local or parameter shadows is not told apart, since the syntax tree does not
 * bind names.
 */
public final class ExposedStateCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String RETURN = "'return'";
  private static final String EXPRESSION = "expression";
  private static final String PARAMETER = "formalParameter";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final int RETURN_PARTS = 3;
  private static final Pattern WRAP =
      Pattern.compile("^(?:[\\w.]*\\.)?\\w*Buffer\\.wrap\\((?:this\\.)?(\\w+)(?:,[^()]*)?\\)$");
  private static final Pattern DUPLICATE =
      Pattern.compile("^(?:this\\.)?(\\w+)\\.(?:duplicate|slice)\\(\\)$");
  private static final Pattern BUFFER_TYPE = Pattern.compile("^(?:[\\w.]*\\.)?\\w*Buffer$");

  private final Criteria criteria;
  private final String message;

  /**
   * What a reported method does.
   *
   * @param store whether a parameter is stored in a field, instead of a field being returned
   * @param staticFields whether the fields are the static ones
   * @param buffers whether the exposed object is a buffer sharing an array, not the array itself
   * @param types a pattern the compact type text of the field or parameter matches
   * @param publicStaticMethod whether the returning method must be public and static
   */
  public record Criteria(
      boolean store,
      boolean staticFields,
      boolean buffers,
      Pattern types,
      boolean publicStaticMethod) {

    /** Checks the pattern. */
    public Criteria {
      Objects.requireNonNull(types, "types");
    }
  }

  /**
   * Creates the check.
   *
   * @param criteria what a reported method does
   * @param message the message; {@code {value}} is the field
   */
  public ExposedStateCheck(final Criteria criteria, final String message) {
    this.criteria = Objects.requireNonNull(criteria, "criteria");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final Map<String, String> fields = ClassFieldTypes.of(visit.node(), criteria.staticFields());
      for (final SyntaxNode member : ClassMembers.callableMembers(visit.node())) {
        if (criteria.store()) {
          stores(member, fields, matches);
        } else if (returnerMeets(member)) {
          returns(ClassMembers.declarationOf(member), fields, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private boolean returnerMeets(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    final boolean isStatic = modifiers.contains(MemberDeclarations.STATIC);
    final boolean hidden = modifiers.contains(MemberDeclarations.PRIVATE);
    if (criteria.publicStaticMethod()) {
      return isStatic && modifiers.contains(MemberDeclarations.PUBLIC);
    }
    return !hidden && isStatic == criteria.staticFields();
  }

  private void returns(
      final SyntaxNode method, final Map<String, String> fields, final List<RuleMatch> matches) {
    for (final SyntaxNode node : SyntaxWalk.descendants(method)) {
      final List<SyntaxNode> parts = node.children();
      if (STATEMENT.equals(node.kind())
          && parts.size() == RETURN_PARTS
          && RETURN.equals(parts.get(0).kind())
          && EXPRESSION.equals(parts.get(1).kind())) {
        final String exposed = exposedField(SyntaxWalk.text(parts.get(1)), fields);
        if (!exposed.isEmpty()) {
          matches.add(CheckSupport.at(node.span(), CheckSupport.fill(message, exposed, "")));
        }
      }
    }
  }

  private String exposedField(final String value, final Map<String, String> fields) {
    final String bare = value.startsWith("this.") ? value.substring("this.".length()) : value;
    if (!criteria.buffers()) {
      return fields.containsKey(bare) && criteria.types().matcher(fields.get(bare)).find()
          ? bare
          : "";
    }
    final Matcher wrapped = WRAP.matcher(value);
    if (wrapped.matches() && matchesType(fields, wrapped.group(1))) {
      return wrapped.group(1);
    }
    final Matcher shared = DUPLICATE.matcher(value);
    final boolean bufferField =
        shared.matches()
            && fields.containsKey(shared.group(1))
            && BUFFER_TYPE.matcher(fields.get(shared.group(1))).matches();
    return bufferField ? shared.group(1) : "";
  }

  private boolean matchesType(final Map<String, String> fields, final String name) {
    return fields.containsKey(name) && criteria.types().matcher(fields.get(name)).find();
  }

  private void stores(
      final SyntaxNode member, final Map<String, String> fields, final List<RuleMatch> matches) {
    final SyntaxNode callable = ClassMembers.declarationOf(member);
    final Map<String, String> parameters = parameters(callable);
    for (final SyntaxNode assignment : Assignments.in(callable)) {
      final String target = Assignments.target(assignment);
      final String field = target.startsWith("this.") ? target.substring("this.".length()) : target;
      if (fields.containsKey(field) && storesParameter(Assignments.value(assignment), parameters)) {
        matches.add(CheckSupport.at(assignment.span(), CheckSupport.fill(message, field, "")));
      }
    }
  }

  private boolean storesParameter(final String value, final Map<String, String> parameters) {
    if (!criteria.buffers()) {
      return parameters.containsKey(value)
          && criteria.types().matcher(parameters.get(value)).find();
    }
    final Matcher wrapped = WRAP.matcher(value);
    return wrapped.matches() && matchesType(parameters, wrapped.group(1));
  }

  private static Map<String, String> parameters(final SyntaxNode callable) {
    final Map<String, String> parameters = new HashMap<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(callable)) {
      if (PARAMETER.equals(node.kind())) {
        final String type = ClassMembers.typeText(node).orElse("");
        node.children().stream()
            .filter(child -> DECLARATOR_ID.equals(child.kind()))
            .map(SyntaxWalk::text)
            .forEach(name -> parameters.put(name, type));
      }
    }
    return parameters;
  }
}
