package server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.xml.ws.Endpoint;

/** Publishes all DVRMS JAX-WS endpoints and keeps the JVM alive. */
public final class ServerPublisher {

  private static final CountDownLatch KEEP_ALIVE = new CountDownLatch(1);

  private ServerPublisher() {}

  public static void main(String[] args) {
    List<Endpoint> endpoints = new ArrayList<Endpoint>();
    try {
      endpoints.add(publishOffice("MTL", 8081, 5001));
      endpoints.add(publishOffice("WPG", 8082, 5002));
      endpoints.add(publishOffice("BNF", 8083, 5003));

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  new Runnable() {
                    @Override
                    public void run() {
                      for (Endpoint endpoint : endpoints) {
                        if (endpoint != null) {
                          endpoint.stop();
                        }
                      }
                      System.out.println("SOAP endpoints stopped.");
                    }
                  }));

      System.out.println("\n=== All JAX-WS servers are running ===");
      System.out.println("MTL: http://localhost:8081/mtl?wsdl  + UDP(5001)");
      System.out.println("WPG: http://localhost:8082/wpg?wsdl  + UDP(5002)");
      System.out.println("BNF: http://localhost:8083/bnf?wsdl  + UDP(5003)");
      System.out.println("Press Ctrl+C to stop.\n");

      KEEP_ALIVE.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Server publisher interrupted.");
    } catch (Exception e) {
      System.err.println("Server publisher error: " + e.getMessage());
      e.printStackTrace();
      for (Endpoint endpoint : endpoints) {
        if (endpoint != null) {
          endpoint.stop();
        }
      }
    }
  }

  private static Endpoint publishOffice(String serverID, int httpPort, int udpPort) {
    VehicleReservationWS service = new VehicleReservationWS(serverID, udpPort);
    String address = "http://localhost:" + httpPort + "/" + serverID.toLowerCase();
    Endpoint endpoint = Endpoint.publish(address, service);
    System.out.println("Published " + serverID + " endpoint at " + address);
    return endpoint;
  }
}
