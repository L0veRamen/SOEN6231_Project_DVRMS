package server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleReservationWSTest {

    private String previousDisableUdp;

    @BeforeEach
    void setup() {
        previousDisableUdp = System.getProperty("dvrms.disable.udp");
        System.setProperty("dvrms.disable.udp", "true");
        VehicleReservationWS.resetGlobalStateForTests();
    }

    @AfterEach
    void tearDown() {
        VehicleReservationWS.resetGlobalStateForTests();
        if (previousDisableUdp == null) {
            System.clearProperty("dvrms.disable.udp");
        } else {
            System.setProperty("dvrms.disable.udp", previousDisableUdp);
        }
    }

    @Test
    void byzantineHandleExecute_sendsResultToFe() throws Exception {
        try (DatagramSocket feSocket = new DatagramSocket(0)) {
            feSocket.setSoTimeout(1500);

            VehicleReservationWS ws = new VehicleReservationWS("MTL", 5001, "1");
            ws.handleUDPRequest("SET_BYZANTINE:true");

            String returned = ws.handleExecute(
                0, "REQ-1", "localhost", feSocket.getLocalPort(), "LISTAVAILABLE:MTLM1111");

            byte[] buf = new byte[8192];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            feSocket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

            assertTrue(returned.startsWith("RESULT:0:REQ-1:1:BYZANTINE_RANDOM_"));
            assertEquals(returned, received);
        }
    }

    @Test
    void executePath_reserveCancel_enforcesHomeOfficeBudgetAndCrossOfficeLimit() {
        VehicleReservationWS mtl = new VehicleReservationWS("MTL", PortConfig.officePort(1, "MTL"), "1");
        new VehicleReservationWS("WPG", PortConfig.officePort(1, "WPG"), "1");
        new VehicleReservationWS("BNF", PortConfig.officePort(1, "BNF"), "1");

        String firstReserve =
            mtl.handleUDPRequest("RESERVE_EXECUTE:MTLU1111:WPG1001:01012030:02012030");
        assertTrue(firstReserve.startsWith("SUCCESS: Reserved WPG1001"), firstReserve);
        assertTrue(firstReserve.contains("Remaining budget"), firstReserve);

        String secondReserveSameOffice =
            mtl.handleUDPRequest("RESERVE_EXECUTE:MTLU1111:WPG1002:03012030:04012030");
        assertTrue(
            secondReserveSameOffice.startsWith("FAIL: Already have 1 reservation at WPG office"),
            secondReserveSameOffice);

        String cancel = mtl.handleUDPRequest("CANCEL_EXECUTE:MTLU1111:WPG1001");
        assertTrue(cancel.startsWith("SUCCESS: Reservation cancelled."), cancel);
        assertTrue(cancel.contains("New budget: 1000"), cancel);

        String reserveAfterCancel =
            mtl.handleUDPRequest("RESERVE_EXECUTE:MTLU1111:WPG1002:05012030:06012030");
        assertTrue(reserveAfterCancel.startsWith("SUCCESS: Reserved WPG1002"), reserveAfterCancel);
    }

    @Test
    void remoteUdpReservePath_remainsBudgetNeutral() {
        VehicleReservationWS wpg = new VehicleReservationWS("WPG", PortConfig.officePort(1, "WPG"), "1");
        String reserve = wpg.handleUDPRequest("RESERVE:MTLU1111:WPG1001:01012030:02012030");
        assertTrue(reserve.startsWith("SUCCESS: Reserved WPG1001"), reserve);
        assertFalse(reserve.contains("Remaining budget"), reserve);
    }
}
