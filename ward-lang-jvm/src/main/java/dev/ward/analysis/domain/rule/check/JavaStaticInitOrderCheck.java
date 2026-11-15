package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports a static field that is initialized with an instance of its own class while a static final
 * field declared after it is still unassigned, as SpotBugs' SI_INSTANCE_BEFORE_FINALS_ASSIGNED
 * does. A final field with a literal value is a constant and is assigned before any code runs. The
 * field must matter: a constructor or instance initializer of the class reads it, since an instance
 * that never reads the late field is not harmed by being built first.
 */
public final class JavaStaticInitOrderCheck implements RuleCheck {
  private static final Pattern CONSTANT =
      Pattern.compile("^-?(?:\\d[\\w.]*|\"[^\"]*\"|'[^']*'|true|false)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the final field assigned late
   */
  public JavaStaticInitOrderCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      matches.addAll(inClass(visit.node()));
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> inClass(final SyntaxNode clazz) {
    final String instance = "new" + ClassMembers.nameOf(clazz) + "(";
    final List<SyntaxNode> fields = ClassMembers.fieldMembers(clazz);
    final Set<String> setUp = namesInSetUp(clazz);
    final List<RuleMatch> matches = new ArrayList<>();
    for (int index = 0; index < fields.size(); index++) {
      final SyntaxNode field = fields.get(index);
      if (isStatic(field) && initializer(field).startsWith(instance)) {
        lateFinal(fields.subList(index + 1, fields.size()), setUp)
            .ifPresent(
                late ->
                    matches.add(CheckSupport.at(field.span(), CheckSupport.fill(message, late, "")))
            );
      }
    }
    return matches;
  }

  private static java.util.Optional<String> lateFinal(
      final List<SyntaxNode> later,
      final Set<String> setUp
  ) {
    return later.stream()
        .filter(JavaStaticInitOrderCheck::isStatic)
        .filter(field -> MemberDeclarations.modifiers(field).contains(MemberDeclarations.FINAL))
        .filter(
            field ->
                !initializer(field).isEmpty() && !CONSTANT.matcher(initializer(field)).matches()
        )
        .flatMap(field -> ClassMembers.fieldNames(ClassMembers.declarationOf(field)).stream())
        .filter(setUp::contains)
        .findFirst();
  }

  private static Set<String> namesInSetUp(final SyntaxNode clazz) {
    final List<SyntaxNode> members = new ArrayList<>(ClassShapes.constructors(clazz));
    members.addAll(ClassShapes.initializers(clazz));
    return members.stream()
        .flatMap(member -> SyntaxWalk.leaves(member).stream())
        .map(SyntaxWalk::text)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean isStatic(final SyntaxNode field) {
    return MemberDeclarations.modifiers(field).contains(MemberDeclarations.STATIC);
  }

  private static String initializer(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(node -> "variableInitializer".equals(node.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
