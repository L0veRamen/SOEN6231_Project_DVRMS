package unit.Sequencer;

import org.junit.jupiter.api.Test;
import server.Sequencer;
import server.UDPMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Sequencer.
 *
 * <p>Owner: [Student 3]
 *
 * <p>These tests verify the Sequencer's internal logic in isolation:
 * <ul>
 *   <li>Sequence numbers are assigned monotonically (0, 1, 2, …)</li>
 *   <li>Each REQUEST is multicast as EXECUTE to all replica addresses</li>
 *   <li>NACK causes replay of the requested range from historyBuffer</li>
 *   <li>REPLICA_READY triggers catch-up replay and registers the new address</li>
 * </ul>
 *
 * <p>Tip: {@code Sequencer.start()} binds port 9100 — do NOT call start() in unit tests.
 * Instead, test the private handler methods via a test subclass that overrides or exposes them,
 * or use reflection to call {@code handleRequest}, {@code handleNack}, {@code handleReplicaReady}
 * directly.
 *
 * <p>Tip: replace {@code replicaAddresses} (a {@code CopyOnWriteArrayList}) with test sockets
 * using reflection so multicasts land on ports you can listen on.
 */
class SequencerTest {

    @Test
    void requestHandling_assignsMonotonicallyIncreasingSeqNums() throws Exception {
        Sequencer sequencer = new Sequencer();
        Method handleRequest = privateMethod("handleRequest", UDPMessage.class, DatagramSocket.class);

        try (AckingUdpProbe replicaProbe = new AckingUdpProbe();
             DatagramSocket invokeSocket = new DatagramSocket()) {
            setReplicaAddresses(sequencer, replicaProbe.addressList());

            handleRequest.invoke(sequencer,
                UDPMessage.parse("REQUEST:REQ-1:localhost:9000:LISTAVAILABLE:MTLM1111"), invokeSocket);
            handleRequest.invoke(sequencer,
                UDPMessage.parse("REQUEST:REQ-2:localhost:9000:LISTAVAILABLE:MTLM1111"), invokeSocket);
            handleRequest.invoke(sequencer,
                UDPMessage.parse("REQUEST:REQ-3:localhost:9000:LISTAVAILABLE:MTLM1111"), invokeSocket);

            List<String> executeMsgs = replicaProbe.awaitMessages(3, 4000);
            assertEquals(3, executeMsgs.size(), "Expected three EXECUTE multicasts");

            List<Integer> seqs = extractSeqs(executeMsgs);
            seqs.sort(Integer::compareTo);
            assertEquals(0, (int) seqs.get(0), "Expected seq set to include 0");
            assertEquals(1, (int) seqs.get(1), "Expected seq set to include 1");
            assertEquals(2, (int) seqs.get(2), "Expected seq set to include 2");
        }
    }

    @Test
    void nackHandling_replaysHistoryBufferForMissedRange() throws Exception {
        Sequencer sequencer = new Sequencer();
        Method handleNack = privateMethod("handleNack", UDPMessage.class, DatagramSocket.class, DatagramPacket.class);
        historyBuffer(sequencer).put(0, "EXECUTE:0:REQ-0:localhost:9000:OP0");
        historyBuffer(sequencer).put(1, "EXECUTE:1:REQ-1:localhost:9000:OP1");
        historyBuffer(sequencer).put(2, "EXECUTE:2:REQ-2:localhost:9000:OP2");

        try (AckingUdpProbe requesterProbe = new AckingUdpProbe();
             DatagramSocket invokeSocket = new DatagramSocket()) {
            DatagramPacket from = new DatagramPacket(
                new byte[0],
                0,
                InetAddress.getByName("localhost"),
                requesterProbe.port());

            handleNack.invoke(sequencer, UDPMessage.parse("NACK:3:0:1"), invokeSocket, from);

            List<String> replayed = requesterProbe.awaitMessages(2, 4000);
            assertEquals(2, replayed.size(), "Expected two replayed messages for seq 0..1");
            List<Integer> seqs = extractSeqs(replayed);
            assertTrue(seqs.contains(0), "Replay should contain seq 0. Got: " + replayed);
            assertTrue(seqs.contains(1), "Replay should contain seq 1. Got: " + replayed);
        }
    }

