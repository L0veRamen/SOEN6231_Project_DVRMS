package server;

public class UDPMessage {

    public enum Type {
        // Core flow
        REQUEST,    // FE → Sequencer
        EXECUTE,    // Sequencer → Replicas
        RESULT,     // Replica → FE
        ACK,        // any → sender
        NACK,       // Replica → Sequencer

        // Failure handling
        INCORRECT_RESULT,  // FE → all RMs
        CRASH_SUSPECT,     // FE → all RMs
        REPLACE_REQUEST,   // FE → all RMs

        // RM coordination
        VOTE_BYZANTINE, VOTE_CRASH,
        SHUTDOWN, REPLICA_READY,

        // Heartbeat
        HEARTBEAT_CHECK, HEARTBEAT_ACK,

        // State transfer
        STATE_REQUEST, STATE_TRANSFER, INIT_STATE,

        // Testing
        SET_BYZANTINE
    }

    private final Type type;
    private final String[] fields;

    public UDPMessage(Type type, String... fields) {
        this.type = type;
        this.fields = fields;
    }

    /** Parse "TYPE:field1:field2:..." into a UDPMessage. */
    public static UDPMessage parse(String raw) {
        String[] parts = raw.split(":", -1);
        Type type = Type.valueOf(parts[0]);
        String[] fields = new String[parts.length - 1];
        System.arraycopy(parts, 1, fields, 0, fields.length);
        return new UDPMessage(type, fields);
    }

    /** Serialize back to "TYPE:field1:field2:..." */
    public String serialize() {
        StringBuilder sb = new StringBuilder(type.name());
        for (String f : fields) {
            sb.append(':').append(f);
        }
        return sb.toString();
    }

    public Type getType()          { return type; }
    public String getField(int i)  { return fields[i]; }
    public int fieldCount()        { return fields.length; }
}
