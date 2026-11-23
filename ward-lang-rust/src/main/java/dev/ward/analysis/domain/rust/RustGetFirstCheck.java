package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports {@code receiver.get(0)} where the receiver is a slice, array, {@code Vec} or {@code
 * VecDeque}, as Clippy's {@code get_first} does. The receiver's type is what the source states, see
 * {@link RustReceiverTypes} and {@link RustLocalTypes}: a parameter or {@code let}, {@code self}, or a field of {@code self}.
 * A receiver whose type defines its own {@code get}, or that is a map or set, is left alone.
 * shortcut: a receiver of unknown type is reported unless the file defines a {@code get} method;
 * upgrade with type facts (#761).
 */
public final class RustGetFirstCheck implements RuleCheck {
  private static final Pattern CALL =
      Pattern.compile("^([A-Za-z_]\\w*)((?:\\.\\w+)*)\\.get\\(0\\)$");
  private static final Set<String> KEYED = Set.of("HashMap", "BTreeMap", "HashSet", "BTreeSet");
  private static final String SELF = "self";

  private final String message;

  /** Creates the check with the message of each finding. */
  public RustGetFirstCheck(final String message) {
    this.message = message;
  }

  /** The impl or trait that surrounds a node, and the typed locals of its function. */
  private record Context(String owner, Map<String, String> locals) {}

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final FilePass run = new FilePass(new RustReceiverTypes(root));
    run.walk(root, new Context("", Map.of()));
    return run.found;
  }

  /** One pass over a file. */
  private final class FilePass {
    private final RustReceiverTypes types;
    private final List<RuleMatch> found = new ArrayList<>();
    private final Set<Span> seen = new HashSet<>();

    FilePass(final RustReceiverTypes types) {
      this.types = types;
    }

    void walk(final SyntaxNode node, final Context outer) {
      final Context context = enter(node, outer);
      if (RustKinds.EXPRESSION.equals(node.kind()) && !seen.contains(node.span())) {
        final Matcher call = CALL.matcher(RustTrees.text(node));
        if (call.matches() && !skips(call, context)) {
          seen.add(node.span());
          found.add(new RuleMatch(node.span(), message, List.of()));
        }
      }
      node.children().forEach(child -> walk(child, context));
    }

    private Context enter(final SyntaxNode node, final Context outer) {
      if (RustTypeNames.isImpl(node)) {
        return new Context(RustTypeNames.implType(node), outer.locals());
      }
      if (RustKinds.FUNCTION.equals(node.kind())) {
        return new Context(outer.owner(), RustLocalTypes.of(node));
      }
      return outer;
    }

    private boolean skips(final Matcher call, final Context context) {
      final String type = receiverType(call.group(1), call.group(2), context);
      if (RustTypeNames.SEQUENCE.equals(type)) {
        return false;
      }
      if (RustTypeNames.UNKNOWN.equals(type)) {
        return types.definesGet();
      }
      return KEYED.contains(type) || types.hasOwnGet(type);
    }

    private String receiverType(final String head, final String path, final Context context) {
      if (SELF.equals(head)) {
        return path.isEmpty()
            ? context.owner()
            : types.fieldType(context.owner(), path.substring(1));
      }
      return path.isEmpty()
          ? context.locals().getOrDefault(head, RustTypeNames.UNKNOWN)
          : RustTypeNames.UNKNOWN;
    }
  }
}
