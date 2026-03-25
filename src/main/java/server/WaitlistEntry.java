package server;

import java.io.Serializable;

final class WaitlistEntry implements Serializable {
  final String customerID;
  final String startDate;
  final String endDate;

  WaitlistEntry(String customerID, String startDate, String endDate) {
    this.customerID = customerID;
    this.startDate = startDate;
    this.endDate = endDate;
  }
}

