package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.application.license.LicenseService;
import dev.ward.domain.config.LicenseSettings;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.dependency.dotnet.DotnetDependencySource;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import dev.ward.infrastructure.license.dotnet.NugetLicenseMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotnetLicenseGateTest {
  @TempDir Path packages;

  private void nuspec(final String id, final String version, final String license)
      throws IOException {
    final Path dir = packages.resolve(id.toLowerCase(java.util.Locale.ROOT)).resolve(version);
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve(id.toLowerCase(java.util.Locale.ROOT) + ".nuspec"),
        "<package xmlns=\"http://schemas.microsoft.com/packaging/2013/05/nuspec.xsd\"><metadata>"
            + license
            + "</metadata></package>"
    );
  }

  @Test
  @DisplayName("a license outside the allowed list")
  void aLicenseOutsideTheAllowedList() throws IOException {
    nuspec("Newtonsoft.Json", "13.0.3", "<license type=\"expression\">MIT</license>");
    nuspec("Serilog.Sinks.File", "6.0.0", "<license type=\"expression\">GPL-3.0-only</license>");
    nuspec("Serilog", "4.0.0", "<license type=\"expression\">Apache-2.0</license>");
    final Map<Ecosystem, LicenseMetadata> readers = new EnumMap<>(Ecosystem.class);
    readers.put(Ecosystem.NUGET, new NugetLicenseMetadata(packages));
    final LicenseService service =
        new LicenseService(
            new DependencyInventoryService(
                Map.of(BuildTool.DOTNET, new DotnetDependencySource(new ForyJsonCodec()))
            ),
            readers,
            new BundledLicenseCatalog()
        );
    final LicenseSettings allow =
        new LicenseSettings(
            LicenseSettings.Mode.ALLOW,
            List.of("Apache-2.0", "MIT"),
            List.of(),
            Set.of(),
            true
        );

    final GateResult result =
        new LicenseGate("license", service, allow)
            .execute(
                new Project(
                    InventoryFixtures.fixture("dotnet-lock").toAbsolutePath(),
                    Language.CSHARP,
                    BuildTool.DOTNET,
                    Framework.NONE
                )
            );

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo(LicenseGate.NOT_ALLOWED);
    assertThat(finding.message()).contains("Serilog.Sinks.File:6.0.0").contains("GPL-3.0-only");
    assertThat(result.findings().items()).hasSize(1);
  }
}