    @Test
    void replicaReady_triggersReplayAndUpdatesReplicaList() throws Exception {
        Sequencer sequencer = new Sequencer();
        Method handleReplicaReady = privateMethod("handleReplicaReady",
            UDPMessage.class, DatagramSocket.class, DatagramPacket.class);
        historyBuffer(sequencer).put(0, "EXECUTE:0:REQ-0:localhost:9000:OP0");
        historyBuffer(sequencer).put(1, "EXECUTE:1:REQ-1:localhost:9000:OP1");
        historyBuffer(sequencer).put(2, "EXECUTE:2:REQ-2:localhost:9000:OP2");
        sequenceCounter(sequencer).set(3);

        try (AckingUdpProbe recoveredReplicaProbe = new AckingUdpProbe();
             DatagramSocket sequencerSocket = new DatagramSocket();
             DatagramSocket rmSocket = new DatagramSocket(0)) {
            DatagramPacket from = new DatagramPacket(
                new byte[0],
                0,
                InetAddress.getByName("localhost"),
                rmSocket.getLocalPort());
            String readyRaw = "REPLICA_READY:3:localhost:" + recoveredReplicaProbe.port() + ":1";

            handleReplicaReady.invoke(sequencer, UDPMessage.parse(readyRaw), sequencerSocket, from);

            List<String> replayed = recoveredReplicaProbe.awaitMessages(1, 4000);
            assertEquals(1, replayed.size(), "Expected exactly one replayed message");
            assertEquals(2, extractSeq(replayed.get(0)),
                "lastSeq=1 should replay only seq 2 before current=3");

            rmSocket.setSoTimeout(4000);
            DatagramPacket ack = new DatagramPacket(new byte[256], 256);
            rmSocket.receive(ack);
            String ackMsg = new String(ack.getData(), 0, ack.getLength(), StandardCharsets.UTF_8);
            assertEquals("ACK:REPLICA_READY:3", ackMsg);

            boolean updated = false;
            for (InetSocketAddress addr : replicaAddresses(sequencer)) {
                if (addr.getPort() == recoveredReplicaProbe.port()) {
                    updated = true;
                    break;
                }
            }
            assertTrue(updated, "replicaAddresses should include recovered replica endpoint");
        }
    }

    private static Method privateMethod(String name, Class<?>... parameterTypes) throws Exception {
        Method m = Sequencer.class.getDeclaredMethod(name, parameterTypes);
        m.setAccessible(true);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<Integer, String> historyBuffer(Sequencer sequencer) throws Exception {
        Field field = Sequencer.class.getDeclaredField("historyBuffer");
        field.setAccessible(true);
        return (ConcurrentHashMap<Integer, String>) field.get(sequencer);
    }

    private static AtomicInteger sequenceCounter(Sequencer sequencer) throws Exception {
        Field field = Sequencer.class.getDeclaredField("sequenceCounter");
        field.setAccessible(true);
        return (AtomicInteger) field.get(sequencer);
    }

    @SuppressWarnings("unchecked")
    private static CopyOnWriteArrayList<InetSocketAddress> replicaAddresses(Sequencer sequencer) throws Exception {
        Field field = Sequencer.class.getDeclaredField("replicaAddresses");
        field.setAccessible(true);
        return (CopyOnWriteArrayList<InetSocketAddress>) field.get(sequencer);
    }

    private static void setReplicaAddresses(Sequencer sequencer, List<InetSocketAddress> testAddresses)
        throws Exception {
        CopyOnWriteArrayList<InetSocketAddress> addrs = replicaAddresses(sequencer);
        addrs.clear();
        addrs.addAll(testAddresses);
    }

    private static int extractSeq(String executeMsg) {
        String[] parts = executeMsg.split(":", 3);
        assertTrue(parts.length >= 2, "Invalid EXECUTE message: " + executeMsg);
        assertEquals("EXECUTE", parts[0], "Expected EXECUTE message");
        return Integer.parseInt(parts[1]);
    }

    private static List<Integer> extractSeqs(List<String> msgs) {
        List<Integer> out = new ArrayList<>();
        for (String msg : msgs) {
            out.add(extractSeq(msg));
        }
        return out;
    }

    private static class AckingUdpProbe implements AutoCloseable {
        private final DatagramSocket socket;
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread listener;

        AckingUdpProbe() throws Exception {
            this.socket = new DatagramSocket(0);
            this.listener = new Thread(() -> {
                while (running.get()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(new byte[8192], 8192);
                        socket.receive(packet);
                        String msg = new String(
                            packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        messages.add(msg);

                        byte[] ack = "ACK:TEST".getBytes(StandardCharsets.UTF_8);
                        DatagramPacket ackPacket = new DatagramPacket(
                            ack, ack.length, packet.getAddress(), packet.getPort());
                        socket.send(ackPacket);
                    } catch (Exception e) {
                        if (running.get()) {
                            // ignored in test helper; caller asserts expected outcomes
                        }
                    }
                }
            }, "sequencer-test-probe-" + socket.getLocalPort());
            this.listener.setDaemon(true);
            this.listener.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        List<InetSocketAddress> addressList() throws Exception {
            List<InetSocketAddress> addrs = new ArrayList<>();
            addrs.add(new InetSocketAddress(InetAddress.getByName("localhost"), port()));
            return addrs;
        }

        List<String> awaitMessages(int expectedCount, long timeoutMs) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                if (messages.size() >= expectedCount) {
                    return new ArrayList<>(messages.subList(0, expectedCount));
                }
                Thread.sleep(20);
            }
            return new ArrayList<>(messages);
        }

        @Override
        public void close() throws Exception {
            running.set(false);
            socket.close();
            listener.join(1000);
        }
    }
}
