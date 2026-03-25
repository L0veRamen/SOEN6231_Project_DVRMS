package integration;

import org.junit.jupiter.api.*;
import server.*;

import java.net.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end replication tests.
 * Starts all P2 components and validates behavior through the FE SOAP interface.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReplicationIntegrationTest {

    @BeforeAll
    static void startSystem() throws Exception {
        // Start Sequencer
        new Thread(() -> new Sequencer().start()).start();

        // Start 4 Replicas
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            new Thread(() -> ReplicaLauncher.main(new String[]{String.valueOf(id)})).start();
        }

        // Start 4 RMs
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            new Thread(() -> new ReplicaManager(id).start()).start();
        }

        // Start FE (SOAP endpoint)
        FrontEnd fe = new FrontEnd();
        javax.xml.ws.Endpoint.publish("http://localhost:" + PortConfig.FE_SOAP + "/fe", fe);

        Thread.sleep(3000); // wait for startup
    }

    @AfterAll
    static void stopSystem() {
        // Shutdown all components
    }

    // ===== T1–T5: Normal operation =====

    @Test @Order(1)
    void t1_vehicleCrud() {
        // TODO: Add, list, remove vehicle through FE
    }

    @Test @Order(2)
    void t2_localReservation() {
        // TODO: Reserve a vehicle; budget deducted
    }

    @Test @Order(3)
    void t3_crossOfficeReservation() {
        // TODO: MTLU1111 reserves WPG1001
    }

    @Test @Order(4)
    void t4_concurrentRequests() {
        // TODO: Two threads reserve same vehicle simultaneously
    }

    @Test @Order(5)
    void t5_waitlist() {
        // TODO: Second customer tries same vehicle → waitlisted
    }

    // ===== T6–T10: Byzantine failure =====

    @Test @Order(6)
    void t6_byzantineFirstStrike() {
        enableByzantine(3, true);
        // TODO: Send request → FE returns correct from R1/R2/R4
    }

    @Test @Order(7)
    void t7_byzantineSecondStrike() {
        // TODO: R3 still Byzantine → byzantineCount = 2
    }

    @Test @Order(8)
    void t8_byzantineThirdStrikeReplace() {
        // TODO: byzantineCount = 3 → triggers replacement
    }

    @Test @Order(9)
    void t9_afterReplacement() {
        // TODO: New R3 is correct
    }

    @Test @Order(10)
    void t10_byzantineCounterReset() {
        enableByzantine(3, false);
        // TODO: Correct response resets counter
    }

    // ===== T11–T14: Crash failure =====

    @Test @Order(11)
    void t11_crashDetection() {
        // TODO: Kill R2 process → FE returns from 3 matching replicas
    }

    @Test @Order(12)
    void t12_crashRecovery() {
        // TODO: RMs detect + replace R2
    }

    @Test @Order(13)
    void t13_stateTransfer() {
        // TODO: New R2 gets state → subsequent requests match all 4
    }

    @Test @Order(14)
    void t14_operationDuringRecovery() {
        // TODO: System works with 3 replicas while R2 recovers
    }

    // ===== T15–T17: Simultaneous failure =====

    @Test @Order(15)
    void t15_crashPlusByzantine() {
        // TODO: Kill R2 + Byzantine R3 → R1/R4 match (f+1 = 2)
    }

    @Test @Order(16)
    void t16_dualRecovery() {
        // TODO: Both R2 and R3 replaced
    }

    @Test @Order(17)
    void t17_stateConsistencyAfterRecovery() {
        // TODO: All 4 replicas have identical state
    }

    // ===== T18–T21: Edge cases =====

    @Test @Order(18)
    void t18_packetLossRetransmit() {
        // TODO: Simulate packet loss → Sequencer retransmits → R4 ACKs
    }

    @Test @Order(19)
    void t19_outOfOrderDelivery() {
        // TODO: seq#5 before seq#4 → holdback queue buffers → correct order
    }

    @Test @Order(20)
    void t20_threeSimultaneousClients() {
        // TODO: 3 threads send requests → unique seq# each → same total order
    }

    @Test @Order(21)
    void t21_fullCrossOfficeFlow() {
        // TODO: reserve → update → cancel across offices
    }

    // ===== Helpers =====

    private void enableByzantine(int replicaId, boolean enable) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String msg = "SET_BYZANTINE:" + enable;
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length,
                InetAddress.getByName("localhost"),
                PortConfig.ALL_REPLICAS[replicaId - 1]));
        } catch (Exception e) {
            fail("Could not send SET_BYZANTINE: " + e.getMessage());
        }
    }
}
