package dev.ward.application.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.infrastructure.license.BundledLicenseTexts;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoticeServiceTest {
  private static final Path DIRECTORY = Path.of("/work/app");
  private static final String APACHE_BODY = "TERMS AND CONDITIONS FOR USE";

  private final NoticeService notice =
      new NoticeService(
          LicenseFixtures.detector(), LicenseFixtures.mixed(), new BundledLicenseTexts());

  @Test
  void listsPackagesUnderTheirLicenseAndEndsWithTheTexts() {
    final String text = notice.generate(DIRECTORY, Set.of()).content();

    assertThat(text)
        .startsWith("Lists of 5 third-party dependencies.")
        .contains("\nApache-2.0\n    @scope/pkg:1.0.0 [npm]")
        .contains("\nUnknown license\n    mystery:2.0.0 [npm]")
        .contains("\nMIT OR GPL-3.0-only\n    left-pad:1.3.0 [npm]");
    assertThat(text.indexOf("GNU GENERAL PUBLIC LICENSE")).isPositive();
    assertThat(text).contains("The text of EPL-1.0 is not bundled with Ward.");
    assertThat(text.indexOf(APACHE_BODY))
        .as("the Apache text appears once")
        .isPositive()
        .isEqualTo(text.lastIndexOf(APACHE_BODY));
  }

  @Test
  void isByteIdenticalAndHandlesNoDependencies() {
    final NoticeService empty =
        new NoticeService(
            LicenseFixtures.detector(),
            LicenseFixtures.service(LicenseFixtures.inventory(), Map.of()),
            new BundledLicenseTexts());

    assertThat(notice.generate(DIRECTORY, Set.of()).content())
        .isEqualTo(notice.generate(DIRECTORY, Set.of()).content());
    assertThat(empty.generate(DIRECTORY, Set.of()).content())
        .isEqualTo("The project has no dependencies.\n");
  }

  @Test
  void warnsAboutUnknownLicenses() {
    assertThat(notice.generate(DIRECTORY, Set.of()).warnings())
        .singleElement()
        .asString()
        .contains("mystery");
  }

  @Test
  void anUnusableInventoryProducesNoDocument() {
    final NoticeService failing =
        new NoticeService(
            LicenseFixtures.detector(), LicenseFixtures.failing(), new BundledLicenseTexts());

    assertThatThrownBy(() -> failing.generate(DIRECTORY, Set.of()))
        .isInstanceOf(WardException.class);
  }

  @Test
  void reportsEachPackageOnceAcrossModules() {
    final Dependency shared =
        LicenseFixtures.dependency(Ecosystem.NPM, "shared", "1.0.0", DependencyScope.RUNTIME, "");
    final DependencyInventory inventory =
        new DependencyInventory(
            List.of(
                new ModuleDependencies("", List.of(shared)),
                new ModuleDependencies("packages/a", List.of(shared))));

    assertThat(
            LicenseFixtures.service(inventory, Map.of("shared", LicenseFixtures.declared("MIT")))
                .licenses(LicenseFixtures.NPM, Set.of()))
        .hasSize(1);
  }
}
