package server;

import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Replica Manager — one per replica.
 * Usage: java server.ReplicaManager <replicaId>
 */
public class ReplicaManager {

    private final int replicaId;
    private final int rmPort;
    private final int replicaPort;
    private Process replicaProcess;
    private final ReliableUDPSender sender = new ReliableUDPSender();

    private static final int HEARTBEAT_INTERVAL_MS = 3000;
    private static final int HEARTBEAT_TIMEOUT_MS = 2000;

    public ReplicaManager(int replicaId) {
        this.replicaId = replicaId;
        this.rmPort = PortConfig.ALL_RMS[replicaId - 1];
        this.replicaPort = PortConfig.ALL_REPLICAS[replicaId - 1];
    }

    public void start() {
        launchReplica();
        new Thread(this::heartbeatLoop, "RM" + replicaId + "-Heartbeat").start();
        listenForMessages();
    }

    private void launchReplica() {
        try {
            replicaProcess = new ProcessBuilder(
                "java", "-cp", System.getProperty("java.class.path"),
                "server.ReplicaLauncher", String.valueOf(replicaId)
            ).inheritIO().start();
            System.out.println("RM" + replicaId + ": Replica launched on port " + replicaPort);
        } catch (Exception e) {
            System.err.println("RM" + replicaId + ": Failed to launch replica: " + e.getMessage());
        }
    }

    private void killReplica() {
        if (replicaProcess != null && replicaProcess.isAlive()) {
            replicaProcess.destroyForcibly();
            System.out.println("RM" + replicaId + ": Replica killed");
        }
    }

    private void heartbeatLoop() {
        while (true) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
                boolean alive = sendHeartbeat();
                if (!alive) {
                    System.out.println("RM" + replicaId + ": Heartbeat FAILED");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean sendHeartbeat() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(HEARTBEAT_TIMEOUT_MS);
            String msg = "HEARTBEAT_CHECK:" + replicaId;
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length,
                InetAddress.getByName("localhost"), replicaPort));

            byte[] buf = new byte[8192];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            socket.receive(reply);
            String response = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);
            return response.startsWith("HEARTBEAT_ACK");
        } catch (Exception e) {
            return false;
        }
    }

    private void listenForMessages() {
        try (DatagramSocket socket = new DatagramSocket(rmPort)) {
            byte[] buf = new byte[8192];
            System.out.println("RM" + replicaId + " listening on port " + rmPort);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                UDPMessage msg = UDPMessage.parse(raw);

                switch (msg.getType()) {
                    case REPLACE_REQUEST:
                        handleByzantineReplace(msg, socket);
                        break;
                    case CRASH_SUSPECT:
                        handleCrashSuspect(msg, socket);
                        break;
                    case VOTE_BYZANTINE:
                    case VOTE_CRASH:
                        handleVote(msg, socket);
                        break;
                    case STATE_REQUEST:
                        handleStateRequest(msg, socket, packet);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("RM" + replicaId + " error: " + e.getMessage());
        }
    }

    private void handleByzantineReplace(UDPMessage msg, DatagramSocket socket) {
        String faultyReplicaId = msg.getField(0);
        System.out.println("RM" + replicaId + ": Byzantine replace requested for " + faultyReplicaId);

        for (int port : PortConfig.ALL_RMS) {
            try {
                sender.send("VOTE_BYZANTINE:" + faultyReplicaId,
                    InetAddress.getByName("localhost"), port, socket);
            } catch (Exception e) { /* log */ }
        }
        // TODO: Collect votes and perform replacement if majority agrees
    }

    private void handleCrashSuspect(UDPMessage msg, DatagramSocket socket) {
        String suspectedId = msg.getField(2);
        boolean alive = sendHeartbeat();
        String vote = "VOTE_CRASH:" + suspectedId + ":"
            + (alive ? "ALIVE" : "CRASH_CONFIRMED");
        for (int port : PortConfig.ALL_RMS) {
            try {
                sender.send(vote,
                    InetAddress.getByName("localhost"), port, socket);
            } catch (Exception e) { /* log */ }
        }
    }

    private void handleVote(UDPMessage msg, DatagramSocket socket) {
        // TODO: Collect and tally votes, perform replacement if majority agrees
    }

    private void handleStateRequest(UDPMessage msg, DatagramSocket socket, DatagramPacket from) {
        // TODO: Request snapshot from co-located replica, send STATE_TRANSFER back
    }

    private void replaceReplica() {
        killReplica();
        launchReplica();
        // TODO: Request state from lowest-ID healthy replica
        // TODO: Send REPLICA_READY to Sequencer and FE
    }

    public static void main(String[] args) {
        int id = Integer.parseInt(args[0]);
        new ReplicaManager(id).start();
    }
}
