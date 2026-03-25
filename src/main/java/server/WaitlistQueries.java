package server;

import java.util.List;

final class WaitlistQueries {

  private WaitlistQueries() {}

  static int findFirstByCustomer(List<WaitlistEntry> entries, String customerID) {
    if (entries == null) {
      return -1;
    }
    for (int i = 0; i < entries.size(); i++) {
      WaitlistEntry entry = entries.get(i);
      if (entry.customerID.equals(customerID)) {
        return i;
      }
    }
    return -1;
  }

  static int findExact(
      List<WaitlistEntry> entries, String customerID, String startDate, String endDate) {
    if (entries == null) {
      return -1;
    }
    for (int i = 0; i < entries.size(); i++) {
      WaitlistEntry entry = entries.get(i);
      if (entry.customerID.equals(customerID)
          && entry.startDate.equals(startDate)
          && entry.endDate.equals(endDate)) {
        return i;
      }
    }
    return -1;
  }

  static boolean containsExact(
      List<WaitlistEntry> entries, String customerID, String startDate, String endDate) {
    return findExact(entries, customerID, startDate, endDate) >= 0;
  }

  static int removeFirstByCustomer(List<WaitlistEntry> entries, String customerID) {
    int idx = findFirstByCustomer(entries, customerID);
    if (idx >= 0) {
      entries.remove(idx);
    }
    return idx;
  }
}

