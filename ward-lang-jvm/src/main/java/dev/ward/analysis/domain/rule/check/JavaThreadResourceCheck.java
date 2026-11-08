package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a field that code started as {@code new Thread(...)} assigns and also calls a method on,
 * from at least two methods, with no lock held at some access, as SpotBugs'
 * AT_UNSAFE_RESOURCE_ACCESS_IN_THREAD does. The thread code is the lambda or {@code this::method}
 * handed to the constructor, and the methods of the class it calls. SpotBugs also counts a call
 * whose name looks like a setter as a change; here only an assignment of the field counts, so this
 * reports a subset of what SpotBugs does. Atomic fields and fields set to a synchronized collection
 * are left out.
 */
public final class JavaThreadResourceCheck implements RuleCheck {

  private static final Pattern THIS_REFERENCE = Pattern.compile("^this::(\\w+)$");

  private final String message;

  private record Region(String key, SyntaxNode node, Set<String> shadow, boolean synced) {
    static Region ofMethod(final SyntaxNode member) {
      final SyntaxNode declaration = ClassMembers.declarationOf(member);
      return new Region(
          MemberDeclarations.name(declaration),
          declaration,
          ClassShapes.declaredNames(declaration),
          MemberDeclarations.modifiers(member).contains("synchronized"));
    }
  }

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field
   */
  public JavaThreadResourceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> imports = JavaAnnotations.imports(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      matches.addAll(report(visit.node(), imports));
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> report(final SyntaxNode clazz, final Set<String> imports) {
    final FieldAccessScan scan = FieldAccessScan.fieldsOnly(clazz);
    final Map<String, Region> methods = methods(clazz);
    final Deque<Region> queue = new ArrayDeque<>(seeds(clazz, methods));
    final Set<String> seen = new HashSet<>();
    while (!queue.isEmpty()) {
      final Region region = queue.poll();
      if (seen.add(region.key())) {
        scan.scanRegion(
            region.node(),
            region.synced() ? FieldAccessScan.Lock.THIS : FieldAccessScan.Lock.NONE,
            region.key(),
            region.shadow());
        scan.callees(region.key()).stream()
            .filter(methods::containsKey)
            .forEach(callee -> queue.add(methods.get(callee)));
      }
    }
    return fieldMatches(scan, imports, SyntaxWalk.text(clazz));
  }

  private List<RuleMatch> fieldMatches(
      final FieldAccessScan scan, final Set<String> imports, final String classText) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final FieldAccessScan.Field field : scan.fields().values()) {
      if (shared(field, imports, classText)) {
        final List<FieldAccessScan.Access> accesses =
            scan.accesses().stream()
                .filter(access -> access.field().equals(field))
                .filter(access -> access.kind() == FieldAccessScan.Kind.WRITE || access.receiver())
                .toList();
        if (unsafe(accesses)) {
          accesses.forEach(
              access ->
                  matches.add(
                      CheckSupport.at(
                          access.node().span(), CheckSupport.fill(message, field.name(), ""))));
        }
      }
    }
    return matches;
  }

  private static boolean unsafe(final List<FieldAccessScan.Access> accesses) {
    final boolean modified =
        accesses.stream().anyMatch(a -> a.kind() == FieldAccessScan.Kind.WRITE);
    final boolean call = accesses.stream().anyMatch(a -> a.kind() == FieldAccessScan.Kind.READ);
    final boolean unlocked = accesses.stream().anyMatch(a -> a.lock() == FieldAccessScan.Lock.NONE);
    final long methods = accesses.stream().map(FieldAccessScan.Access::method).distinct().count();
    return modified && call && unlocked && methods > 1;
  }

  private static boolean shared(
      final FieldAccessScan.Field field, final Set<String> imports, final String classText) {
    final String type = field.type().replaceAll("<.*>", "");
    final String simple = ClassMembers.simpleName(type);
    final boolean atomic =
        simple.startsWith("Atomic")
            && (type.startsWith("java.util.concurrent.atomic.")
                || JavaAnnotations.resolves(
                    imports, simple, "java.util.concurrent.atomic." + simple));
    final boolean wrapped =
        Pattern.compile(
                "(?:this\\.)?" + Pattern.quote(field.name()) + "=Collections\\.synchronized")
            .matcher(classText)
            .find();
    return !atomic && !wrapped;
  }

  private static Map<String, Region> methods(final SyntaxNode clazz) {
    final Map<String, Region> methods = new LinkedHashMap<>();
    for (final SyntaxNode member : ClassShapes.methods(clazz)) {
      final Region region = Region.ofMethod(member);
      methods.putIfAbsent(region.key(), region);
    }
    return methods;
  }

  private static List<Region> seeds(final SyntaxNode clazz, final Map<String, Region> methods) {
    final List<Region> seeds = new ArrayList<>();
    for (final SyntaxNode member : ClassShapes.methods(clazz)) {
      final SyntaxNode declaration = ClassMembers.declarationOf(member);
      final Set<String> shadow = ClassShapes.declaredNames(declaration);
      for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(declaration, Set.of("creator"))) {
        if (isThread(visit)) {
          argument(visit.node()).ifPresent(arg -> seed(arg, shadow, methods, seeds));
        }
      }
    }
    return seeds;
  }

  private static void seed(
      final SyntaxNode argument,
      final Set<String> shadow,
      final Map<String, Region> methods,
      final List<Region> seeds) {
    final List<SyntaxNode> parts = argument.children();
    if (parts.size() == 1 && "lambdaExpression".equals(parts.get(0).kind())) {
      final SyntaxNode lambda = parts.get(0);
      final SyntaxNode body = lambda.children().get(lambda.children().size() - 1);
      seeds.add(new Region("lambda@" + lambda.span().startOffset(), body, shadow, false));
    } else {
      final Matcher reference = THIS_REFERENCE.matcher(SyntaxWalk.text(argument));
      if (reference.matches() && methods.containsKey(reference.group(1))) {
        seeds.add(methods.get(reference.group(1)));
      }
    }
  }

  private static boolean isThread(final SyntaxWalk.Visit visit) {
    final String type = JavaNodes.kid(visit.node(), "createdName").map(SyntaxWalk::text).orElse("");
    final boolean hook =
        visit.ancestors().stream()
            .anyMatch(
                node ->
                    "methodCall".equals(node.kind())
                        && JavaNodes.kid(node, "identifier")
                            .map(SyntaxWalk::text)
                            .filter("addShutdownHook"::equals)
                            .isPresent());
    return ("Thread".equals(type) || "java.lang.Thread".equals(type)) && !hook;
  }

  private static Optional<SyntaxNode> argument(final SyntaxNode creator) {
    return JavaNodes.kid(creator, "classCreatorRest")
        .flatMap(rest -> JavaNodes.kid(rest, "arguments"))
        .flatMap(args -> JavaNodes.kid(args, "expressionList"))
        .flatMap(list -> JavaNodes.kids(list, "expression").stream().findFirst());
  }
}
