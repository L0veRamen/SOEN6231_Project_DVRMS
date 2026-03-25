package server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP listener thread for inter-server communication.
 *
 * <p>Receives UDP requests from other office servers and delegates processing to the
 * VehicleReservationWS.handleUDPRequest() method.
 *
 * <p>Unchanged from Assignment 2 except class reference update for A3 JAX-WS migration.
 */
public class UDPServer implements Runnable {

  private static final int BUFFER_SIZE = 8192;
  private static final int SOCKET_TIMEOUT_MS = 1000;

  private final VehicleReservationWS servant;
  private final int port;
  private volatile boolean running = true;
  private volatile DatagramSocket socket;
  private final Set<String> deliveredMsgIds = ConcurrentHashMap.newKeySet();

  public UDPServer(VehicleReservationWS servant, int port) {
    this.servant = servant;
    this.port = port;
  }

  @Override
  public void run() {
    try (DatagramSocket serverSocket = new DatagramSocket(port)) {
      socket = serverSocket;
      serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
      System.out.println("[" + servant.getServerID() + "] UDP Server listening on port " + port);

      byte[] receiveData = new byte[BUFFER_SIZE];

      while (running) {
        try {
          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          serverSocket.receive(receivePacket);

          String request =
              new String(
                  receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
          System.out.println("[" + servant.getServerID() + "] UDP Received: " + request);

          // P2: Parse message using UDPMessage
          UDPMessage msg = UDPMessage.parse(request);

          // P2: ACK messages are handled silently
          if (msg.getType() == UDPMessage.Type.ACK) {
            continue;
          }

          // P2: Extract msgId for dedup (first field for most messages)
          String msgId = msg.getField(0);

          // P2: Dedup — if already delivered, ACK but don't re-execute
          if (deliveredMsgIds.contains(msgId)) {
            String ack = "ACK:" + msgId;
            byte[] ackData = ack.getBytes(StandardCharsets.UTF_8);
            serverSocket.send(new DatagramPacket(ackData, ackData.length,
                receivePacket.getAddress(), receivePacket.getPort()));
            continue;
          }

          String response = servant.handleUDPRequest(request);
          deliveredMsgIds.add(msgId);

          // P2: Send ACK back to sender
          String ack = "ACK:" + msgId;
          byte[] ackData = ack.getBytes(StandardCharsets.UTF_8);
          serverSocket.send(new DatagramPacket(ackData, ackData.length,
              receivePacket.getAddress(), receivePacket.getPort()));

          byte[] sendData = response.getBytes(StandardCharsets.UTF_8);
          DatagramPacket sendPacket =
              new DatagramPacket(
                  sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());
          serverSocket.send(sendPacket);

          System.out.println("[" + servant.getServerID() + "] UDP Response: " + response);
        } catch (SocketTimeoutException ignored) {
          // Periodic wake-up to check running flag.
        } catch (Exception e) {
          if (running) {
            System.err.println(
                "[" + servant.getServerID() + "] UDP Request Handling Error: " + e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      if (running) {
        System.err.println("[" + servant.getServerID() + "] UDP Server Error: " + e.getMessage());
      }
    } finally {
      socket = null;
    }
  }

  public void stop() {
    running = false;
    DatagramSocket current = socket;
    if (current != null && !current.isClosed()) {
      current.close();
    }
  }
}
