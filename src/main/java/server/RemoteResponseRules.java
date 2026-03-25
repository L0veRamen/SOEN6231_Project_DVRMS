package server;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RemoteResponseRules {

  private static final Pattern PRICE_PATTERN =
      Pattern.compile("(?:Cost|Refund):\\s*([0-9]+(?:\\.[0-9]+)?)");

  private RemoteResponseRules() {}

  static RemoteResponseType classifyResponse(String response) {
    if (response == null) {
      return RemoteResponseType.OTHER;
    }
    if (response.startsWith("SUCCESS")) {
      return RemoteResponseType.SUCCESS;
    }
    if (response.startsWith("FAIL")) {
      return RemoteResponseType.FAIL;
    }
    if (response.startsWith("WAITLIST")) {
      return RemoteResponseType.WAITLIST;
    }
    if (response.startsWith("PROMOTED")) {
      return RemoteResponseType.PROMOTED;
    }
    return RemoteResponseType.OTHER;
  }

  static double extractPrice(String response, Consumer<String> logger) {
    if (response == null || response.isEmpty()) {
      return 0;
    }
    Matcher matcher = PRICE_PATTERN.matcher(response);
    if (matcher.find()) {
      try {
        return Double.parseDouble(matcher.group(1));
      } catch (NumberFormatException e) {
        logger.accept("Error extracting price: " + e.getMessage());
      }
    }
    return 0;
  }

  static boolean isReservationUpdatedSuccess(String response) {
    return response != null && response.startsWith("SUCCESS: Reservation updated.");
  }

  static String appendNewBudgetInfo(String response, double newBudget) {
    if (response == null || response.contains("New budget:")) {
      return response;
    }
    String trimmed = response.trim();
    if (trimmed.endsWith(".")) {
      return trimmed + " New budget: " + newBudget;
    }
    return trimmed + ". New budget: " + newBudget;
  }
}

