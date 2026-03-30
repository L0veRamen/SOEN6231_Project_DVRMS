package server;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Starts one replica (3 offices: MTL, WPG, BNF) with UDP listener.
 * No SOAP endpoint — all requests come through the Sequencer.
 *
 * Usage: java server.ReplicaLauncher <replicaId>
 *        where replicaId = 1, 2, 3, or 4
 */
public class ReplicaLauncher {

    private static final String DEFAULT_OFFICE = "MTL";
    private static final ReliableUDPSender RELIABLE_SENDER = new ReliableUDPSender();

    private static final class PendingExecute {
        final int seqNum;
        final String reqID;
        final String feHost;
        final int fePort;
        final String operation;

        PendingExecute(int seqNum, String reqID, String feHost, int fePort, String operation) {
            this.seqNum = seqNum;
            this.reqID = reqID;
            this.feHost = feHost;
            this.fePort = fePort;
            this.operation = operation;
        }
    }

    static final class ExecutionGate {
        private final int replicaId;
        private final Map<String, VehicleReservationWS> offices;
        private final VehicleReservationWS defaultOffice;
        private final TreeMap<Integer, PendingExecute> holdbackQueue = new TreeMap<>();
        private int nextExpectedSeq = 0;

        ExecutionGate(
            int replicaId, Map<String, VehicleReservationWS> offices, VehicleReservationWS defaultOffice) {
            this.replicaId = replicaId;
            this.offices = offices;
            this.defaultOffice = defaultOffice;
            syncOfficeSequences();
        }

        synchronized List<String> handleExecute(
            int seqNum, String reqID, String feHost, int fePort, String operation) {
            if (seqNum < nextExpectedSeq) {
                return Collections.singletonList("ACK:" + seqNum);
            }
            if (seqNum > nextExpectedSeq) {
                holdbackQueue.putIfAbsent(seqNum, new PendingExecute(seqNum, reqID, feHost, fePort, operation));
                List<String> replies = new ArrayList<String>(2);
                replies.add("NACK:" + replicaId + ":" + nextExpectedSeq + ":" + (seqNum - 1));
                replies.add("ACK:" + seqNum);
                return replies;
            }

            executeCommitted(new PendingExecute(seqNum, reqID, feHost, fePort, operation));
            drainBufferedContiguous();
            return Collections.singletonList("ACK:" + seqNum);
        }

        synchronized int getNextExpectedSeq() {
            return nextExpectedSeq;
        }

        synchronized void resetNextExpectedSeq(int nextExpectedSeq) {
            this.nextExpectedSeq = nextExpectedSeq;
            holdbackQueue.clear();
            syncOfficeSequences();
        }

        private void executeCommitted(PendingExecute pending) {
            VehicleReservationWS target = resolveTargetOffice(pending.operation);
            target.executeCommittedSequence(
                pending.seqNum, pending.reqID, pending.feHost, pending.fePort, pending.operation);
            nextExpectedSeq++;
            syncOfficeSequences();
        }

        private void drainBufferedContiguous() {
            PendingExecute next = holdbackQueue.remove(nextExpectedSeq);
            while (next != null) {
                executeCommitted(next);
                next = holdbackQueue.remove(nextExpectedSeq);
            }
        }

        private VehicleReservationWS resolveTargetOffice(String operation) {
            VehicleReservationWS target = offices.get(extractTargetOffice(operation));
            return target != null ? target : defaultOffice;
        }

        private void syncOfficeSequences() {
            for (VehicleReservationWS office : offices.values()) {
                office.syncNextExpectedSeq(nextExpectedSeq);
            }
        }
    }

