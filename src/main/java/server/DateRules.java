package server;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

final class DateRules {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);

  DateRules() {}

  LocalDate parseDate(String dateStr) {
    if (dateStr == null) {
      return null;
    }
    try {
      return LocalDate.parse(dateStr, DATE_FMT);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  boolean datesOverlapInclusive(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
    return !s1.isAfter(e2) && !s2.isAfter(e1);
  }
}
