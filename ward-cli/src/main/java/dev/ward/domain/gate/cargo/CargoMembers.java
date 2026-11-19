package dev.ward.domain.gate.cargo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the workspace members of a {@code Cargo.toml} from its text, so that the crates of any Git
 * revision can be found without running cargo.
 *
 * <p>shortcut: only a {@code members} array of string literals in the {@code [workspace]} table is
 * read, with a trailing {@code /*} as the one glob; {@code exclude} and other globs are not seen.
 */
public final class CargoMembers {
  /** The manifest file name. */
  public static final String MANIFEST = "Cargo.toml";

  /** The glob that stands for every crate directly below a directory. */
  public static final String CHILDREN = "/*";

  private static final Pattern WORKSPACE = Pattern.compile(
      "(?ms)^\\[workspace\\]\\s*$(.*?)(?=^\\[|\\z)"
  );
  private static final Pattern MEMBERS = Pattern.compile("(?s)\\bmembers\\s*=\\s*\\[(.*?)\\]");
  private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

  private CargoMembers() {}

  /** Returns the member patterns the manifest lists, such as {@code core} or {@code crates/*}. */
  public static List<String> listed(final String manifest) {
    final List<String> members = new ArrayList<>();
    final Matcher workspace = WORKSPACE.matcher(manifest);
    if (workspace.find()) {
      final Matcher array = MEMBERS.matcher(workspace.group(1));
      if (array.find()) {
        final Matcher quoted = QUOTED.matcher(array.group(1));
        while (quoted.find()) {
          members.add(quoted.group(1).replaceAll("^\\./", "").replaceAll("/+$", ""));
        }
      }
    }
    return List.copyOf(members);
  }
}
