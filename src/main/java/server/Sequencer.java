package server;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Sequencer — single instance, assumed failure-free.
 * Assigns total order to all requests and reliably multicasts to replicas.
 *
 * Usage: java server.Sequencer
 */
public class Sequencer {

    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, String> historyBuffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap.KeySetView<Integer, Boolean>> ackTracker =
        new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<InetSocketAddress> replicaAddresses = new CopyOnWriteArrayList<>();
    private final ReliableUDPSender sender = new ReliableUDPSender();

    public Sequencer() {
        for (int port : PortConfig.ALL_REPLICAS) {
            try {
                replicaAddresses.add(new InetSocketAddress(InetAddress.getByName("localhost"), port));
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    public void start() {
        System.out.println("Sequencer listening on port " + PortConfig.SEQUENCER);

        try (DatagramSocket socket = new DatagramSocket(PortConfig.SEQUENCER)) {
            byte[] buf = new byte[8192];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                UDPMessage msg = UDPMessage.parse(raw);

                switch (msg.getType()) {
                    case REQUEST:
                        // Send ACK to FE
                        String ack = "ACK:" + msg.getField(0);
                        byte[] ackData = ack.getBytes(StandardCharsets.UTF_8);
                        socket.send(new DatagramPacket(ackData, ackData.length,
                            packet.getAddress(), packet.getPort()));
                        handleRequest(msg, socket);
                        break;
                    case ACK:
                        handleAck(msg, packet);
                        break;
                    case NACK:
                        handleNack(msg, socket, packet);
                        break;
                    case REPLICA_READY:
                        handleReplicaReady(msg, socket);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Sequencer error: " + e.getMessage());
        }
    }

    private void handleRequest(UDPMessage msg, DatagramSocket socket) {
        int seqNum = sequenceCounter.getAndIncrement();

        StringBuilder execute = new StringBuilder("EXECUTE:" + seqNum);
        for (int i = 0; i < msg.fieldCount(); i++) {
            execute.append(':').append(msg.getField(i));
        }
        String executeMsg = execute.toString();

        historyBuffer.put(seqNum, executeMsg);
        ackTracker.put(seqNum, ConcurrentHashMap.newKeySet());

        multicast(executeMsg);

        System.out.println("Sequencer: assigned seq#" + seqNum + " to " + msg.getField(0));
    }

    private void multicast(String message) {
        for (InetSocketAddress addr : replicaAddresses) {
            new Thread(() -> {
                try (DatagramSocket sendSocket = new DatagramSocket()) {
                    boolean acked = sender.send(message, addr.getAddress(), addr.getPort(), sendSocket);
                    if (!acked) {
                        System.err.println("Sequencer: replica at " + addr.getPort() + " unresponsive");
                    }
                } catch (Exception e) {
                    System.err.println("Sequencer: multicast send error: " + e.getMessage());
                }
            }).start();
        }
    }

    private void handleAck(UDPMessage msg, DatagramPacket from) {
        try {
            int seqNum = Integer.parseInt(msg.getField(0));
            ConcurrentHashMap.KeySetView<Integer, Boolean> acks = ackTracker.get(seqNum);
            if (acks != null) {
                acks.add(from.getPort());
                if (acks.size() >= PortConfig.ALL_REPLICAS.length) {
                    ackTracker.remove(seqNum);
                }
            }
        } catch (NumberFormatException e) {
            // Non-numeric ACK field — ignore
        }
    }

    private void handleNack(UDPMessage msg, DatagramSocket socket, DatagramPacket from) {
        String replicaID = msg.getField(0);
        int seqStart = Integer.parseInt(msg.getField(1));
        int seqEnd = Integer.parseInt(msg.getField(2));

        System.out.println("Sequencer: NACK from " + replicaID + " for seq " + seqStart + "-" + seqEnd);

        InetAddress requesterAddr = from.getAddress();
        int requesterPort = from.getPort();
        for (int seq = seqStart; seq <= seqEnd; seq++) {
            String historicMsg = historyBuffer.get(seq);
            if (historicMsg != null) {
                final String msgToSend = historicMsg;
                new Thread(() -> {
                    try (DatagramSocket sendSocket = new DatagramSocket()) {
                        sender.send(msgToSend, requesterAddr, requesterPort, sendSocket);
                    } catch (Exception e) {
                        System.err.println("Sequencer: replay send failed: " + e.getMessage());
                    }
                }).start();
            }
        }
    }

    private void handleReplicaReady(UDPMessage msg, DatagramSocket socket) {
        String replicaID = msg.getField(0);
        String host = msg.getField(1);
        int replicaPort = Integer.parseInt(msg.getField(2));
        int lastSeq = Integer.parseInt(msg.getField(3));

        System.out.println("Sequencer: " + replicaID + " ready, replaying from seq " + (lastSeq + 1));

        int current = sequenceCounter.get();
        try {
            InetAddress addr = InetAddress.getByName(host);
            for (int seq = lastSeq + 1; seq < current; seq++) {
                String historicMsg = historyBuffer.get(seq);
                if (historicMsg != null) {
                    final String msgToSend = historicMsg;
                    new Thread(() -> {
                        try (DatagramSocket sendSocket = new DatagramSocket()) {
                            sender.send(msgToSend, addr, replicaPort, sendSocket);
                        } catch (Exception e) {
                            System.err.println("Sequencer: replay send failed: " + e.getMessage());
                        }
                    }).start();
                }
            }
        } catch (Exception e) {
            System.err.println("Sequencer: replay to " + replicaID + " failed: " + e.getMessage());
        }

        try {
            InetSocketAddress newAddr = new InetSocketAddress(
                InetAddress.getByName(host), replicaPort);
            replicaAddresses.removeIf(a -> a.getPort() == replicaPort);
            replicaAddresses.add(newAddr);
        } catch (Exception e) { /* log */ }
    }

    public static void main(String[] args) {
        new Sequencer().start();
    }
}
