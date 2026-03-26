# Failure Demo Setup

## 1) Design in 60 Seconds

- The Front End (FE) waits for matching replica results and returns when **2 replicas match** (`f+1=2`).
- A **Byzantine fault** means one replica returns a wrong result. After repeated mismatches, that replica is replaced.
- A **Crash fault** means one replica does not respond. Replica Manager (RM) replaces it and restores state.

## 2) Preflight

Verify required tools:

```bash
for cmd in java mvn wsimport nc lsof; do
  command -v "$cmd" >/dev/null && echo "OK   $cmd" || echo "MISS $cmd"
done
```

Kill stale processes from previous runs:

```bash
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" || true
```

Verify ports are free (should return empty):

```bash
lsof -nP \
  -iTCP:8080 \
  -iUDP:9000 -iUDP:9100 \
  -iUDP:6001 -iUDP:6002 -iUDP:6003 -iUDP:6004 \
  -iUDP:7001 -iUDP:7002 -iUDP:7003 -iUDP:7004
```

## 3) Start System

Build once:

```bash
mvn clean compile
```

Open 7 terminals. Start each component in order -- wait for the expected log before moving to the next.

### Terminal 1 -- Sequencer

```bash
java -cp target/classes server.Sequencer
```

Expected: `Sequencer listening on port 9100`

### Terminal 2 -- Replica Manager 1

```bash
java -cp target/classes server.ReplicaManager 1
```

Expected: `RM1: Replica launched on port 6001`

### Terminal 3 -- Replica Manager 2

```bash
java -cp target/classes server.ReplicaManager 2
```

Expected: `RM2: Replica launched on port 6002`

### Terminal 4 -- Replica Manager 3

```bash
java -cp target/classes server.ReplicaManager 3
```

Expected: `RM3: Replica launched on port 6003`

### Terminal 5 -- Replica Manager 4

```bash
java -cp target/classes server.ReplicaManager 4
```

Expected: `RM4: Replica launched on port 6004`

### Terminal 6 -- Front End

```bash
java -cp target/classes server.FrontEnd
```

Expected: `FrontEnd published at http://localhost:8080/fe`

### Terminal 7 -- Client

Build client stubs:

```bash
./build-client.sh --wsdl http://localhost:8080/fe?wsdl
```

Run the manager client:

```bash
java -cp bin client.ManagerClient --wsdl http://localhost:8080/fe?wsdl
```

Run the customer client:

```bash
java -cp bin client.CustomerClient --wsdl http://localhost:8080/fe?wsdl
```

The client prompts for an ID at startup. The first 3 characters choose the office, the 4th character chooses the role (`M` = manager, `U` = customer):

| Role | MTL | WPG | BNF |
|---|---|---|---|
| Manager | MTLM1000 | WPGM1000 | BNFM1000 |
| Customer | MTLU1000 | WPGU1000 | BNFU1000 |

To run multiple clients at once, open additional terminals and repeat the `java -cp bin ...` command with a different ID each time.

Baseline before fault injection: run menu option **3** (List Available Vehicles) once and confirm the response is successful.

### Alternative -- One-Command Startup

Instead of opening 7 terminals manually:

```bash
./demo-start.sh
```

Logs go to `logs/`. Stop everything with:

```bash
./demo-stop.sh
```

## 4) Three Demo Scenarios

### A) Byzantine Demo (Replica 3)

Inject fault:

```bash
echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003
```

From `ManagerClient`, run option **3** at least three times.

Wait 8-10 seconds for replacement + state transfer, then run option **3** once more for post-recovery check.

Expected:

- Client still gets successful responses (not `FAIL`).
- Replica 3 is replaced automatically.
- RM3 logs: `Byzantine replace requested for 3`, `Starting replica replacement`, `State transfer complete, lastSeq=...`
- Sequencer log: `3 ready, replaying from seq ...`

Narration: "Inject Byzantine on replica 3, show client still succeeds, then show RM3 replacement and sequencer replay."

Reset:

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003
```

### B) Crash Demo (Replica 2)

Inject fault:

```bash
kill $(lsof -ti udp:6002 | head -n1)
```

From `ManagerClient`:

1. Run option **3** once (triggers crash handling).
2. Wait 8-10 seconds for recovery.
3. Run option **3** again (post-recovery verification).

Expected:

- Requests still succeed (not `FAIL`).
- RM2 recovers replica 2 and synchronizes state.
- RM2 logs: `Starting replica replacement`, `State transfer complete, lastSeq=...`
- Sequencer log: `2 ready, replaying from seq ...`

Narration: "Crash replica 2, show request still succeeds from remaining replicas, then show RM2 recovery logs."

### C) Simultaneous Demo (Crash + Byzantine)

Inject both faults:

```bash
kill $(lsof -ti udp:6002 | head -n1)
```

```bash
echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003
```

From `ManagerClient`, run option **3** three times.

- First request shows immediate tolerance: 2 healthy replicas still return correct majority.
- Repeated requests drive Byzantine strike count to replacement threshold.

Wait 8-10 seconds, then run option **3** once more for post-recovery verification.

Expected:

- Immediate: FE returns correct result from two matching healthy replicas.
- Delayed: RM2 and RM3 recovery workflows execute; replacement/state transfer logs appear.
- Sequencer logs: `2 ready, replaying from seq ...` and `3 ready, replaying from seq ...`

Narration: "Inject crash + Byzantine together, show immediate correct response, then show both recovery paths in logs."

Reset:

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003
```

## 5) Reset Between Scenarios

Minimal reset (recommended between A/B/C):

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003 || true
```

Full reset (then restart from section 3):

```bash
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" || true
```

## 6) What to Look For

- FE side: request returns success (not `FAIL`).
- RM side: `Byzantine replace requested for 3` (Byzantine scenarios).
- RM side: `Starting replica replacement`.
- RM side: `State transfer complete`.
- Sequencer side: `<replicaID> ready, replaying from seq`.

## 7) Verify via Tests (Optional)

```bash
mvn -q -Dtest=integration.ReplicationIntegrationTest#t6_byzantineFirstStrike+t7_byzantineSecondStrike+t8_byzantineThirdStrikeReplace test
```

```bash
mvn -q -Dtest=integration.ReplicationIntegrationTest#t11_crashDetection+t12_crashRecovery test
```

```bash
mvn -q -Dtest=integration.ReplicationIntegrationTest#t15_crashPlusByzantine test
```
