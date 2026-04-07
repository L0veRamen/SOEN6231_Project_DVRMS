package server;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Sequencer — single instance, assumed failure-free, in the FT-DVRMS system.
 *
 * <p>The Sequencer is responsible for:
 * <ol>
 *   <li>Assigning a monotonically increasing sequence number to every client request.</li>
 *   <li>Reliably multicasting {@code EXECUTE} to all known replicas (one thread per replica).</li>
 *   <li>Tracking per-message ACKs from replicas; sending {@code CRASH_SUSPECT} to all RMs
 *       when a replica does not ACK within the reliable-UDP timeout.</li>
 *   <li>Replaying missed messages from the history buffer when a replica sends a {@code NACK}.</li>
 *   <li>Re-registering a replacement replica and replaying all missed messages upon
 *       receiving a {@code REPLICA_READY} from an RM.</li>
 * </ol>
 *
 * <p><b>Workflow overview:</b>
 * <pre>
 *   FE sends REQUEST
 *       │  ACK immediately returned to FE
 *       ▼
 *   handleRequest()
 *       │  assigns seq# N, stores EXECUTE:N:... in historyBuffer
 *       ▼
 *   multicast(EXECUTE:N:...)  — one thread per replica
 *       │
 *       ├─ replica ACKs    ──→ handleAck() tracks received ACKs
 *       │
 *       └─ no ACK (timeout)──→ notifyRmsCrashSuspectFor()
 *                               CRASH_SUSPECT:reqID:seqNum:replicaId ──→ all RMs
 *
 *   Replica sends NACK:replicaId:seqStart:seqEnd
 *       │
 *       ▼
 *   handleNack() — replays historyBuffer[seqStart..seqEnd] to that replica
 *
 *   RM sends REPLICA_READY:replicaId:host:port:lastSeq
 *       │
 *       ▼
 *   handleReplicaReady()
 *       │  replays historyBuffer[lastSeq+1..current] to new replica
 *       └─ re-registers replica address; ACKs RM
 * </pre>
 *
 * <p>Usage: {@code java server.Sequencer}
 *
 * @see PortConfig for Sequencer and replica port assignments
 * @see UDPMessage for message format definitions
 * @see ReliableUDPSender for ACK-based reliable UDP with exponential backoff
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

    /**
     * Starts the Sequencer: opens the UDP socket on {@link PortConfig#SEQUENCER} and enters
     * the main message loop, dispatching {@code REQUEST}, {@code ACK}, {@code NACK}, and
     * {@code REPLICA_READY} messages to their respective handlers.
     */
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
                        sendProtocolAck(socket, packet, "ACK");
                        handleAck(msg, packet);
                        break;
                    case NACK:
                        sendProtocolAck(socket, packet, "NACK");
                        handleNack(msg, socket, packet);
                        break;
                    case REPLICA_READY:
                        handleReplicaReady(msg, socket, packet);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Sequencer error: " + e.getMessage());
        }
    }

    /** Sends {@code ACK:<token>} back to the source of a UDP packet. */
    private void sendProtocolAck(DatagramSocket socket, DatagramPacket source, String token) {
        try {
            String ack = "ACK:" + token;
            byte[] ackData = ack.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(
                ackData, ackData.length, source.getAddress(), source.getPort()));
        } catch (Exception e) {
            System.err.println("Sequencer: failed to send protocol ACK: " + e.getMessage());
        }
    }

    /**
     * Handles an incoming {@code REQUEST} from the FE. Assigns the next sequence number,
     * stores the stamped message in the history buffer for future replay, and multicasts
     * it to all replicas.
     *
     * <p>Outbound message format: {@code EXECUTE:<seqNum>:<reqID>:<host>:<fePort>:<operation>}
     *
     * @param msg    the parsed REQUEST message (fields: reqID, host, fePort, operation, ...)
     * @param socket the Sequencer's listener socket (unused after ACK; kept for handler signature)
     */
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

    /**
     * Reliably multicasts {@code message} to all registered replica addresses,
     * spawning one thread per replica. If a replica does not ACK within the
     * {@link ReliableUDPSender} timeout, calls {@link #notifyRmsCrashSuspectFor}.
     *
     * @param message the EXECUTE message to send (format: {@code EXECUTE:<seqNum>:...})
     */
    private void multicast(String message) {
        for (InetSocketAddress addr : replicaAddresses) {
            new Thread(() -> {
                try (DatagramSocket sendSocket = new DatagramSocket()) {
                    boolean acked = sender.send(message, addr.getAddress(), addr.getPort(), sendSocket);
                    if (!acked) {
                        System.err.println("Sequencer: replica at " + addr.getPort() + " unresponsive");
                        notifyRmsCrashSuspectFor(message, addr.getPort());
                    }
                } catch (Exception e) {
                    System.err.println("Sequencer: multicast send error: " + e.getMessage());
                }
            }).start();
        }
    }

    /**
     * Notifies all RMs that a replica has not responded to its EXECUTE message.
     * Parses the reqID and seqNum from the EXECUTE message, maps the unresponsive
     * replica port to a replica ID, and sends
     * {@code CRASH_SUSPECT:<reqID>:<seqNum>:<replicaId>} to every RM.
     *
     * @param executeMessage the EXECUTE message that went unACKed
     * @param replicaPort    the UDP port of the unresponsive replica
     */
    private void notifyRmsCrashSuspectFor(String executeMessage, int replicaPort) {
        try {
            String[] parts = executeMessage.split(":", 5);
            if (parts.length < 3 || !"EXECUTE".equals(parts[0])) {
                return;
            }
            int seqNum = Integer.parseInt(parts[1]);
            String reqID = parts[2];
            int replicaId = replicaIdForPort(replicaPort);
            if (replicaId < 0) {
                return;
            }

            String crashMsg = "CRASH_SUSPECT:" + reqID + ":" + seqNum + ":" + replicaId;
            InetAddress localhost = InetAddress.getByName("localhost");
            try (DatagramSocket rmSocket = new DatagramSocket()) {
                for (int rmPort : PortConfig.ALL_RMS) {
                    sender.send(crashMsg, localhost, rmPort, rmSocket);
                }
            }
        } catch (Exception e) {
            System.err.println("Sequencer: failed to notify RMs about unresponsive replica: " + e.getMessage());
        }
    }

    /**
     * Maps a replica UDP port to its 1-based replica ID.
     *
     * @param replicaPort the replica's UDP port
     * @return the replica ID (1–4), or {@code -1} if the port is not registered
     */
    private int replicaIdForPort(int replicaPort) {
        for (int i = 0; i < PortConfig.ALL_REPLICAS.length; i++) {
            if (PortConfig.ALL_REPLICAS[i] == replicaPort) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Records an ACK from a replica for a given sequence number.
     * Removes the tracking entry once all replicas have acknowledged.
     *
     * <p>Message format: {@code ACK:<seqNum>}
     *
     * @param msg  the parsed ACK message (field 0: seqNum)
     * @param from the source packet identifying the acknowledging replica by port
     */
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

    /**
     * Handles a {@code NACK} from a replica that detected a gap in received sequence numbers.
     * Replays each missing {@code EXECUTE} message from the history buffer back to the
     * requesting replica, spawning one thread per message.
     *
     * <p>Message format: {@code NACK:<replicaId>:<seqStart>:<seqEnd>}
     *
     * @param msg    the parsed NACK message (fields: replicaId, seqStart, seqEnd)
     * @param socket the Sequencer's listener socket
     * @param from   the source packet (used to find the replica's address for replay)
     */
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

    /**
     * Handles a {@code REPLICA_READY} notification from an RM after a replica replacement.
     * Replays all {@code EXECUTE} messages from {@code lastSeq+1} to the current counter
     * to the new replica, re-registers its address in the multicast list, and sends
     * {@code ACK:REPLICA_READY:<replicaId>} back to the RM.
     *
     * <p>Message format: {@code REPLICA_READY:<replicaId>:<host>:<port>:<lastSeqNum>}
     *
     * @param msg    the parsed REPLICA_READY message (fields: replicaId, host, port, lastSeqNum)
     * @param socket the Sequencer's listener socket (used to send the ACK)
     * @param from   the source packet (the RM that sent REPLICA_READY)
     */
    private void handleReplicaReady(UDPMessage msg, DatagramSocket socket, DatagramPacket from) {
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

        try {
            String ack = "ACK:REPLICA_READY:" + replicaID;
            byte[] ackData = ack.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(ackData, ackData.length, from.getAddress(), from.getPort()));
        } catch (Exception e) {
            System.err.println("Sequencer: failed to ACK REPLICA_READY: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Sequencer().start();
    }
}
