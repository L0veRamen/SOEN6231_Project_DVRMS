package server;

import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleReservationWSTest {

    @Test
    void byzantineHandleExecute_sendsResultToFe() throws Exception {
        String previousDisableUdp = System.getProperty("dvrms.disable.udp");
        System.setProperty("dvrms.disable.udp", "true");

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
        } finally {
            if (previousDisableUdp == null) {
                System.clearProperty("dvrms.disable.udp");
            } else {
                System.setProperty("dvrms.disable.udp", previousDisableUdp);
            }
        }
    }
}
