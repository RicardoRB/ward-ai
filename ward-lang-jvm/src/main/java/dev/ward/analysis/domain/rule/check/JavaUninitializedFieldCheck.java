package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports a private reference field that no constructor or initializer sets, that other methods
 * assign and dereference, and that the class never tests against null, as SpotBugs'
 * UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR does.
 */
public final class JavaUninitializedFieldCheck implements RuleCheck {
  private static final Set<String> BEFORE =
      Set.of("BeforeEach", "BeforeAll", "Before", "BeforeClass", "PostConstruct");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field
   */
  public JavaUninitializedFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode clazz = visit.node();
      for (final SyntaxNode field : ClassMembers.fieldMembers(clazz)) {
        if (isCandidate(field)) {
          ClassMembers.fieldNames(ClassMembers.declarationOf(field)).stream()
              .filter(name -> uninitialized(clazz, name))
              .forEach(
                  name ->
                      matches.add(
                          CheckSupport.at(field.span(), CheckSupport.fill(message, name, ""))
                      )
              );
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isCandidate(final SyntaxNode field) {
    final SyntaxNode declaration = ClassMembers.declarationOf(field);
    final List<String> modifiers = MemberDeclarations.modifiers(field);
    return modifiers.contains(MemberDeclarations.PRIVATE)
        && !modifiers.contains(MemberDeclarations.STATIC)
        && !modifiers.contains(MemberDeclarations.FINAL)
        && JavaNodes.kids(field, "annotation").isEmpty()
        && field.children().stream()
            .noneMatch(
                child -> "modifier".equals(child.kind()) && SyntaxWalk.text(child).startsWith("@")
            )
        && !SyntaxWalk.text(declaration).contains("=")
        && SyntaxWalk.descendants(declaration).stream()
            .noneMatch(node -> "primitiveType".equals(node.kind()));
  }

  private static boolean uninitialized(final SyntaxNode clazz, final String name) {
    final String text = SyntaxWalk.text(clazz);
    final Pattern nullTest =
        Pattern.compile(
            "(?:\\b|\\.)" + name + "[!=]=null|null[!=]=" + name + "\\b|requireNonNull\\(" + name
        );
    final List<SyntaxNode> methods = ClassShapes.methods(clazz);
    final List<SyntaxNode> setUp = setUpMembers(clazz, methods);
    return setUp.stream().noneMatch(member -> assigns(member, name))
        && methods.stream().anyMatch(member -> assigns(member, name))
        && methods.stream().anyMatch(member -> dereferences(member, name))
        && !nullTest.matcher(text).find();
  }

  /**
   * Returns what runs before the methods of a class are used: its constructors, its initializers,
   * its {@code @BeforeEach}-style methods, and the methods those call by name, as far as it goes.
   */
  private static List<SyntaxNode> setUpMembers(
      final SyntaxNode clazz,
      final List<SyntaxNode> methods
  ) {
    final List<SyntaxNode> setUp = new ArrayList<>(ClassShapes.constructors(clazz));
    setUp.addAll(ClassShapes.initializers(clazz));
    methods.stream()
        .filter(method -> !Collections.disjoint(JUnitMembers.annotations(method), BEFORE))
        .forEach(setUp::add);
    boolean grew = true;
    while (grew) {
      final String text = setUp.stream().map(SyntaxWalk::text).collect(Collectors.joining());
      final List<SyntaxNode> called =
          methods.stream()
              .filter(method -> !setUp.contains(method) && calls(text, nameOf(method)))
              .toList();
      setUp.addAll(called);
      grew = !called.isEmpty();
    }
    return setUp;
  }

  private static String nameOf(final SyntaxNode method) {
    return JUnitMembers.name(ClassMembers.declarationOf(method));
  }

  private static boolean calls(final String text, final String method) {
    return Pattern.compile("(?:(?<![\\w.])|(?<=this\\.))" + Pattern.quote(method) + "\\(")
        .matcher(text)
        .find();
  }

  private static boolean dereferences(final SyntaxNode member, final String name) {
    return SyntaxWalk.descendants(member).stream()
        .filter(node -> "expression".equals(node.kind()) && node.children().size() > 1)
        .anyMatch(
            node ->
                "'.'".equals(node.children().get(1).kind())
                    && ClassShapes.writes(SyntaxWalk.text(node.children().get(0)), name)
        );
  }

  private static boolean assigns(final SyntaxNode member, final String name) {
    return Assignments.in(member).stream()
        .anyMatch(assignment -> ClassShapes.writes(Assignments.target(assignment), name));
  }
}
