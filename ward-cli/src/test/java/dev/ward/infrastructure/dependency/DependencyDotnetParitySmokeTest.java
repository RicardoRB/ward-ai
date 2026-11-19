package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.dotnet.DotnetDependencySource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Compares the C# inventory with {@code dotnet list package --include-transitive}. */
class DependencyDotnetParitySmokeTest {
  private static final Pattern PACKAGE =
      Pattern.compile("^\\s+> (\\S+)\\s+(?:\\S+\\s+)?(\\S+)\\s*$");

  @ParameterizedTest
  @ValueSource(strings = {"dotnet-lock", "dotnet-assets"})
  void dotnetMatchesListPackage(final String fixture, @TempDir final Path work)
      throws IOException {
    ParitySupport.enabled("dotnet");
    final String dotnet = System.getProperty("ward.dependency.parity.dotnet.binary", "dotnet");
    final Path dir = ParitySupport.copy(fixture, work);
    ParitySupport.run(dir, dotnet, "restore");
    final Set<String> listed = new TreeSet<>();
    for (final String line :
        ParitySupport.run(dir, dotnet, "list", "package", "--include-transitive").lines().toList()) {
      final Matcher pin = PACKAGE.matcher(line);
      if (pin.find()) {
        listed.add(pin.group(1) + "@" + pin.group(2));
      }
    }

    final DependencyInventory inventory =
        new DotnetDependencySource(ParitySupport.CODEC)
            .read(new Project(dir, Language.CSHARP, BuildTool.DOTNET, Framework.NONE));

    assertThat(ParitySupport.inventorySet(inventory, Ecosystem.NUGET)).isEqualTo(listed);
  }
}
