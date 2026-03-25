package server;

public final class PortConfig {
    private PortConfig() {}

    // Replica UDP ports (receive EXECUTE from Sequencer)
    public static final int REPLICA_1 = 6001;
    public static final int REPLICA_2 = 6002;
    public static final int REPLICA_3 = 6003;
    public static final int REPLICA_4 = 6004;
    public static final int[] ALL_REPLICAS = {REPLICA_1, REPLICA_2, REPLICA_3, REPLICA_4};

    // Replica Manager UDP ports
    public static final int RM_1 = 7001;
    public static final int RM_2 = 7002;
    public static final int RM_3 = 7003;
    public static final int RM_4 = 7004;
    public static final int[] ALL_RMS = {RM_1, RM_2, RM_3, RM_4};

    // Per-replica office ports (inter-office UDP, same as A3)
    // Replica 1: MTL=5001, WPG=5002, BNF=5003
    // Replica 2: MTL=5011, WPG=5012, BNF=5013
    // Replica 3: MTL=5021, WPG=5022, BNF=5023
    // Replica 4: MTL=5031, WPG=5032, BNF=5033
    public static int officePort(int replicaIndex, String office) {
        int base = 5001 + (replicaIndex - 1) * 10;
        switch (office) {
            case "MTL": return base;
            case "WPG": return base + 1;
            case "BNF": return base + 2;
            default: throw new IllegalArgumentException("Unknown office: " + office);
        }
    }

    // Front End
    public static final int FE_SOAP = 8080;
    public static final int FE_UDP  = 9000;

    // Sequencer
    public static final int SEQUENCER = 9100;
}
