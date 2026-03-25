package server;

final class ServerIdRules {

  private ServerIdRules() {}

  static boolean isValidManager(String managerID, String serverID) {
    return managerID.length() == 8
        && managerID.substring(0, 3).equals(serverID)
        && managerID.charAt(3) == 'M';
  }

  static boolean isValidCustomer(String customerID, String serverID) {
    return customerID.length() == 8
        && customerID.substring(0, 3).equals(serverID)
        && customerID.charAt(3) == 'U';
  }

  static String extractOfficeID(String id) {
    return id.substring(0, 3);
  }
}
