package dev.ward.analysis.domain.rust;

import static dev.ward.analysis.domain.rust.RustScopeFixtures.assertResolves;

import dev.ward.analysis.domain.scope.UnitScopes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RustScopeAnalysisTest {
  private static final String SOURCE =
      """
      use std::collections::HashMap;
      use std::io::{self, Read as Reader};

      const LIMIT: u32 = 10;

      fn total(values: &[u32], scale: u32) -> u32 {
          let sum = 0;
          let sum = sum + scale;
          let scale = 2;
          {
              let sum = sum * scale;
              let _ = sum;
          }
          let doubled = values.iter().map(|value| value * scale).sum::<u32>();
          match Some(sum) {
              Some(sum) if sum > LIMIT => sum,
              Some(other) => other + doubled,
              None => helper(),
          }
      }

      fn helper() -> u32 {
          let map: HashMap<u32, u32> = HashMap::new();
          let mut reader: Option<Reader> = None;
          if let Some(found) = map.get(&1) && *found > LIMIT {
              return *found;
          }
          for item in 0..3 {
              println!("{item} {}", map.len());
          }
          io::stdout();
          0
      }
      """;

  @Test
  @DisplayName("scopes and references")
  void everyReferenceResolvesToTheBindingTheCompilerWouldChoose() {
    final UnitScopes scopes = RustScopeFixtures.scopes(SOURCE);

    assertResolves(
        scopes,
        "sum@8=7:9",
        "scale@8=6:26",
        "sum@11=8:9",
        "scale@11=9:9",
        "sum@12=11:13",
        "value@14=14:38",
        "scale@14=9:9",
        "values@14=6:10",
        "sum@15=8:9",
        "sum@16=16:14",
        "LIMIT@16=4:7",
        "other@17=17:14",
        "doubled@17=14:9",
        "helper@18=22:4",
        "HashMap@23=1:23",
        "Reader@24=2:29",
        "map@25=23:9",
        "found@25=25:17",
        "found@26=25:17",
        "item@29=28:9",
        "map@29=23:9",
        "io@31=2:15"
    );
  }
}
