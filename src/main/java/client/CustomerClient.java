package client;

import client.generated.VehicleReservationWS;
import java.util.Scanner;

/**
 * SOAP-based customer client for the DVRMS system.
 *
 * <p>Run with: {@code java client.CustomerClient}
 *
 * <p>Uses wsimport-generated SOAP stubs from the office WSDL endpoint.
 */
public class CustomerClient {

  private final String customerID;
  private VehicleReservationWS server;
  private final String serverID;

  public CustomerClient(String customerID) {
    this.customerID = customerID;
    this.serverID = customerID.substring(0, 3); // Extract MTL, WPG, or BNF
  }

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);

    System.out.println("===== DVRMS Customer Client (SOAP) =====");
    System.out.print("Enter Customer ID (e.g., MTLU1111): ");
    String customerID = scanner.nextLine().toUpperCase();

    // Validate format
    if (customerID.length() != 8 || customerID.charAt(3) != 'U') {
      System.err.println("Invalid Customer ID format. Must be like MTLU1111");
      return;
    }

    CustomerClient client = new CustomerClient(customerID);
    WsEndpoint endpoint = ClientRuntimeSupport.resolveWsEndpoint(args, client.serverID);
    System.out.println("Preflight:");
    System.out.println("- Customer ID: " + customerID);
    System.out.println("- Target office: " + client.serverID);
    System.out.println("- WSDL endpoint: " + endpoint.wsdlUrl() + " (" + endpoint.source() + ")");
    System.out.println("Attempting to connect to " + client.serverID + " server via SOAP...");

    if (client.connect(args)) {
      System.out.println("Connection successful! Loading menu...\n");
      client.displayMenu();
    } else {
      System.err.println("Connection failed.");
    }
  }

  // ==================== MENU ====================

  /**
   * Connect to the office SOAP endpoint and create the JAX-WS port.
   *
   * @param args optional override args (e.g. --wsdl http://localhost:8081/mtl?wsdl)
   * @return true if connection successful
   */
  public boolean connect(String[] args) {
    WsEndpoint endpoint = ClientRuntimeSupport.resolveWsEndpoint(args, serverID);
    try {
      server = ClientRuntimeSupport.connectToOffice(endpoint);
      log(
          "Connected to "
              + serverID
              + " server via SOAP @ "
              + endpoint.wsdlUrl()
              + " ("
              + endpoint.source()
              + ")");
      return true;
    } catch (Exception e) {
      System.err.println("SOAP Connection Error: " + ClientRuntimeSupport.formatConnectionError(e));
      System.err.println(ClientRuntimeSupport.buildConnectionHelp(endpoint, serverID));
      return false;
    }
  }

  // ==================== OPERATIONS ====================

  public void displayMenu() {
    Scanner scanner = new Scanner(System.in);

    while (true) {
      System.out.println("\n===== Customer Menu (" + customerID + ") =====");
      System.out.println("1. Reserve Vehicle");
      System.out.println("2. Update Reservation");
      System.out.println("3. Cancel Reservation");
      System.out.println("4. Find Vehicle");
      System.out.println("5. List My Reservations");
      System.out.println("6. Exit");
      System.out.print("Choose option: ");
      int choice = ClientRuntimeSupport.readMenuChoice(scanner, 1, 6);

      try {
        switch (choice) {
          case 1:
            reserveVehicle(scanner);
            break;
          case 2:
            updateReservation(scanner);
            break;
          case 3:
            cancelReservation(scanner);
            break;
          case 4:
            findVehicle(scanner);
            break;
          case 5:
            listMyReservations();
            break;
          case 6:
            System.out.println("Goodbye!");
            return;
          default:
            System.out.println("Invalid option");
        }
      } catch (Exception e) {
        System.err.println("Error: " + e.getMessage());
      }
    }
  }

  private void reserveVehicle(Scanner scanner) {
    System.out.print("Enter Vehicle ID (e.g., MTL1012): ");
    String vehicleID = scanner.nextLine().toUpperCase();

    System.out.print("Enter Start Date (ddmmyyyy): ");
    String startDate = scanner.nextLine();

    System.out.print("Enter End Date (ddmmyyyy): ");
    String endDate = scanner.nextLine();

    String result = server.reserveVehicle(customerID, vehicleID, startDate, endDate);
    System.out.println("\nResult: " + result);
    log("reserveVehicle(" + vehicleID + ", " + startDate + ", " + endDate + ") - " + result);

    // Handle waitlist prompt
    if (result.startsWith("WAITLIST:")) {
      System.out.print("Add to waitlist? (yes/no): ");
      String answer = scanner.nextLine().toLowerCase();
      if (answer.equals("yes") || answer.equals("y")) {
        String waitlistResult = server.addToWaitList(customerID, vehicleID, startDate, endDate);
        System.out.println(waitlistResult);
        log("addToWaitList(" + vehicleID + ") - " + waitlistResult);
      }
    }
  }

  private void updateReservation(Scanner scanner) {
    System.out.print("Enter Vehicle ID of reservation to update: ");
    String vehicleID = scanner.nextLine().toUpperCase();

    System.out.print("Enter New Start Date (ddmmyyyy): ");
    String newStartDate = scanner.nextLine();

    System.out.print("Enter New End Date (ddmmyyyy): ");
    String newEndDate = scanner.nextLine();

    String result = server.updateReservation(customerID, vehicleID, newStartDate, newEndDate);
    System.out.println("\nResult: " + result);
    log(
        "updateReservation("
            + vehicleID
            + ", "
            + newStartDate
            + ", "
            + newEndDate
            + ") - "
            + result);
  }

  private void cancelReservation(Scanner scanner) {
    System.out.print("Enter Vehicle ID of reservation to cancel: ");
    String vehicleID = scanner.nextLine().toUpperCase();

    String result = server.cancelReservation(customerID, vehicleID);
    System.out.println("\nResult: " + result);
    log("cancelReservation(" + vehicleID + ") - " + result);
  }

  private void findVehicle(Scanner scanner) {
    System.out.print("Enter Vehicle Type to search (Sedan/SUV/Truck): ");
    String vehicleType = scanner.nextLine();

    String result = server.findVehicle(customerID, vehicleType);
    System.out.println("\n=== Search Results ===");
    System.out.println(result);
    log("findVehicle(" + vehicleType + ") - Found");
  }

  // ==================== LOGGING ====================

  private void listMyReservations() {
    String result = server.listCustomerReservations(customerID);
    System.out.println("\n=== My Reservations ===");
    System.out.println(result);
    log("listCustomerReservations() - Listed");
  }

  // ==================== MAIN ====================

  private void log(String message) {
    ClientRuntimeSupport.appendClientLog(customerID, message);
  }
}
