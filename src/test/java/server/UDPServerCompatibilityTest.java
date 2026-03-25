package server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UDPServerCompatibilityTest {

  @Test
  void legacyPayload_isHandledWithoutProtocolAckFraming() throws Exception {
    String previousDisableUdp = System.getProperty("dvrms.disable.udp");
    System.setProperty("dvrms.disable.udp", "true");

    UDPServer udpServer = null;
    Thread serverThread = null;
    try (DatagramSocket clientSocket = new DatagramSocket()) {
      int serverPort = findFreePort();
      VehicleReservationWS ws = new VehicleReservationWS("MTL", serverPort + 1, "1");
      udpServer = new UDPServer(ws, serverPort);
      serverThread = new Thread(udpServer, "udp-compat-test");
      serverThread.start();
      Thread.sleep(100);

      clientSocket.setSoTimeout(1500);
      String request = "LISTAVAILABLE:MTLM1111";
      byte[] reqData = request.getBytes(StandardCharsets.UTF_8);
      clientSocket.send(
          new DatagramPacket(reqData, reqData.length, InetAddress.getByName("localhost"), serverPort));

      DatagramPacket firstReplyPacket = new DatagramPacket(new byte[8192], 8192);
      clientSocket.receive(firstReplyPacket);
      String firstReply =
          new String(
              firstReplyPacket.getData(),
              0,
              firstReplyPacket.getLength(),
              StandardCharsets.UTF_8);

      assertFalse(firstReply.startsWith("ACK:"), "Legacy A3 payload should not get protocol ACK framing");
      assertTrue(firstReply.contains("MTL"), "Expected business response from LISTAVAILABLE");

      DatagramPacket secondReplyPacket = new DatagramPacket(new byte[8192], 8192);
      assertThrows(SocketTimeoutException.class, () -> clientSocket.receive(secondReplyPacket));
    } finally {
      if (udpServer != null) {
        udpServer.stop();
      }
      if (serverThread != null) {
        serverThread.join(1500);
      }
      if (previousDisableUdp == null) {
        System.clearProperty("dvrms.disable.udp");
      } else {
        System.setProperty("dvrms.disable.udp", previousDisableUdp);
      }
    }
  }

  private int findFreePort() throws Exception {
    try (DatagramSocket socket = new DatagramSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