    public static void main(String[] args) {
        int replicaId = Integer.parseInt(args[0]);
        int replicaPort = PortConfig.ALL_REPLICAS[replicaId - 1];
        String replicaIdToken = String.valueOf(replicaId);

        // Create the 3 office instances (same as A3, but no SOAP publish)
        VehicleReservationWS mtl =
            new VehicleReservationWS("MTL", PortConfig.officePort(replicaId, "MTL"), replicaIdToken);
        VehicleReservationWS wpg =
            new VehicleReservationWS("WPG", PortConfig.officePort(replicaId, "WPG"), replicaIdToken);
        VehicleReservationWS bnf =
            new VehicleReservationWS("BNF", PortConfig.officePort(replicaId, "BNF"), replicaIdToken);

        Map<String, VehicleReservationWS> offices = new HashMap<>();
        offices.put("MTL", mtl);
        offices.put("WPG", wpg);
        offices.put("BNF", bnf);
        ExecutionGate executionGate = new ExecutionGate(replicaId, offices, mtl);

        System.out.println("Replica " + replicaId + " started on UDP port " + replicaPort);

        try (DatagramSocket socket = new DatagramSocket(replicaPort)) {
            byte[] buf = new byte[8192];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                try {
                    UDPMessage msg = UDPMessage.parse(raw);
                    switch (msg.getType()) {
                        case EXECUTE: {
                            if (msg.fieldCount() < 5) {
                                System.err.println("Replica " + replicaId + ": malformed EXECUTE, ignoring");
                                break;
                            }
                            int seqNum = Integer.parseInt(msg.getField(0));
                            String reqID = msg.getField(1);
                            String feHost = msg.getField(2);
                            int fePort = Integer.parseInt(msg.getField(3));
                            // Remaining fields = "op:params"
                            StringBuilder op = new StringBuilder(msg.getField(4));
                            for (int i = 5; i < msg.fieldCount(); i++) {
                                op.append(':').append(msg.getField(i));
                            }

                            List<String> replies =
                                executionGate.handleExecute(seqNum, reqID, feHost, fePort, op.toString());
                            for (String reply : replies) {
                                byte[] replyData = reply.getBytes(StandardCharsets.UTF_8);
                                socket.send(
                                    new DatagramPacket(
                                        replyData, replyData.length, packet.getAddress(), packet.getPort()));
                            }
                            break;
                        }
                        case HEARTBEAT_CHECK: {
                            String reply = "HEARTBEAT_ACK:" + replicaId + ":" + executionGate.getNextExpectedSeq();
                            byte[] replyData = reply.getBytes(StandardCharsets.UTF_8);
                            socket.send(new DatagramPacket(replyData, replyData.length,
                                packet.getAddress(), packet.getPort()));
                            break;
                        }
                        case SHUTDOWN: {
                            System.out.println("Replica " + replicaId + ": SHUTDOWN received");
                            return;
                        }
                        case SET_BYZANTINE: {
                            boolean enable = msg.fieldCount() > 0 && "true".equalsIgnoreCase(msg.getField(0));
                            for (VehicleReservationWS office : offices.values()) {
                                office.handleUDPRequest("SET_BYZANTINE:" + enable);
                            }
                            String reply = "ACK:SET_BYZANTINE:" + enable;
                            byte[] replyData = reply.getBytes(StandardCharsets.UTF_8);
                            socket.send(new DatagramPacket(replyData, replyData.length,
                                packet.getAddress(), packet.getPort()));
                            break;
                        }
                        case STATE_REQUEST: {
                            // Collect snapshot from all 3 offices
                            sendPlainAck(socket, packet, "STATE_REQUEST");
                            StringBuilder snapshot = new StringBuilder();
                            snapshot.append(mtl.getStateSnapshot()).append("|");
                            snapshot.append(wpg.getStateSnapshot()).append("|");
                            snapshot.append(bnf.getStateSnapshot());
                            String reply = "STATE_TRANSFER:" + replicaId + ":" + snapshot.toString();
                            sendReliably(reply, packet.getAddress(), packet.getPort());
                            break;
                        }
                        case INIT_STATE: {
                            // Load snapshot into all 3 offices
                            // Format: INIT_STATE:mtlSnapshot|wpgSnapshot|bnfSnapshot
                            String[] snapshots = msg.getField(0).split("\\|");
                            if (snapshots.length < 3) {
                                System.err.println("Replica " + replicaId + ": malformed INIT_STATE, ignoring");
                                break;
                            }
                            mtl.loadStateSnapshot(snapshots[0]);
                            wpg.loadStateSnapshot(snapshots[1]);
                            bnf.loadStateSnapshot(snapshots[2]);
                            int nextSeq = mtl.getNextExpectedSeq();
                            executionGate.resetNextExpectedSeq(nextSeq);
                            int lastSeqNum = nextSeq - 1;
                            String reply = "ACK:INIT_STATE:" + replicaId + ":" + lastSeqNum;
                            sendReliably(reply, packet.getAddress(), packet.getPort());
                            break;
                        }
                        case ACK:
                            break; // silently ignore
                        default:
                            System.out.println("Replica " + replicaId + ": unhandled message type " + msg.getType());
                            break;
                    }
                } catch (Exception packetError) {
                    System.err.println("Replica " + replicaId + ": ignoring malformed packet: " + packetError.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Replica " + replicaId + " error: " + e.getMessage());
        }
    }

    private static void sendPlainAck(DatagramSocket socket, DatagramPacket packet, String token) {
        try {
            String ack = "ACK:" + token;
            byte[] ackData = ack.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(
                ackData, ackData.length, packet.getAddress(), packet.getPort()));
        } catch (Exception e) {
            System.err.println("Replica: ACK send failed: " + e.getMessage());
        }
    }

    private static void sendReliably(String message, InetAddress address, int port) {
        try (DatagramSocket sendSocket = new DatagramSocket()) {
            boolean acked = RELIABLE_SENDER.send(message, address, port, sendSocket);
            if (!acked) {
                System.err.println("Replica: reliable send not ACKed for message " + message);
            }
        } catch (Exception e) {
            System.err.println("Replica: reliable send failed: " + e.getMessage());
        }
    }

    static String extractTargetOffice(String operation) {
        if (operation == null || operation.trim().isEmpty()) {
            return DEFAULT_OFFICE;
        }
        String[] parts = operation.split(":", -1);
        if (parts.length == 0) {
            return DEFAULT_OFFICE;
        }
        String op = parts[0];
        switch (op) {
            case "FIND":
                return DEFAULT_OFFICE;
            case "RESERVE_EXECUTE":
            case "CANCEL_EXECUTE":
            case "ATOMIC_UPDATE_EXECUTE":
                // Execute-path customer operations must run on the customer's home office
                // so cross-office budget/quota logic stays equivalent to A3 behavior.
                return officeFromField(parts, 1);
            case "ADDVEHICLE":
            case "REMOVEVEHICLE":
            case "LISTAVAILABLE":
                return officeFromField(parts, 1);
            case "LISTRES":
                return officeFromField(parts, 1);
            case "RESERVE":
            case "CANCEL":
            case "WAITLIST":
            case "ATOMIC_UPDATE":
                return officeFromField(parts, 2);
            default:
                return DEFAULT_OFFICE;
        }
    }

    private static String officeFromField(String[] parts, int fieldIndex) {
        if (parts.length <= fieldIndex) {
            return DEFAULT_OFFICE;
        }
        String id = parts[fieldIndex];
        if (id == null || id.length() < 3) {
            return DEFAULT_OFFICE;
        }
        String office;
        try {
            office = ServerIdRules.extractOfficeID(id);
        } catch (Exception ignored) {
            return DEFAULT_OFFICE;
        }
        if (!"MTL".equals(office) && !"WPG".equals(office) && !"BNF".equals(office)) {
            return DEFAULT_OFFICE;
        }
        return office;
    }
}
