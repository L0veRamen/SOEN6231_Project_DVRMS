package client;

import client.generated.VehicleReservationService;
import client.generated.VehicleReservationWS;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

final class ClientRuntimeSupport {

  private static final String DEFAULT_HOST = "localhost";

  private ClientRuntimeSupport() {}

  static WsEndpoint resolveWsEndpoint(String[] args, String officeID) {
    String explicitWsdl = null;
    String host = DEFAULT_HOST;

    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if ("--wsdl".equals(arg) && i + 1 < args.length) {
          explicitWsdl = args[++i];
        } else if ("--host".equals(arg) && i + 1 < args.length) {
          host = args[++i];
        }
      }
    }

    if (explicitWsdl != null) {
      return new WsEndpoint(explicitWsdl, WsEndpoint.SOURCE_ARGS);
    }

    String path;
    int port;
    switch (officeID) {
      case "MTL":
        path = "mtl";
        port = 8081;
        break;
      case "WPG":
        path = "wpg";
        port = 8082;
        break;
      case "BNF":
        path = "bnf";
        port = 8083;
        break;
      default:
        path = officeID == null ? "unknown" : officeID.toLowerCase();
        port = 8081;
        break;
    }
    return new WsEndpoint(
        "http://" + host + ":" + port + "/" + path + "?wsdl", WsEndpoint.SOURCE_DEFAULT);
  }

  static VehicleReservationWS connectToOffice(WsEndpoint endpoint) throws Exception {
    URL wsdlUrl = new URL(endpoint.wsdlUrl());
    VehicleReservationService service = new VehicleReservationService(wsdlUrl);
    return service.getVehicleReservationWSPort();
  }

  static int readMenuChoice(Scanner scanner, int min, int max) {
    while (true) {
      String input = scanner.nextLine().trim();
      try {
        int value = Integer.parseInt(input);
        if (value >= min && value <= max) {
          return value;
        }
      } catch (NumberFormatException ignored) {
        // Keep prompting.
      }
      System.out.println("Invalid option");
      System.out.print("Choose option: ");
    }
  }

  static void appendClientLog(String actorID, String message) {
    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    String logEntry = "[" + timestamp + "] [" + actorID + "] " + message;

    File logDir = new File("logs");
    if (!logDir.exists()) {
      logDir.mkdirs();
    }

    try (FileWriter fw = new FileWriter(new File(logDir, actorID + "_client.log"), true);
        PrintWriter pw = new PrintWriter(fw)) {
      pw.println(logEntry);
    } catch (IOException e) {
      System.err.println("Error writing to log: " + e.getMessage());
    }
  }

  static String formatConnectionError(Exception e) {
    String type = e.getClass().getSimpleName();
    String message = e.getMessage();
    if (message == null || message.trim().isEmpty()) {
      return type;
    }
    return type + ": " + message;
  }

  static String buildConnectionHelp(WsEndpoint endpoint, String officeID) {
      String sb = "Failed to connect to " + officeID + " server. Please ensure:\n" +
              "1. Start SOAP servers:\n" +
              "   ./start-server.sh\n" +
              "2. Generate/compile client stubs:\n" +
              "   ./build-client.sh\n" +
              "3. Use a reachable WSDL endpoint, e.g.:\n" +
              "   " + endpoint.wsdlUrl() + "\n" +
              "4. Run clients:\n" +
              "   java -cp bin client.CustomerClient\n" +
              "   java -cp bin client.ManagerClient";
    return sb;
  }
}
