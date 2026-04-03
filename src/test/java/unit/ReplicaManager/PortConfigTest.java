package unit.ReplicaManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import server.PortConfig;

import static org.junit.jupiter.api.Assertions.*;

public class PortConfigTest {

    @ParameterizedTest(name = "replicaId={0} → rmPort={1}, replicaPort={2}")
    @CsvSource({
        "1, 7001, 6001",
        "2, 7002, 6002",
        "3, 7003, 6003",
        "4, 7004, 6004"
    })
    void rmAndReplicaPorts(int id, int expectedRmPort, int expectedReplicaPort) {
        assertEquals(expectedRmPort,      PortConfig.ALL_RMS[id - 1],      "RM port mismatch for id=" + id);
        assertEquals(expectedReplicaPort, PortConfig.ALL_REPLICAS[id - 1], "Replica port mismatch for id=" + id);
    }

    @ParameterizedTest(name = "replica={0}, office={1} → port={2}")
    @CsvSource({
        "1, MTL, 5001",
        "1, WPG, 5002",
        "1, BNF, 5003",
        "2, WPG, 5012",
        "4, BNF, 5033"
    })
    void officePortMapping(int replicaId, String office, int expectedPort) {
        assertEquals(expectedPort, PortConfig.officePort(replicaId, office));
    }

    @Test
    void officePortThrowsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> PortConfig.officePort(1, "TORONTO"));
    }
}
