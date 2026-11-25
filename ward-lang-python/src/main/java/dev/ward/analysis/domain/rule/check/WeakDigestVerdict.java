package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.flow.taint.TaintedUses;
import dev.ward.analysis.domain.flow.taint.TaintedUses.Found;
import dev.ward.analysis.domain.flow.taint.TaintedUses.Origin;
import dev.ward.analysis.domain.ir.Confidence;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.DigestUse;
import dev.ward.analysis.domain.python.DigestUse.Kind;
import dev.ward.analysis.domain.python.DigestUses;
import dev.ward.analysis.domain.rule.FlowView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the uses of one weak digest say about it.
 *
 * <p>The finding is proven when a use compares the digest or gives it to an authentication
 * function, when the digest is returned and no call of the file takes it, or when it is used for
 * nothing. It is possible when some use goes where the analysis does not follow it, or when the
 * digest is returned to callers of the file, whose other callers in other files are not seen. There
 * is no finding when every use is only an identifier: a file name, a path or a key.
 *
 * @param confidence how sure the finding is
 * @param assumptions what the analysis assumed, sorted; empty when the finding is proven
 */
record WeakDigestVerdict(Confidence confidence, List<String> assumptions) {
  private static final String IGNORED = "usedforsecurity";

  /** Copies the assumptions. */
  WeakDigestVerdict {
    assumptions = List.copyOf(assumptions);
  }

  static WeakDigestVerdict proven() {
    return new WeakDigestVerdict(Confidence.PROVEN, List.of());
  }

  /** Whether the call states with {@code usedforsecurity=False} that it is not for security. */
  static boolean ignored(final SyntaxNode root, final FlowSite site) {
    return DigestUses.callArgumentsText(root, site.span()).replace(" ", "").contains(
        IGNORED + "=False"
    );
  }

  /**
   * The verdict on the digest that {@code site} makes, or none when it is only an identifier.
   *
   * @param root the syntax tree of the file
   * @param flow the graphs of the file and of the files it depends on
   * @param spec the specification that names the weak hash calls
   * @param site the weak hash call
   */
  static Optional<WeakDigestVerdict> of(
      final SyntaxNode root,
      final FlowView flow,
      final TaintSpec spec,
      final FlowSite site
  ) {
    final Set<String> names = new LinkedHashSet<>();
    DigestWalk walk = walk(root, flow, spec, site, names);
    while (!names.containsAll(walk.returned)) {
      names.addAll(walk.returned);
      walk = walk(root, flow, spec, site, names);
    }
    return walk.verdict(site, names);
  }

  /** One walk, following the calls of the functions that return the digest, by name. */
  private static DigestWalk walk(
      final SyntaxNode root,
      final FlowView flow,
      final TaintSpec spec,
      final FlowSite site,
      final Set<String> names
  ) {
    final List<TaintEntry> callers =
        names.stream()
            .map(
                name -> new TaintEntry(
                    FlowSiteKind.CALL,
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    List.of()
                )
            )
            .toList();
    final Found found =
        TaintedUses.of(flow.graphs(), flow.related(), spec, callers, Optional.of(site.span()));
    final DigestWalk walk = new DigestWalk(root, flow, found);
    walk.collect(site, names);
    return walk;
  }

  /** One walk over the uses of a digest and of the calls that return it. */
  private static final class DigestWalk {
    private final SyntaxNode root;
    private final FlowView flow;
    private final Found found;
    private final Set<String> assumptions = new TreeSet<>();
    private final Set<String> returned = new TreeSet<>();
    private boolean benign;
    private boolean seenCallers;
    private boolean security;

    DigestWalk(final SyntaxNode root, final FlowView flow, final Found found) {
      this.root = root;
      this.flow = flow;
      this.found = found;
    }

    /** Classifies the uses of the digest and of the values that its callers get. */
    void collect(final FlowSite site, final Set<String> names) {
      security |= classify(site.span(), originGraph(site.span()));
      for (final String name : names) {
        final List<Origin> callers = callers(name);
        seenCallers |= !callers.isEmpty();
        for (final Origin caller : callers) {
          security |= classify(caller.site().span(), caller.graph());
        }
      }
    }

    Optional<WeakDigestVerdict> verdict(final FlowSite site, final Set<String> names) {
      if (security || names.stream().anyMatch(name -> callers(name).isEmpty())) {
        return Optional.of(proven());
      }
      if (!assumptions.isEmpty() || seenCallers) {
        return Optional.of(possible());
      }
      return benign ? Optional.empty() : Optional.of(proven());
    }

    private WeakDigestVerdict possible() {
      final List<String> all = new ArrayList<>(assumptions);
      if (seenCallers) {
        all.add("the callers of the function that returns the digest in other files are not seen");
      }
      return new WeakDigestVerdict(Confidence.POSSIBLE, all.stream().sorted().distinct().toList());
    }

    /** Notes the uses of the value made at {@code origin}; whether one is a security use. */
    private boolean classify(final Span origin, final int graph) {
      final List<DigestUse> uses = new ArrayList<>();
      uses.add(DigestUses.ofCall(root, origin));
      found.reads().stream()
          .filter(read -> read.origin().equals(origin))
          .forEach(read -> uses.add(DigestUses.ofRead(root, read.span())));
      uses.forEach(use -> note(use, graph));
      return uses.stream().anyMatch(use -> use.kind() == Kind.SECURITY);
    }

    private void note(final DigestUse use, final int graph) {
      switch (use.kind()) {
        case BENIGN -> benign = true;
        case UNKNOWN -> assumptions.add(
            "the digest is used where it is not followed: " + use.detail()
        );
        case ESCAPE -> returned.add(functionOf(graph));
        default -> {}
      }
    }

    private List<Origin> callers(final String name) {
      return found.origins().stream()
          .filter(origin -> origin.derived() && origin.site().name().equals(name))
          .toList();
    }

    private int originGraph(final Span span) {
      return found.origins().stream()
          .filter(origin -> !origin.derived() && origin.site().span().equals(span))
          .map(Origin::graph)
          .findFirst()
          .orElse(0);
    }

    /** The name of the function of a graph, such as {@code f} for {@code m#f} or {@code C#f(2)}. */
    private String functionOf(final int graph) {
      final String body = flow.graphs().get(graph).body();
      final int start = body.lastIndexOf('#') + 1;
      final int end = body.indexOf('(', start);
      return end < 0 ? body.substring(start) : body.substring(start, end);
    }
  }
}
