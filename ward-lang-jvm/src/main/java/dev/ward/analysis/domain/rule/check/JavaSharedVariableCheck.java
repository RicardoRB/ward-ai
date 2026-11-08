package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an unlocked write of a field in a class that other code runs in several threads, as
 * SpotBugs' SharedVariableAtomicityDetector does. A read-modify-write of a primitive or boxed field
 * is {@code ARITHMETIC}, any other write of a non-volatile {@code long} or {@code double} is {@code
 * WIDE}, and any other write of a non-volatile primitive is {@code STALE}. The write is only
 * reported if its method can run with no lock held and another method of the class reads the field
 * in the same way. A class is multi-threaded if it is a {@code Runnable} or {@code Thread}, has a
 * volatile or atomic or lock field, or has a synchronized method or block.
 */
public final class JavaSharedVariableCheck implements RuleCheck {

  /** What the check reports. */
  public enum Mode {
    /** A read-modify-write of a shared field. */
    ARITHMETIC,
    /** A plain write of a long or double. */
    WIDE,
    /** A plain write of another primitive. */
    STALE
  }

  private static final Set<String> WIDE_TYPES = Set.of("long", "double");
  private static final Set<String> PRIMITIVES =
      Set.of("byte", "short", "int", "long", "float", "double", "char", "boolean");
  private static final Set<String> BOXED =
      Set.of("Byte", "Short", "Integer", "Long", "Float", "Double", "Character", "Boolean");

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what to report
   * @param message the message; {@code {value}} is the field
   */
  public JavaSharedVariableCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> imports = JavaAnnotations.imports(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      if (!MultiThreaded.notThreadSafe(JavaNodes.modifiers(visit))
          && MultiThreaded.is(visit.node(), imports)) {
        matches.addAll(report(visit.node()));
      }
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> report(final SyntaxNode clazz) {
    final FieldAccessScan scan = new FieldAccessScan(clazz);
    return scan.accesses().stream()
        .filter(access -> reported(scan, access))
        .map(
            access ->
                CheckSupport.at(
                    access.node().span(), CheckSupport.fill(message, access.field().name(), "")))
        .toList();
  }

  private boolean reported(final FieldAccessScan scan, final FieldAccessScan.Access write) {
    final FieldAccessScan.Field field = write.field();
    return write.kind() == FieldAccessScan.Kind.WRITE
        && write.lock() == FieldAccessScan.Lock.NONE
        && !field.has(MemberDeclarations.FINAL)
        && classify(write).filter(mode::equals).isPresent()
        && scan.reachable(write.method())
        && scan.accesses().stream()
            .anyMatch(
                read ->
                    read.field().equals(field)
                        && read.kind() == FieldAccessScan.Kind.READ
                        && read.lock() == FieldAccessScan.Lock.NONE
                        && !read.method().equals(write.method())
                        && scan.reachable(read.method()));
  }

  private static Optional<Mode> classify(final FieldAccessScan.Access write) {
    final String type = write.field().type();
    final boolean primitive = PRIMITIVES.contains(type);
    if (write.arithmetic() && (primitive || BOXED.contains(type))) {
      return Optional.of(Mode.ARITHMETIC);
    }
    if (write.field().has("volatile") || !primitive || write.arithmetic()) {
      return Optional.empty();
    }
    return Optional.of(WIDE_TYPES.contains(type) ? Mode.WIDE : Mode.STALE);
  }
}
