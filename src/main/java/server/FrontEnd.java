package server;

import java.util.Map;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Front End (FE) — single instance, the sole entry point for client requests in the FT-DVRMS system.
 *
 * <p>The FE is responsible for:
 * <ol>
 *   <li>Exposing all vehicle-reservation operations as JAX-WS {@code @WebMethod}s (SOAP port 8080).</li>
 *   <li>Forwarding each client request to the Sequencer via reliable UDP for total-order delivery.</li>
 *   <li>Collecting {@code RESULT} messages from replicas on a background listener thread and
 *       resolving the majority answer.</li>
 *   <li>Detecting Byzantine faults (3 consecutive mismatches from the same replica) and sending
 *       a {@code REPLACE_REQUEST} to all RMs.</li>
 *   <li>Detecting crash faults (replica did not respond) and sending a {@code CRASH_SUSPECT}
 *       to all RMs.</li>
 * </ol>
 *
 * <p><b>Workflow overview:</b>
 * <pre>
 *   Client (SOAP)
 *       │
 *       ▼
 *   {@literal @}WebMethod ──→ forwardAndCollect()
 *       │  REQUEST:reqID:localhost:FE_UDP:operation  ──→  Sequencer
 *       │
 *       │  (background thread: FE-ResultListener)
 *       │  ◄── RESULT:seqNum:reqID:replicaID:payload  (from replicas)
 *       │       addResult() ──→ 2+ matching ──→ majorityFuture.complete()
 *       │
 *       ▼  (majority ready OR timeout)
 *   processResults()
 *       │
 *       ├─ mismatch ──→ INCORRECT_RESULT to all RMs
 *       │               3 strikes ──→ REPLACE_REQUEST to all RMs
 *       │
 *       └─ no reply ──→ CRASH_SUSPECT to all RMs
 * </pre>
 *
 * <p>Usage: {@code java server.FrontEnd}
 *
 * @see PortConfig for FE and RM port assignments
 * @see UDPMessage for message format definitions
 * @see ReliableUDPSender for ACK-based reliable UDP with exponential backoff
 */
@WebService(name = "VehicleReservationWS", serviceName = "VehicleReservationService")
public class FrontEnd {

