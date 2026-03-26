# Failure Demo Setup (Design-Doc First)

This guide is intentionally simple and demo-friendly.

## 1) Design in 60 Seconds

- The Front End (FE) waits for matching replica results and returns when **2 replicas match** (`f+1=2`).
- A **Byzantine fault** means one replica returns a wrong result. After repeated mismatches, that replica is replaced.
- A **Crash fault** means one replica does not respond. Replica Manager (RM) replaces it and restores state.

## 2) Preflight (Before You Launch)

Verify required tools:

```bash
for cmd in java mvn wsimport nc lsof; do
  command -v "$cmd" >/dev/null && echo "OK   $cmd" || echo "MISS $cmd"
done
```

Clean stale processes from previous runs:

```bash
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" || true
```

Quick port occupancy check (should be empty before startup):

```bash
lsof -nP \
  -iTCP:8080 \
  -iUDP:9000 -iUDP:9100 \
  -iUDP:6001 -iUDP:6002 -iUDP:6003 -iUDP:6004 \
  -iUDP:7001 -iUDP:7002 -iUDP:7003 -iUDP:7004
```

## 3) Start System (RM-Owned Replica Lifecycle)

Build once:

```bash
mvn clean compile
```

Open separate terminals and start in this order:

```bash
# Terminal 1
java -cp target/classes server.Sequencer

# Terminal 2
java -cp target/classes server.ReplicaManager 1

# Terminal 3
java -cp target/classes server.ReplicaManager 2

# Terminal 4
java -cp target/classes server.ReplicaManager 3

# Terminal 5
java -cp target/classes server.ReplicaManager 4

# Terminal 6
java -cp target/classes server.FrontEnd
```

FE endpoint: `http://localhost:8080/fe?wsdl`

Startup verification (look for these logs):

- Sequencer terminal: `Sequencer listening on port 9100`
- RM terminals:
  - `RM1..RM4: Replica launched on port 6001..6004`
  - `RM1..RM4 listening on port 7001..7004`
  - replica subprocess logs: `Replica <id> started on UDP port 600<id>`
- FE terminal: `FrontEnd published at http://localhost:8080/fe`

Run client against FE WSDL:

```bash
./build-client.sh --wsdl http://localhost:8080/fe?wsdl
java -cp bin client.ManagerClient --wsdl http://localhost:8080/fe?wsdl
```

Use manager ID `MTLM1111`.

Baseline before fault injection:

1. Run menu option `3` (List Available Vehicles) once.
2. Confirm response is successful and non-empty.

## 4) Three Demo Scenarios

### A) Byzantine Demo (Replica 3)

Inject fault:

```bash
echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003
```

Trigger detection/replacement:

From `ManagerClient`, run option `3` at least three times.

Then wait about 8-10 seconds for vote window + replacement + state transfer, and run option `3` once more for post-recovery check.

Expected:

- Client still gets successful responses (not `FAIL`).
- FE still returns correct result from matching replicas.
- Replica 3 is replaced automatically.
- RM logs include:
  - `Byzantine replace requested for 3`
  - `Starting replica replacement`
  - `State transfer complete, lastSeq=...`
- Sequencer log includes:
  - `3 ready, replaying from seq ...`

Narration cue:
- "Inject Byzantine on replica 3, show client still succeeds, then show RM3 replacement and sequencer replay."

Optional reset:

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003
```

### B) Crash Demo (Replica 2)

Inject fault (safe kill command):

```bash
pid=$(lsof -ti udp:6002 | head -n1)
if [ -n "$pid" ]; then
  kill "$pid"
  echo "Killed replica on udp:6002 (pid=$pid)"
else
  echo "Replica 2 already down (no process bound to udp:6002)"
fi
```

Then from `ManagerClient`:

1. Run option `3` once (triggers crash handling path).
2. Wait about 8-10 seconds for recovery.
3. Run option `3` again (post-recovery verification).

Expected:

- Requests still succeed (not `FAIL`).
- RM2 recovers replica 2 and synchronizes state.
- RM2 logs include:
  - `Starting replica replacement`
  - `State transfer complete, lastSeq=...`
- Sequencer log includes:
  - `2 ready, replaying from seq ...`

Narration cue:
- "Crash replica 2, show request still succeeds from remaining replicas, then show RM2 recovery logs."

### C) Simultaneous Demo (Crash + Byzantine)

Inject both faults:

```bash
pid=$(lsof -ti udp:6002 | head -n1)
if [ -n "$pid" ]; then
  kill "$pid"
  echo "Killed replica on udp:6002 (pid=$pid)"
else
  echo "Replica 2 already down (no process bound to udp:6002)"
fi

echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003
```

Then from `ManagerClient`, run option `3` three times (not once).

- First request demonstrates immediate tolerance: 1 crash + 1 Byzantine still returns correct majority from 2 good replicas.
- Repeated requests drive Byzantine strike count to replacement threshold.
- Wait about 8-10 seconds and run option `3` once more for post-recovery verification.

Expected:

- Immediate: FE returns correct result from two matching healthy replicas.
- Delayed: RM2 (crash) and RM3 (Byzantine) recovery workflows execute; replacement/state transfer logs appear.
- Sequencer includes replay logs for recovered replicas:
  - `2 ready, replaying from seq ...`
  - `3 ready, replaying from seq ...`

Narration cue:
- "Inject crash + Byzantine together, show immediate correct response, then show both recovery paths in logs."

Optional reset:

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003
```

## 5) Reset/Cleanup Between Scenarios

Minimal reset (recommended between A/B/C):

```bash
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003 || true
```

Deterministic full reset (if any scenario output looks inconsistent):

```bash
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager" || true
```

Then restart from section **3) Start System**.

## 6) What to Look For (Simple)

- FE side: request returns success (not `FAIL`).
- RM side includes `Byzantine replace requested for 3` (Byzantine scenarios).
- RM side includes `Starting replica replacement`.
- RM side includes `State transfer complete`.
- Sequencer side includes `<replicaID> ready, replaying from seq`.

## 7) Backup Verification (Optional)

If you want quick proof via tests instead of manual demo:

```bash
# Byzantine core flow
mvn -q -Dtest=integration.ReplicationIntegrationTest#t6_byzantineFirstStrike+t7_byzantineSecondStrike+t8_byzantineThirdStrikeReplace test

# Crash core flow
mvn -q -Dtest=integration.ReplicationIntegrationTest#t11_crashDetection+t12_crashRecovery test

# Simultaneous core tolerance
mvn -q -Dtest=integration.ReplicationIntegrationTest#t15_crashPlusByzantine test
```

## 8) Rehearsal Test Plan

1. Cold-start rehearsal from clean ports (use preflight + startup verification).
2. Run scenarios A, B, C in order, using reset between scenarios.
3. Ask one teammate to execute this file verbatim and note unclear points.
4. Confirm each scenario shows at least one full checkpoint set:
   - client success
   - RM replacement/state transfer
   - Sequencer replay-ready log

## 9) Assumptions

- `SETUP.md` remains unchanged.
- Demo uses RM-owned replica lifecycle (no manual `ReplicaLauncher` terminals).
- Demo runs on one localhost machine.
- Goal is presentation reliability, not exhaustive protocol validation.
