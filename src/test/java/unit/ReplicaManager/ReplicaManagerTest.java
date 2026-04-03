package unit.ReplicaManager;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import server.PortConfig;
import server.ReplicaManager;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class ReplicaManagerTest {

    @ParameterizedTest(name = "replicaId={0} → rmPort={1}, replicaPort={2}")
    @CsvSource({
        "1, 7001, 6001",
        "2, 7002, 6002",
        "3, 7003, 6003",
        "4, 7004, 6004"
    })
    void constructorSetsCorrectPorts(int id, int expectedRmPort, int expectedReplicaPort) throws Exception {
        ReplicaManager rm = new ReplicaManager(id);

        Field rmPortField = ReplicaManager.class.getDeclaredField("rmPort");
        rmPortField.setAccessible(true);
        assertEquals(expectedRmPort, rmPortField.getInt(rm), "RM port mismatch for id=" + id);

        Field replicaPortField = ReplicaManager.class.getDeclaredField("replicaPort");
        replicaPortField.setAccessible(true);
        assertEquals(expectedReplicaPort, replicaPortField.getInt(rm), "Replica port mismatch for id=" + id);
    }
}
