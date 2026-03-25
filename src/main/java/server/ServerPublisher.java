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
      int mtlSoap = PortConfig.officeSoapPort("MTL");
      int wpgSoap = PortConfig.officeSoapPort("WPG");
      int bnfSoap = PortConfig.officeSoapPort("BNF");
      int mtlUdp = PortConfig.officePort(1, "MTL");
      int wpgUdp = PortConfig.officePort(1, "WPG");
      int bnfUdp = PortConfig.officePort(1, "BNF");

      endpoints.add(publishOffice("MTL", mtlSoap, mtlUdp));
      endpoints.add(publishOffice("WPG", wpgSoap, wpgUdp));
      endpoints.add(publishOffice("BNF", bnfSoap, bnfUdp));

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
      System.out.println("MTL: http://localhost:" + mtlSoap + "/mtl?wsdl  + UDP(" + mtlUdp + ")");
      System.out.println("WPG: http://localhost:" + wpgSoap + "/wpg?wsdl  + UDP(" + wpgUdp + ")");
      System.out.println("BNF: http://localhost:" + bnfSoap + "/bnf?wsdl  + UDP(" + bnfUdp + ")");
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
