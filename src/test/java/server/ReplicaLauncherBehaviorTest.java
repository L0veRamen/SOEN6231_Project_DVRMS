package server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplicaLauncherBehaviorTest {

  @Test
  void gapDelivery_emitsNumericNackAndAck() {
    ReplicaLauncher.ExecutionGate gate =
        new ReplicaLauncher.ExecutionGate(
            3, Collections.<String, VehicleReservationWS>emptyMap(), null);

    List<String> replies =
        gate.handleExecute(
            5,
            "REQ-10",
            "localhost",
            PortConfig.FE_UDP,
            "RESERVE:MTLU1111:MTL1001:01042026:02042026");

    assertIterableEquals(Arrays.asList("NACK:3:0:4", "ACK:5"), replies);
  }

  @Test
  void extractTargetOffice_routesByOperationShapeAndFallsBackSafely() {
    assertEquals(
        "WPG",
        ReplicaLauncher.extractTargetOffice(
            "ADDVEHICLE:WPGM1111:MB-NEW-1:SUV:WPG9001:120.0"));
    assertEquals("BNF", ReplicaLauncher.extractTargetOffice("REMOVEVEHICLE:BNFM1111:BNF1001"));
    assertEquals("MTL", ReplicaLauncher.extractTargetOffice("LISTAVAILABLE:MTLM1111"));
    assertEquals("WPG", ReplicaLauncher.extractTargetOffice("LISTRES:WPGU1111"));
    assertEquals(
        "BNF",
        ReplicaLauncher.extractTargetOffice("RESERVE:MTLU1111:BNF2001:01042026:02042026"));
    assertEquals("MTL", ReplicaLauncher.extractTargetOffice("FIND:SUV"));

    // Malformed payloads should never throw and should route to deterministic fallback.
    assertEquals("MTL", ReplicaLauncher.extractTargetOffice("RESERVE:MTLU1111"));
    assertEquals("MTL", ReplicaLauncher.extractTargetOffice("LISTRES:XX"));
  }
}
