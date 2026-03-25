# DVRMS Project 2 — Setup and Run Guide

## Prerequisites

- JDK 8 or higher (`java`, `javac`, `wsgen`, `wsimport` on PATH)
- Apache Maven 3.x

## Build

```bash
mvn clean compile
```

Without Maven, compile manually:

```bash
mkdir -p bin
javac -d bin src/main/java/model/*.java src/main/java/server/*.java
```

Then use `java -cp bin` instead of `java -cp target/classes` in the commands below.

## Run Tests

```bash
mvn test
```

## Start the System (localhost)

Start each component in a **separate terminal**, in this order:

### 1. Sequencer

```bash
java -cp target/classes server.Sequencer
```

### 2. Replicas (one per terminal)

```bash
java -cp target/classes server.ReplicaLauncher 1
java -cp target/classes server.ReplicaLauncher 2
java -cp target/classes server.ReplicaLauncher 3
java -cp target/classes server.ReplicaLauncher 4
```

### 3. Replica Managers (one per terminal)

```bash
java -cp target/classes server.ReplicaManager 1
java -cp target/classes server.ReplicaManager 2
java -cp target/classes server.ReplicaManager 3
java -cp target/classes server.ReplicaManager 4
```

**Note:** RMs launch replicas automatically — skip step 2 if using RMs.

### 4. Front End

```bash
java -cp target/classes server.FrontEnd
```

SOAP endpoint: `http://localhost:8080/fe?wsdl`

### 5. Clients

```bash
./build-client.sh --wsdl http://localhost:8080/fe?wsdl
java -cp bin client.CustomerClient
java -cp bin client.ManagerClient
```

## A3 Standalone Mode (dev/debug)

To test business logic without the replication layer:

```bash
./start-server.sh                                        # starts MTL:8081, WPG:8082, BNF:8083
./build-client.sh --wsdl http://localhost:8081/mtl?wsdl   # in another terminal
java -cp bin client.CustomerClient
```

## Failure Scenarios

**Prerequisite:** Full system running (Sequencer → 4 RMs → FE) before each scenario.

### Scenario 1: Byzantine Fault (T6–T10)

A Byzantine replica returns incorrect results. The FE detects via voting.

```bash
# 1. Enable Byzantine mode on Replica 3
echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003

# 2. Send 3+ SOAP requests via client
java -cp bin client.CustomerClient
#    → FE receives 3 correct + 1 incorrect result per request
#    → FE increments byzantineCount for Replica 3

# 3. After 3 consecutive faults, FE sends REPLACE_REQUEST to RMs
#    → RM 3 kills Replica 3, launches a new one
#    → New replica receives state transfer from a healthy replica
#    → byzantineCount resets to 0

# 4. Disable (if testing manually without replacement)
echo -n "SET_BYZANTINE:false" | nc -u -w1 localhost 6003
```

### Scenario 2: Crash Fault (T11–T14)

A crashed replica stops responding entirely.

```bash
# 1. Kill Replica 2
kill $(lsof -ti udp:6002)

# 2. Send SOAP requests
#    → FE times out waiting for Replica 2 (timeout = 2x slowest response)
#    → FE sends CRASH_SUSPECT to all RMs
#    → RMs verify via heartbeat, reach majority consensus
#    → RM 2 launches a new replica, performs state transfer
#    → Sequencer replays missed messages to new replica
```

### Scenario 3: Simultaneous Byzantine + Crash (T15–T17)

One replica crashes, another is Byzantine — the system still returns correct results.

```bash
# 1. Kill Replica 2
kill $(lsof -ti udp:6002)

# 2. Enable Byzantine on Replica 3
echo -n "SET_BYZANTINE:true" | nc -u -w1 localhost 6003

# 3. Send SOAP requests
#    → 3 replicas respond: R1 (correct), R3 (Byzantine), R4 (correct)
#    → FE gets f+1 = 2 matching results (R1 + R4), returns correct answer
#    → Both faults detected and handled independently
```

### Test Scenario Summary

| Test | How to trigger |
|---|---|
| T1–T5 (Normal) | Send SOAP requests via client |
| T6–T10 (Byzantine) | `SET_BYZANTINE:true` on target replica, send requests |
| T11–T14 (Crash) | Kill replica process, send requests |
| T15–T17 (Simultaneous) | Kill one replica + Byzantine another, send requests |
| T18–T21 (Edge cases) | Concurrent clients, cross-office operations |

## Port Assignment

| Component | Port | Protocol |
|---|---|---|
| Sequencer | 9100 | UDP |
| FE (SOAP) | 8080 | TCP |
| FE (results) | 9000 | UDP |
| Replica 1 / RM 1 | 6001 / 7001 | UDP |
| Replica 2 / RM 2 | 6002 / 7002 | UDP |
| Replica 3 / RM 3 | 6003 / 7003 | UDP |
| Replica 4 / RM 4 | 6004 / 7004 | UDP |

Per-replica office ports (inter-office UDP):

| Replica | MTL | WPG | BNF |
|---|---|---|---|
| 1 | 5001 | 5002 | 5003 |
| 2 | 5011 | 5012 | 5013 |
| 3 | 5021 | 5022 | 5023 |
| 4 | 5031 | 5032 | 5033 |

## Troubleshooting

**Port already in use:**

```bash
lsof -i :6001                          # check who holds the port
pkill -f "server.ReplicaLauncher|server.Sequencer|server.FrontEnd|server.ReplicaManager"
```

**WSDL not reachable:** Make sure FE is running first. `build-client.sh` auto-waits up to 30s.

**UDP bind errors in tests:** The `Address already in use` messages during `mvn test` are expected and harmless — test stubs pass regardless.
