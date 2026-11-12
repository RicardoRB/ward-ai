package dev.ward.domain.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PackageIdTest {
  @Test
  void npmScopeIsPercentEncodedInThePackageUrl() {
    assertThat(new PackageId(Ecosystem.NPM, "@types/node", "22.10.1").purl())
        .isEqualTo("pkg:npm/%40types/node@22.10.1");
  }

  @Test
  void mavenCoordinatesBecomeNamespaceAndName() {
    assertThat(new PackageId(Ecosystem.MAVEN, "com.google.guava:guava", "33.0.0-jre").purl())
        .isEqualTo("pkg:maven/com.google.guava/guava@33.0.0-jre");
  }

  @Test
  void swiftHostAndPathStayInThePackageUrl() {
    assertThat(new PackageId(Ecosystem.SWIFT_URL, "github.com/apple/swift-nio", "2.80.0").purl())
        .isEqualTo("pkg:swift/github.com/apple/swift-nio@2.80.0");
  }

  @Test
  void pypiNameIsUsedAsGiven() {
    assertThat(new PackageId(Ecosystem.PYPI, "django-rest-framework", "3.15.2").purl())
        .isEqualTo("pkg:pypi/django-rest-framework@3.15.2");
  }

  @Test
  void ecosystemsNameThemselvesAsOsvDoes() {
    assertThat(Ecosystem.SWIFT_URL.osvName()).isEqualTo("SwiftURL");
    assertThat(Ecosystem.PYPI.osvName()).isEqualTo("PyPI");
    assertThat(Ecosystem.MAVEN.purlType()).isEqualTo("maven");
  }

  @Test
  void orderIsEcosystemNameThenVersion() {
    final PackageId low = new PackageId(Ecosystem.NPM, "semver", "6.3.1");
    final PackageId high = new PackageId(Ecosystem.NPM, "semver", "7.6.3");
    final PackageId maven = new PackageId(Ecosystem.MAVEN, "z:z", "1");

    assertThat(java.util.List.of(high, low, maven).stream().sorted())
        .containsExactly(maven, low, high);
  }

  @Test
  void blankVersionIsRejected() {
    assertThatThrownBy(() -> new PackageId(Ecosystem.NPM, "a", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
