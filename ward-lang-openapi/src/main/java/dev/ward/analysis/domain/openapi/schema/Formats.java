package dev.ward.analysis.domain.openapi.schema;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The string formats ajv-formats checks. A format it does not know is not checked.
 *
 * <p>shortcut: the patterns of ajv-formats in full mode, without its calendar and leap-second rules
 * beyond month lengths; upgrade when a format's edge cases matter.
 */
final class Formats {

  private static final Pattern DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})\\z");
  private static final Pattern TIME =
      Pattern.compile(
          "^(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?(z|[+-]\\d{2}(?::?\\d{2})?)?\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DATE_TIME =
      Pattern.compile("^(.*?)[t ](.*)\\z", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern URI =
      Pattern.compile(
          "^[a-z][a-z0-9+.-]*:(?:\\/?\\/(?:(?:[a-z0-9\\-._~!$&'()*+,;=:]|%[0-9a-f]{2})*@)?(?:\\[(?:(?:[0-9a-f:.]+)|v[0-9a-f]+\\.[a-z0-9\\-._~!$&'()*+,;=:]+)\\]|(?:[a-z0-9\\-._~!$&'()*+,;=]|%[0-9a-f]{2})*)(?::\\d*)?(?:\\/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*|\\/(?:(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+(?:\\/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)?|(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+(?:\\/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)(?:\\?(?:[a-z0-9\\-._~!$&'()*+,;=:@\\/?]|%[0-9a-f]{2})*)?(?:#(?:[a-z0-9\\-._~!$&'()*+,;=:@\\/?]|%[0-9a-f]{2})*)?\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern URI_REFERENCE =
      Pattern.compile(
          "^(?:[a-z][a-z0-9+.-]*:)?(?:\\/?\\/(?:(?:[a-z0-9\\-._~!$&'()*+,;=:]|%[0-9a-f]{2})*@)?(?:\\[(?:(?:[0-9a-f:.]+)|v[0-9a-f]+\\.[a-z0-9\\-._~!$&'()*+,;=:]+)\\]|(?:[a-z0-9\\-._~!$&'()*+,;=]|%[0-9a-f]{2})*)(?::\\d*)?(?:\\/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*|\\/(?:(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+(?:\\/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)?|(?:[a-z0-9\\-._~!$&'()*+,;=@]|%[0-9a-f]{2})+(?:\\/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)?(?:\\?(?:[a-z0-9\\-._~!$&'()*+,;=:@\\/?]|%[0-9a-f]{2})*)?(?:#(?:[a-z0-9\\-._~!$&'()*+,;=:@\\/?]|%[0-9a-f]{2})*)?\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern EMAIL =
      Pattern.compile(
          "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern HOSTNAME =
      Pattern.compile(
          "^(?=.{1,253}\\.?\\z)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[-0-9a-z]{0,61}[0-9a-z])?)*\\.?\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern IPV4 =
      Pattern.compile(
          "^(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\z");
  private static final Pattern IPV6 =
      Pattern.compile(
          "^((([0-9a-f]{1,4}:){7}([0-9a-f]{1,4}|:))|(([0-9a-f]{1,4}:){6}(:[0-9a-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9a-f]{1,4}:){5}(((:[0-9a-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9a-f]{1,4}:){4}(((:[0-9a-f]{1,4}){1,3})|((:[0-9a-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9a-f]{1,4}:){3}(((:[0-9a-f]{1,4}){1,4})|((:[0-9a-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9a-f]{1,4}:){2}(((:[0-9a-f]{1,4}){1,5})|((:[0-9a-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9a-f]{1,4}:){1}(((:[0-9a-f]{1,4}){1,6})|((:[0-9a-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9a-f]{1,4}){1,7})|((:[0-9a-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern UUID =
      Pattern.compile(
          "^(?:urn:uuid:)?[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern BYTE =
      Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?\\z");
  private static final Pattern JSON_POINTER = Pattern.compile("^(?:\\/(?:[^~/]|~0|~1)*)*\\z");
  private static final int[] DAYS = {0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  private static final int LEAP_CYCLE = 4;
  private static final int CENTURY = 100;
  private static final int LONG_CYCLE = 400;
  private static final int FEBRUARY = 2;
  private static final int MAX_HOUR = 23;
  private static final int MAX_MINUTE = 59;
  private static final int MAX_SECOND = 60;
  private static final int MAX_MONTH = 12;
  private static final int SECOND_GROUP = 3;
  private static final int ZONE_GROUP = 5;
  private static final Map<String, Predicate<String>> CHECKS =
      Map.ofEntries(
          Map.entry("date", Formats::date),
          Map.entry("time", Formats::time),
          Map.entry("date-time", Formats::dateTime),
          Map.entry("uri", text -> URI.matcher(text).find()),
          Map.entry("uri-reference", text -> URI_REFERENCE.matcher(text).find()),
          Map.entry("email", text -> EMAIL.matcher(text).find()),
          Map.entry("hostname", text -> HOSTNAME.matcher(text).find()),
          Map.entry("ipv4", text -> IPV4.matcher(text).find()),
          Map.entry("ipv6", text -> IPV6.matcher(text).find()),
          Map.entry("uuid", text -> UUID.matcher(text).find()),
          Map.entry("byte", text -> BYTE.matcher(text).find()),
          Map.entry("json-pointer", text -> JSON_POINTER.matcher(text).find()),
          Map.entry("regex", Formats::regex));

  private Formats() {}

  /** Tells whether {@code text} is valid for {@code format}; an unknown format accepts anything. */
  static boolean valid(final String format, final String text) {
    final Predicate<String> check = CHECKS.get(format);
    return check == null || check.test(text);
  }

  private static boolean date(final String text) {
    final Matcher matcher = DATE.matcher(text);
    if (!matcher.find()) {
      return false;
    }
    final int year = Integer.parseInt(matcher.group(1));
    final int month = Integer.parseInt(matcher.group(2));
    final int day = Integer.parseInt(matcher.group(3));
    if (month < 1 || month > MAX_MONTH || day < 1) {
      return false;
    }
    return day <= (month == FEBRUARY && !leap(year) ? DAYS[month] - 1 : DAYS[month]);
  }

  private static boolean leap(final int year) {
    return year % LEAP_CYCLE == 0 && (year % CENTURY != 0 || year % LONG_CYCLE == 0);
  }

  private static boolean time(final String text) {
    final Matcher matcher = TIME.matcher(text);
    return matcher.find()
        && Integer.parseInt(matcher.group(1)) <= MAX_HOUR
        && Integer.parseInt(matcher.group(2)) <= MAX_MINUTE
        && Integer.parseInt(matcher.group(SECOND_GROUP)) <= MAX_SECOND
        && matcher.group(ZONE_GROUP) != null;
  }

  private static boolean dateTime(final String text) {
    final Matcher matcher = DATE_TIME.matcher(text);
    return matcher.find() && date(matcher.group(1)) && time(matcher.group(2));
  }

  private static boolean regex(final String text) {
    try {
      Pattern.compile(text);
      return true;
    } catch (final PatternSyntaxException exception) {
      return false;
    }
  }
}
