package client;

import client.generated.VehicleReservationWS;
import java.util.Scanner;

/**
 * SOAP-based manager client for the DVRMS system.
 *
 * <p>Run with: {@code java client.ManagerClient}
 *
 * <p>Uses wsimport-generated SOAP stubs from the office WSDL endpoint.
 */
public class ManagerClient {

  private final String managerID;
  private VehicleReservationWS server;
  private final String serverID;

  public ManagerClient(String managerID) {
    this.managerID = managerID;
    this.serverID = managerID.substring(0, 3);
  }

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);

    System.out.println("===== DVRMS Manager Client (SOAP) =====");
    System.out.print("Enter Manager ID (e.g., MTLM1111): ");
    String managerID = scanner.nextLine().toUpperCase();

    // Validate format
    if (managerID.length() != 8 || managerID.charAt(3) != 'M') {
      System.err.println("Invalid Manager ID format. Must be like MTLM1111");
      return;
    }

    ManagerClient client = new ManagerClient(managerID);
    WsEndpoint endpoint = ClientRuntimeSupport.resolveWsEndpoint(args, client.serverID);
    System.out.println("Preflight:");
    System.out.println("- Manager ID: " + managerID);
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

  /** Connect to the office SOAP endpoint and create the JAX-WS port. */
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
      System.out.println("\n===== Manager Menu (" + managerID + ") =====");
      System.out.println("1. Add Vehicle");
      System.out.println("2. Remove Vehicle");
      System.out.println("3. List Available Vehicles");
      System.out.println("4. Exit");
      System.out.print("Choose option: ");
      int choice = ClientRuntimeSupport.readMenuChoice(scanner, 1, 4);

      try {
        switch (choice) {
          case 1:
            addVehicle(scanner);
            break;
          case 2:
            removeVehicle(scanner);
            break;
          case 3:
            listVehicles();
            break;
          case 4:
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

  private void addVehicle(Scanner scanner) {
    System.out.print("Enter Vehicle ID (e.g., " + serverID + "1012): ");
    String vehicleID = scanner.nextLine();

    System.out.print("Enter License Plate (e.g., ABC123): ");
    String licensePlate = scanner.nextLine();

    System.out.print("Enter Vehicle Type (Sedan/SUV/Truck): ");
    String vehicleType = scanner.nextLine();
    String normalizedType = normalizeVehicleType(vehicleType);
    if (normalizedType == null) {
      System.out.println("Invalid vehicle type. Allowed: Sedan, SUV, Truck");
      return;
    }

    System.out.print("Enter Reservation Price: ");
    double price = scanner.nextDouble();
    scanner.nextLine();

    String result = server.addVehicle(managerID, licensePlate, normalizedType, vehicleID, price);
    System.out.println("\nResult: " + result);
    log("addVehicle(" + vehicleID + ", " + normalizedType + ", " + price + ") - " + result);
  }

  private void removeVehicle(Scanner scanner) {
    System.out.print("Enter Vehicle ID to remove: ");
    String vehicleID = scanner.nextLine();

    String result = server.removeVehicle(managerID, vehicleID);
    System.out.println("\nResult: " + result);
    log("removeVehicle(" + vehicleID + ") - " + result);
  }

  // ==================== HELPERS ====================

  private void listVehicles() {
    String result = server.listAvailableVehicle(managerID);
    System.out.println("\n=== Available Vehicles ===");
    System.out.println(result);
    log("listAvailableVehicle() - Listed");
  }

  // ==================== LOGGING ====================

  private String normalizeVehicleType(String vehicleType) {
    if (vehicleType == null) return null;
    switch (vehicleType.trim().toUpperCase()) {
      case "SEDAN":
        return "Sedan";
      case "SUV":
        return "SUV";
      case "TRUCK":
        return "Truck";
      default:
        return null;
    }
  }

  // ==================== MAIN ====================

  private void log(String message) {
    ClientRuntimeSupport.appendClientLog(managerID, message);
  }
}
