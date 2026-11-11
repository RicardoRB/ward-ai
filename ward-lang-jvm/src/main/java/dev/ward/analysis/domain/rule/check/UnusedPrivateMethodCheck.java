package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Reports each private method whose name no call or method reference ({@code Type::name}) in the
 * file names. The check matches names, not bindings, so an overload or another class's method of
 * the same name counts as a use. As in PMD, a method with a framework annotation, a serialization
 * hook, or a parameterless method named by a JUnit {@code @MethodSource} is not reported.
 */
public final class UnusedPrivateMethodCheck implements UnitCheck {

  /** PMD ignores a private method with one of these annotations, which a framework calls. */
  private static final Set<String> FRAMEWORK_ANNOTATIONS =
      Set.of("Deprecated", "PostConstruct", "PreDestroy", "Include");

  private static final Set<String> SERIALIZATION_METHODS =
      Set.of("readObject", "readObjectNoData", "writeObject", "readResolve", "writeReplace");
  private static final String METHOD_SOURCE = "MethodSource";
  private static final String STRING_LITERAL = "STRING_LITERAL";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public UnusedPrivateMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final Set<String> called = calledNames(root);
    final Set<String> sources = methodSources(root);
    return SyntaxWalk.visits(root, Set.of("classBody")).stream()
        .flatMap(visit -> JUnitMembers.members(visit.node()).stream())
        .filter(JUnitMembers::isPrivate)
        .filter(
            member -> Collections.disjoint(JUnitMembers.annotations(member), FRAMEWORK_ANNOTATIONS))
        .flatMap(member -> JUnitMembers.method(member).stream())
        .filter(method -> !called.contains(JUnitMembers.name(method)))
        .filter(method -> !SERIALIZATION_METHODS.contains(JUnitMembers.name(method)))
        .filter(
            method ->
                !(JUnitMembers.hasNoParameters(method)
                    && sources.contains(JUnitMembers.name(method))))
        .map(
            method ->
                CheckSupport.at(
                    method.span(), CheckSupport.fill(message, JUnitMembers.name(method), "")))
        .toList();
  }

  /** The method names that the string values of {@code @MethodSource} annotations name. */
  private static Set<String> methodSources(final SyntaxNode root) {
    return SyntaxWalk.descendants(root).stream()
        .filter(node -> "annotation".equals(node.kind()))
        .filter(UnusedPrivateMethodCheck::isMethodSource)
        .flatMap(node -> SyntaxWalk.descendants(node).stream())
        .filter(node -> STRING_LITERAL.equals(node.kind()))
        .map(node -> SyntaxWalk.text(node).replace("\"", ""))
        .map(text -> text.substring(text.indexOf('#') + 1).replace("()", ""))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean isMethodSource(final SyntaxNode annotation) {
    final String text = SyntaxWalk.text(annotation);
    final int open = text.indexOf('(');
    final String name = open < 0 ? text : text.substring(0, open);
    return METHOD_SOURCE.equals(name.substring(name.lastIndexOf('.') + 1).replace("@", ""));
  }

  private static Set<String> calledNames(final SyntaxNode root) {
    final List<SyntaxNode> nodes = SyntaxWalk.descendants(root);
    return Stream.concat(
            nodes.stream()
                .filter(node -> "methodCall".equals(node.kind()))
                .flatMap(call -> call.children().stream())
                .filter(child -> "identifier".equals(child.kind())),
            nodes.stream().flatMap(UnusedPrivateMethodCheck::referenced))
        .map(SyntaxWalk::text)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The identifier after the {@code ::} of a method reference such as {@code Type::name}. */
  private static Stream<SyntaxNode> referenced(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    return IntStream.range(0, parts.size())
        .filter(index -> "'::'".equals(parts.get(index).kind()))
        .mapToObj(index -> parts.subList(index + 1, parts.size()))
        .flatMap(rest -> rest.stream().filter(part -> "identifier".equals(part.kind())).limit(1));
  }
}