    private final ConcurrentHashMap<String, RequestContext> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> byzantineCount = new ConcurrentHashMap<>();
    private final AtomicLong slowestResponseTime = new AtomicLong(2000);
    private final ReliableUDPSender sender;
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    static class RequestContext {
        final String requestID;
        final long sentTime = System.currentTimeMillis();
        final ConcurrentHashMap<String, String> replicaResults = new ConcurrentHashMap<>();
        private final CompletableFuture<String> majorityFuture = new CompletableFuture<>();
        volatile int seqNum = -1;

        RequestContext(String requestID) { this.requestID = requestID; }

        void addResult(String replicaID, String result, int seqNum) {
            if (this.seqNum < 0) this.seqNum = seqNum;
            replicaResults.put(replicaID, result);
            long matchCount = replicaResults.values().stream()
                .filter(v -> v.equals(result)).count();
            if (matchCount >= 2) {
                majorityFuture.complete(result);
            }
        }

        String awaitMajority(long timeoutMs) {
            try {
                return majorityFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public FrontEnd() {
        this(new ReliableUDPSender());
    }

    public FrontEnd(ReliableUDPSender sender) {
        this.sender = sender;
        if (!"true".equals(System.getProperty("dvrms.disable.udp"))) {
            new Thread(this::listenForResults, "FE-ResultListener").start();
        }
    }

    // ===== @WebMethod — same signatures as VehicleReservationWS =====

    /**
     * Adds a vehicle to the fleet at the given manager's location.
     *
     * @param managerID        the ID of the manager performing the operation
     * @param vehicleNumber    total number of vehicles of this type available
     * @param vehicleType      category of the vehicle (e.g., car, van, SUV)
     * @param vehicleID        unique vehicle identifier
     * @param reservationPrice daily reservation price
     * @return success or failure message from the majority of replicas
     */
    @WebMethod
    public String addVehicle(
            @WebParam(name = "managerID") String managerID,
            @WebParam(name = "vehicleNumber") String vehicleNumber,
            @WebParam(name = "vehicleType") String vehicleType,
            @WebParam(name = "vehicleID") String vehicleID,
            @WebParam(name = "reservationPrice") double reservationPrice) {
        String operation = "ADDVEHICLE:" + managerID + ":" + vehicleNumber + ":" + vehicleType + ":" + vehicleID + ":" + reservationPrice;
        return forwardAndCollect(operation);
    }

    /**
     * Removes a vehicle from the fleet at the given manager's location.
     *
     * @param managerID the ID of the manager performing the operation
     * @param vehicleID unique identifier of the vehicle to remove
     * @return success or failure message from the majority of replicas
     */
    @WebMethod
    public String removeVehicle(
            @WebParam(name = "managerID") String managerID,
            @WebParam(name = "vehicleID") String vehicleID) {
        String operation = "REMOVEVEHICLE:" + managerID + ":" + vehicleID;
        return forwardAndCollect(operation);
    }

    /**
     * Lists all available vehicles at the given manager's location.
     *
     * @param managerID the ID of the manager whose inventory is queried
     * @return a formatted list of available vehicles from the majority of replicas
     */
    @WebMethod
    public String listAvailableVehicle(@WebParam(name = "managerID") String managerID) {
        String operation = "LISTAVAILABLE:" + managerID;
        return forwardAndCollect(operation);
    }

    /**
     * Reserves a vehicle for a customer over the given date range.
     *
     * <p>Routed as an execute-path operation so that home-office reservation logic
     * (A3) is applied correctly at the replica matching the customer's city prefix.
     *
     * @param customerID the customer's ID (city prefix determines home office)
     * @param vehicleID  unique identifier of the vehicle to reserve
     * @param startDate  reservation start date
     * @param endDate    reservation end date
     * @return success or failure message from the majority of replicas
     */
    @WebMethod
    public String reserveVehicle(
            @WebParam(name = "customerID") String customerID,
            @WebParam(name = "vehicleID") String vehicleID,
            @WebParam(name = "startDate") String startDate,
            @WebParam(name = "endDate") String endDate) {
        // Execute-path operation: route by customer office so A3 home-office logic is preserved.
        String operation = "RESERVE_EXECUTE:" + customerID + ":" + vehicleID + ":" + startDate + ":" + endDate;
        return forwardAndCollect(operation);
    }

    /**
     * Updates the date range of an existing reservation.
     *
     * <p>Routed as an execute-path operation so that home-office logic is preserved.
     *
     * @param customerID   the customer's ID
     * @param vehicleID    unique identifier of the reserved vehicle
     * @param newStartDate updated reservation start date
     * @param newEndDate   updated reservation end date
     * @return success or failure message from the majority of replicas
     */
    @WebMethod
    public String updateReservation(
            @WebParam(name = "customerID") String customerID,
            @WebParam(name = "vehicleID") String vehicleID,
            @WebParam(name = "newStartDate") String newStartDate,
            @WebParam(name = "newEndDate") String newEndDate) {
        // Execute-path operation: route by customer office so A3 home-office logic is preserved.
        String operation =
            "ATOMIC_UPDATE_EXECUTE:" + customerID + ":" + vehicleID + ":" + newStartDate + ":" + newEndDate;
        return forwardAndCollect(operation);
    }

    /**
     * Cancels an existing reservation for a customer.
     *
     * <p>Routed as an execute-path operation so that home-office logic is preserved.
     *
     * @param customerID the customer's ID
     * @param vehicleID  unique identifier of the reserved vehicle
     * @return success or failure message from the majority of replicas
     */
    @WebMethod
    public String cancelReservation(
            @WebParam(name = "customerID") String customerID,
            @WebParam(name = "vehicleID") String vehicleID) {
        // Execute-path operation: route by customer office so A3 home-office logic is preserved.
        String operation = "CANCEL_EXECUTE:" + customerID + ":" + vehicleID;
        return forwardAndCollect(operation);
    }

    /**
     * Finds vehicles of a given type available across all city offices.
     *
     * @param customerID  the customer's ID
     * @param vehicleType category of vehicle to search for
     * @return a formatted list of matching vehicles from the majority of replicas
     */
    @WebMethod
    public String findVehicle(
            @WebParam(name = "customerID") String customerID,
            @WebParam(name = "vehicleType") String vehicleType) {
        String operation = "FIND:" + customerID + ":" + vehicleType;
        return forwardAndCollect(operation);
    }

    /**
     * Lists all active reservations for a customer.
     *
     * @param customerID the customer whose reservations are queried
     * @return a formatted list of reservations from the majority of replicas
     */
    @WebMethod
    public String listCustomerReservations(@WebParam(name = "customerID") String customerID) {
        String operation = "LISTRES:" + customerID;
        return forwardAndCollect(operation);
    }

    /**
     * Adds a customer to the wait list for a vehicle over a given date range.
     *
     * @param customerID the customer's ID
     * @param vehicleID  unique identifier of the desired vehicle
     * @param startDate  requested start date
     * @param endDate    requested end date
     * @return success or failure message from the majority of replicas
     */
    @WebMethod
    public String addToWaitList(
            @WebParam(name = "customerID") String customerID,
            @WebParam(name = "vehicleID") String vehicleID,
            @WebParam(name = "startDate") String startDate,
            @WebParam(name = "endDate") String endDate) {
        String operation = "WAITLIST:" + customerID + ":" + vehicleID + ":" + startDate + ":" + endDate;
        return forwardAndCollect(operation);
    }

    // ===== Core: forward to Sequencer and collect results =====

    /**
     * Forwards an operation to the Sequencer and collects the majority result from replicas.
     *
     * <p>Sends {@code REQUEST:reqID:localhost:FE_UDP:operation} to the Sequencer, then blocks
     * until a majority future completes or the adaptive timeout ({@code 2 × slowestResponseTime})
     * expires. On timeout, falls back to {@link #vote(RequestContext)} over already-received results.
     *
     * @param operation the operation string (e.g. {@code "ADDVEHICLE:MTL001:..."})
     * @return the majority result, or {@code "FAIL:..."} if no majority was reached
     */
    private String forwardAndCollect(String operation) {
        String reqID = "REQ-" + requestCounter.incrementAndGet();
        RequestContext ctx = new RequestContext(reqID);
        pendingRequests.put(reqID, ctx);

        String requestMsg = "REQUEST:" + reqID + ":localhost:" + PortConfig.FE_UDP + ":" + operation;
        try {
            DatagramSocket socket = new DatagramSocket();
            sender.send(requestMsg, InetAddress.getByName("localhost"), PortConfig.SEQUENCER, socket);
            socket.close();
        } catch (Exception e) {
            pendingRequests.remove(reqID);
            return "FAIL: Could not reach Sequencer";
        }
        System.out.println("FE: [" + reqID + "] forwarded");

        long timeout = 2 * slowestResponseTime.get();
        String majorityResult = ctx.awaitMajority(timeout);

        if (majorityResult != null) {
            processResults(ctx, majorityResult);
            System.out.println("FE: [" + reqID + "] returning result");
            return majorityResult;
        }

        String result = vote(ctx);
        if (!result.startsWith("FAIL")) {
            System.out.println("FE: [" + reqID + "] returning result");
        }
        return result;
    }

    /**
     * Tallies already-received replica results after a timeout to find a majority.
     *
     * @param ctx the request context containing partial replica results
     * @return the first result agreed upon by 2 or more replicas,
     *         or {@code "FAIL: No majority result"} if none exists
     */
    private String vote(RequestContext ctx) {
        ConcurrentHashMap<String, java.util.List<String>> resultToReplicas = new ConcurrentHashMap<>();
        for (Map.Entry<String, String> entry : ctx.replicaResults.entrySet()) {
            resultToReplicas.computeIfAbsent(entry.getValue(), k -> new CopyOnWriteArrayList<>())
                .add(entry.getKey());
        }

        String majorityResult = null;
        for (Map.Entry<String, java.util.List<String>> entry : resultToReplicas.entrySet()) {
            if (entry.getValue().size() >= 2) {
                majorityResult = entry.getKey();
                break;
            }
        }

        if (majorityResult == null) {
            pendingRequests.remove(ctx.requestID);
            return "FAIL: No majority result";
        }

        processResults(ctx, majorityResult);
        return majorityResult;
    }

    /**
     * Compares each replica's result against the majority and triggers fault notifications.
     *
     * <p>For every replica whose result differs from {@code majorityResult}, increments its
     * Byzantine strike counter and sends {@code INCORRECT_RESULT:reqID:seqNum:replicaID} to
     * all RMs. On 3 strikes, also sends {@code REPLACE_REQUEST:replicaID:BYZANTINE_THRESHOLD}.
     * For each replica that did not respond at all, sends
     * {@code CRASH_SUSPECT:reqID:seqNum:replicaID} to all RMs.
     * Updates {@code slowestResponseTime} and removes the request from {@code pendingRequests}.
     *
     * @param ctx           the completed request context
     * @param majorityResult the agreed-upon result from the majority of replicas
     */
    private void processResults(RequestContext ctx, String majorityResult) {
        for (Map.Entry<String, String> entry : ctx.replicaResults.entrySet()) {
            String replicaID = entry.getKey();
            if (entry.getValue().equals(majorityResult)) {
                byzantineCount.computeIfAbsent(replicaID, k -> new AtomicInteger(0)).set(0);
            } else {
                int count = byzantineCount.computeIfAbsent(replicaID, k -> new AtomicInteger(0))
                    .incrementAndGet();
                System.out.println("FE: [" + ctx.requestID + "] Byzantine mismatch from replica "
                    + replicaID + " (strike " + count + "/3)");
                sendToAllRMs("INCORRECT_RESULT:" + ctx.requestID + ":" + ctx.seqNum + ":" + replicaID);
                if (count >= 3) {
                    System.out.println("FE: [" + ctx.requestID + "] REPLACE_REQUEST sent for replica " + replicaID);
                    sendToAllRMs("REPLACE_REQUEST:" + replicaID + ":BYZANTINE_THRESHOLD");
                }
            }
        }
        // Report crash for non-responding replicas
        for (int i = 0; i < PortConfig.ALL_REPLICAS.length; i++) {
            String rid = String.valueOf(i + 1);
            if (!ctx.replicaResults.containsKey(rid)) {
                System.out.println("FE: [" + ctx.requestID + "] CRASH_SUSPECT sent for replica " + rid);
                sendToAllRMs("CRASH_SUSPECT:" + ctx.requestID + ":" + ctx.seqNum + ":" + rid);
            }
        }
        long elapsed = System.currentTimeMillis() - ctx.sentTime;
        slowestResponseTime.updateAndGet(prev -> Math.max(prev, elapsed));
        pendingRequests.remove(ctx.requestID);
    }

    // ===== UDP listener for RESULT messages =====

    /** Sends {@code ACK:<token>} back to the source of a UDP packet. */
    private void ackUdpMessage(DatagramSocket socket, DatagramPacket packet, String token) {
        try {
            String ack = "ACK:" + token;
            byte[] ackData = ack.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(ackData, ackData.length, packet.getAddress(), packet.getPort()));
        } catch (Exception e) {
            System.err.println("FE ACK send error: " + e.getMessage());
        }
    }

    /**
     * Background UDP listener — receives {@code RESULT} messages from replicas and routes
     * each one to its pending {@link RequestContext}. Also ACKs {@code REPLICA_READY} messages
     * sent by RMs after a replica replacement.
     *
     * <p>Listens on {@link PortConfig#FE_UDP}. Runs in a dedicated background thread.
     *
     * <p>Handled message types:
     * <ul>
     *   <li>{@code RESULT:<seqNum>:<reqID>:<replicaID>:<payload>} — replica response</li>
     *   <li>{@code REPLICA_READY} — replacement complete notification from an RM</li>
     * </ul>
     */
    private void listenForResults() {
        try (DatagramSocket socket = new DatagramSocket(PortConfig.FE_UDP)) {
            byte[] buf = new byte[8192];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                UDPMessage msg = UDPMessage.parse(raw);
                if (msg.getType() == UDPMessage.Type.RESULT) {
                    ackUdpMessage(socket, packet, "RESULT");
                    int seqNum = Integer.parseInt(msg.getField(0));
                    String reqID = msg.getField(1);
                    String replicaID = msg.getField(2);
                    // Result is everything from field 3 onward
                    StringBuilder result = new StringBuilder(msg.getField(3));
                    for (int i = 4; i < msg.fieldCount(); i++) {
                        result.append(':').append(msg.getField(i));
                    }

                    RequestContext ctx = pendingRequests.get(reqID);
                    if (ctx != null) {
                        System.out.println("FE: [" + reqID + "] result from replica " + replicaID);
                        ctx.addResult(replicaID, result.toString(), seqNum);
                    }
                } else if (msg.getType() == UDPMessage.Type.REPLICA_READY) {
                    ackUdpMessage(socket, packet, "REPLICA_READY");
                }
            }
        } catch (Exception e) {
            System.err.println("FE result listener error: " + e.getMessage());
        }
    }

    /** Broadcasts {@code message} to all four RM ports via reliable UDP. */
    private void sendToAllRMs(String message) {
        try {
            DatagramSocket socket = new DatagramSocket();
            for (int rmPort : PortConfig.ALL_RMS) {
                sender.send(message, InetAddress.getByName("localhost"), rmPort, socket);
            }
            socket.close();
        } catch (Exception e) {
            System.err.println("FE→RM send error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        FrontEnd fe = new FrontEnd();
        String url = "http://localhost:" + PortConfig.FE_SOAP + "/fe";
        Endpoint.publish(url, fe);
        System.out.println("FrontEnd published at " + url);
    }
}
