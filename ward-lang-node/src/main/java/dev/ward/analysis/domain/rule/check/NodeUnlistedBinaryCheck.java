package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeManifest.Member;
import dev.ward.analysis.domain.node.NodeManifest.Value;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

/**
 * Reports a command that a package script runs when the installed package that provides it is not
 * listed in the package's manifest, as knip reports unlisted binaries. The provider is found from
 * the {@code bin} field of the installed packages; with no installed package, or one that is not
 * installed, nothing is reported. System and package-manager commands are never reported.
 *
 * <p>shortcut: reads the installed packages at the root only, and checks each workspace against its
 * own dependencies; a command provided by a root-listed package is reported in a workspace that
 * does not list it. Upgrade by reading each workspace's installed packages.
 */
public final class NodeUnlistedBinaryCheck implements RuleCheck, ProjectCheck {

  private static final Set<String> SYSTEM_COMMANDS =
      Set.of(
          "node",
          "npm",
          "npx",
          "pnpm",
          "yarn",
          "bun",
          "corepack",
          "git",
          "sh",
          "bash",
          "zsh",
          "cd",
          "echo",
          "rm",
          "cp",
          "mv",
          "mkdir",
          "cat",
          "env",
          "export",
          "test",
          "exit",
          "true",
          "false",
          "sleep");
  private static final Pattern SEPARATORS = Pattern.compile("[\\s;&|()]+");
  private static final List<String> SECTIONS =
      List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
  private static final String VALUE = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the command name
   */
  public NodeUnlistedBinaryCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    final Map<String, String> providers = providers(files);
    final List<ProjectMatch> found = new ArrayList<>();
    for (final NodeManifests.Manifest owner : NodeManifests.byDirectory(files).values()) {
      final Set<String> listed = NodeManifests.names(owner.manifest(), SECTIONS);
      for (final Member script : owner.manifest().entries("scripts")) {
        if (script.value() instanceof Value.Text text) {
          found.addAll(unlisted(owner.path(), script, text.value(), providers, listed));
        }
      }
    }
    return List.copyOf(found);
  }

  /**
   * The package that provides each command name, from the installed packages' {@code bin} fields.
   */
  private static Map<String, String> providers(final ProjectFiles files) {
    final Map<String, String> providers = new HashMap<>();
    NodeInstalledPackages.installed(files)
        .forEach(
            (name, manifest) ->
                NodeInstalledPackages.binaryNames(name, manifest.manifest())
                    .forEach(command -> providers.put(command, name)));
    return providers;
  }

  /** The commands of one script whose provider the manifest does not list. */
  private List<ProjectMatch> unlisted(
      final String path,
      final Member script,
      final String command,
      final Map<String, String> providers,
      final Set<String> listed) {
    final Set<String> reported = new HashSet<>();
    final List<ProjectMatch> found = new ArrayList<>();
    for (final String token : SEPARATORS.split(command.strip())) {
      final String provider = providers.get(token);
      if (provider != null
          && !listed.contains(provider)
          && !SYSTEM_COMMANDS.contains(token)
          && reported.add(token)) {
        found.add(new ProjectMatch(path, match(script, token)));
      }
    }
    return found;
  }

  private RuleMatch match(final Member script, final String command) {
    final Span at = new Span(script.line(), script.column(), script.line(), script.column(), 0, 0);
    return new RuleMatch(at, message.replace(VALUE, command));
  }
}
