import java.time.ZoneId;

final class Zones {
  ZoneId utc() {
    return ZoneId.of("Z");
  }
}
