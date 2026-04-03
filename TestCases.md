# Test Cases — FT-DVRMS

COMP 6231 — Distributed System Design, Winter 2026, Concordia University

---

## Integration Test Cases (`ReplicationIntegrationTest.java`)

Tests run in order. Each test depends on the system state left by previous tests.
Full system is started once in `@BeforeAll`: Sequencer + 4 RMs + 4 Replicas + FE (SOAP :8080).

| Test ID | Category | Description | Input / Steps | Expected Output |
|---------|----------|-------------|---------------|-----------------|
| T1 | Normal | Vehicle CRUD — add, list, remove | `addVehicle("MTLM1111", "QC-NEW-9099", "Sedan", "MTL9099", 99.0)` → `listAvailableVehicle("MTLM1111")` → `removeVehicle("MTLM1111", "MTL9099")` | Add: `SUCCESS: Vehicle MTL9099...`; List contains `MTL9099`; Remove: `SUCCESS: Vehicle MTL9099 removed.` |
| T2 | Normal | Local reservation + cancel with budget deduct/refund | `reserveVehicle("MTLU2222", "MTL1001", "01012030", "02012030")` → `cancelReservation("MTLU2222", "MTL1001")` | Reserve: `SUCCESS: Reserved MTL1001 ... Remaining budget`; Cancel: `SUCCESS: Reservation cancelled. New budget: 1000` |
| T3 | Normal | Cross-office reserve, limit enforcement (max 1 per remote office), findVehicle routing, listCustomerReservations routing | `reserveVehicle MTLU3333 WPG1001` → `reserveVehicle MTLU3333 WPG1002` → cancel WPG1001 → retry WPG1002 → `findVehicle("MTLU3333", "Sedan")` → `listCustomerReservations("MTLU3333")` | 2nd WPG: `FAIL: Already have 1 reservation at WPG`; retry after cancel: `SUCCESS`; findVehicle returns MTL sedans only; listCustomerReservations returns home-office reservations only |
| T4 | Normal | 2 concurrent clients reserve different vehicles simultaneously | 2 threads released by `CountDownLatch` at same instant, each reserving a different vehicle | Both threads: `SUCCESS` |
| T5 | Normal | Waitlist add and auto-promotion when existing reservation is cancelled | `reserveVehicle MTLU5001 MTL1002` → `addToWaitList MTLU5002 MTL1002` → `cancelReservation MTLU5001 MTL1002` | Waitlist: `SUCCESS: Added to waitlist for MTL1002`; Cancel response contains `auto-assigned from waitlist` |
| T6 | Byzantine | 1st Byzantine mismatch from R3 — FE returns correct result using R1/R2/R4 majority | `SET_BYZANTINE:true` → UDP port 6003 → `listAvailableVehicle("MTLM1111")` | Non-null, does not start with `FAIL` |
| T7 | Byzantine | 2nd Byzantine mismatch from R3 — majority vote still correct | `listAvailableVehicle("MTLM1111")` (R3 still Byzantine) | Non-null, does not start with `FAIL` |
| T8 | Byzantine | 3rd mismatch triggers `REPLACE_REQUEST`; replacement completes; system healthy after | `listAvailableVehicle` ×3 → wait 8 s → `listAvailableVehicle` | Requests during replacement: non-FAIL; post-replacement: non-null and non-FAIL |
| T9 | Byzantine | Replaced R3 is a fresh replica that participates in reads and writes | `listAvailableVehicle` → `addVehicle("MTLM1111", ..., "MTL9009", 50.0)` → `removeVehicle("MTLM1111", "MTL9009")` | All three calls: `SUCCESS` |
| T10 | Byzantine | Byzantine counter resets when R3 returns correct results again | `SET_BYZANTINE:false` → UDP port 6003 → `listAvailableVehicle("MTLM1111")` | Non-FAIL; byzantineCount reset to 0 |
| T11 | Crash | FE tolerates 1 crashed replica — R2 killed directly via RM | `replicaManagers.get(1).killReplica()` → `listAvailableVehicle("MTLM1111")` | Non-null, non-FAIL (R1/R3/R4 majority satisfies f+1=2) |
| T12 | Crash | FE sends `CRASH_SUSPECT`; RMs vote; RM2 replaces R2; system healthy after recovery | `listAvailableVehicle` → wait 8 s → `listAvailableVehicle` | During recovery: non-FAIL; post-recovery: non-null and non-FAIL |
| T13 | Crash | Recovered R2 received correct state snapshot and participates in writes | `listAvailableVehicle` → `addVehicle("MTLM1111", ..., "MTL1309", 70.0)` → `removeVehicle("MTLM1111", "MTL1309")` | All three calls: `SUCCESS` |
| T14 | Crash | Normal reserve/cancel operations work correctly after crash recovery | `reserveVehicle("MTLU1400", "MTL3001", "01012030", "02012030")` → `cancelReservation("MTLU1400", "MTL3001")` | Both: `SUCCESS` |
| T15 | Simultaneous | Crash(R2) + Byzantine(R3) injected at same time — R1/R4 provide f+1=2 majority | `SET_BYZANTINE:true` on R3 + `killReplica` R2 → `listAvailableVehicle` | Non-null, non-FAIL |
| T16 | Simultaneous | Both R2 and R3 undergo independent parallel recovery workflows | `listAvailableVehicle` ×2 → wait 10 s → `listAvailableVehicle` | Post dual-recovery: non-FAIL |
| T17 | Simultaneous | All 4 replicas operational with consistent state after dual recovery | `listAvailableVehicle` → `reserveVehicle("MTLU1700", "BNF1001", ...)` → `cancelReservation` | All: `SUCCESS` |
| T18 | Edge Case | `ReliableUDPSender` retransmit path: add and remove complete successfully | `addVehicle("MTLM1111", "QC-RET-1800", "Sedan", "MTL1800", 60.0)` → `removeVehicle("MTLM1111", "MTL1800")` | Both: `SUCCESS` |
| T19 | Edge Case | Total-order execution preserved: 2 rapid sequential adds both visible in list | `addVehicle MTL1901` + `addVehicle MTL1902` fired back-to-back → `listAvailableVehicle` | Both adds: `SUCCESS`; list contains both `MTL1901` and `MTL1902` |
| T20 | Edge Case | 3 concurrent clients each receive a unique seqNum from the Sequencer and succeed | 3 threads released by `CountDownLatch` simultaneously, each adding a different vehicle | All 3: `SUCCESS` |
| T21 | Edge Case | Full cross-office flow: reserve → update reservation dates → cancel | `reserveVehicle("MTLU2100", "WPG2001", "01012030", "02012030")` → `updateReservation("MTLU2100", "WPG2001", "03012030", "04012030")` → `cancelReservation("MTLU2100", "WPG2001")` | All: `SUCCESS` |

---

## Unit Test Cases

### `PortConfigTest.java`

| Test ID | Test Method | Input | Expected Output |
|---------|-------------|-------|-----------------|
| U1-a | `rmAndReplicaPorts` | replicaId = 1 | RM port = 7001, Replica port = 6001 |
| U1-b | `rmAndReplicaPorts` | replicaId = 2 | RM port = 7002, Replica port = 6002 |
| U1-c | `rmAndReplicaPorts` | replicaId = 3 | RM port = 7003, Replica port = 6003 |
| U1-d | `rmAndReplicaPorts` | replicaId = 4 | RM port = 7004, Replica port = 6004 |
| U2-a | `officePortMapping` | replicaId = 1, office = MTL | port = 5001 |
| U2-b | `officePortMapping` | replicaId = 1, office = WPG | port = 5002 |
| U2-c | `officePortMapping` | replicaId = 1, office = BNF | port = 5003 |
| U2-d | `officePortMapping` | replicaId = 2, office = WPG | port = 5012 |
| U2-e | `officePortMapping` | replicaId = 4, office = BNF | port = 5033 |
| U3 | `officePortThrowsOnUnknown` | office = "TORONTO" | `IllegalArgumentException` thrown |

### `UDPMessageTest.java`

| Test ID | Test Method | Input | Expected Output |
|---------|-------------|-------|-----------------|
| U4 | `parseWithFields` | Raw string `"VOTE_BYZANTINE:3:1"` | Type = `VOTE_BYZANTINE`; field[0] = `"3"`; field[1] = `"1"` |
| U5 | `serializeMessage` | `new UDPMessage(REPLACE_REQUEST, "3", "BYZANTINE_THRESHOLD")` | Serialized = `"REPLACE_REQUEST:3:BYZANTINE_THRESHOLD"` |
| U6 | `parseInvalidTypeThrows` | Raw string `"BOGUS:x"` | `IllegalArgumentException` thrown |

### `ReplicaManagerTest.java`

| Test ID | Test Method | Input | Expected Output |
|---------|-------------|-------|-----------------|
| U7-a | `constructorSetsCorrectPorts` | replicaId = 1 | rmPort = 7001, replicaPort = 6001 |
| U7-b | `constructorSetsCorrectPorts` | replicaId = 2 | rmPort = 7002, replicaPort = 6002 |
| U7-c | `constructorSetsCorrectPorts` | replicaId = 3 | rmPort = 7003, replicaPort = 6003 |
| U7-d | `constructorSetsCorrectPorts` | replicaId = 4 | rmPort = 7004, replicaPort = 6004 |

### `ReplicaManagerBehaviorTest.java`

| Test ID | Test Method | Scenario | Expected Outcome |
|---------|-------------|----------|-----------------|
| U8 | `voteCrash_usesReachableMajorityWithinWindow` | RM receives crash votes from majority of peers | Crash vote collected; replacement triggered |
| U9 | `voteByzantine_usesReachableMajorityWithinWindow` | RM receives Byzantine votes from majority of peers | Byzantine vote collected; replacement triggered |
| U10 | `malformedVotes_areIgnoredWithoutTriggeringReplacement` | Malformed vote messages sent to RM | No replacement triggered; no exception |
| U11 | `malformedFaultNotifications_areIgnoredWithoutExceptions` | Malformed fault notification messages sent to RM | Silently ignored; no exception |
| U12 | `crashSuspect_parsesNumericIdAndBroadcastsVote` | FE sends `CRASH_SUSPECT` with numeric replica ID | Vote parsed and broadcast to all peer RMs |
| U13 | `replaceReplica_requestsStateInitializesAndBuildsReadyMessage` | Replacement triggered for a replica | State requested → replica initialized → `REPLICA_READY` broadcast |
| U14 | `feFaultNotification_getsAckFromRmPath` | FE sends fault notification (e.g. `CRASH_SUSPECT`) to RM | RM replies with `ACK:` message back to FE |

### `FrontEndTest.java`

| Test ID | Test Method | Scenario | Expected Outcome |
|---------|-------------|----------|-----------------|
| U15 | `majorityVoting_returnsCorrectResult` | 3 replicas return results; 2 match | Matching result returned to caller |
| U16 | `byzantineCounter_incrementsOnMismatch_resetsOnMatch` | Replica sends mismatch then correct result | Counter increments on mismatch; resets to 0 on match |
| U17 | `byzantineThreshold_triggersReplaceRequest` | Same replica mismatches 3 times in a row | `REPLACE_REQUEST` sent after 3rd mismatch |
| U18 | `crashSuspect_reportedForNonRespondingReplica` | Replica never responds within timeout | `CRASH_SUSPECT` sent to all RMs |

### `SequencerTest.java`

| Test ID | Test Method | Scenario | Expected Outcome |
|---------|-------------|----------|-----------------|
| U19 | `requestHandling_assignsMonotonicallyIncreasingSeqNums` | 3 sequential requests sent to Sequencer | seqNums 0, 1, 2 assigned in order |
| U20 | `nackHandling_replaysHistoryBufferForMissedRange` | Replica sends NACK for gap in seq 0–1 | Sequencer replays messages from historyBuffer |
| U21 | `replicaReady_triggersReplayAndUpdatesReplicaList` | New replica sends `REPLICA_READY:id:lastSeq` | Sequencer adds replica to multicast list; replays from lastSeq+1 |

### `ReplicaLauncherBehaviorTest.java`

| Test ID | Test Method | Scenario | Expected Outcome |
|---------|-------------|----------|-----------------|
| U22 | `gapDelivery_emitsNumericNackAndAck` | Out-of-order EXECUTE received (seq gap) | NACK sent for missing seq; ACK sent for received seq |
| U23 | `extractTargetOffice_routesByOperationShapeAndFallsBackSafely` | Various operation strings tested for office routing | Correct office extracted; unknown ops fall back to default |

### `ReliableUDPSenderTest.java`

| Test ID | Test Method | Scenario | Expected Outcome |
|---------|-------------|----------|-----------------|
| U24 | `ackReceived_returnsTrue` | Server replies with ACK | `send()` returns `true` |
| U25 | `allRetriesExhausted_returnsFalse` | Server replies with non-ACK on all retries | `send()` returns `false` after exhausting retries |

### `VehicleReservationWSTest.java`

| Test ID | Test Method | Scenario | Expected Outcome |
|---------|-------------|----------|-----------------|
| U26 | `byzantineHandleExecute_sendsResultToFe` | Replica in Byzantine mode executes request | Result (possibly wrong) sent back to FE |
| U27 | `executePath_reserveCancel_enforcesHomeOfficeBudgetAndCrossOfficeLimit` | Reserve home vehicle → cross-office limit → budget check | Home: SUCCESS with budget deducted; cross-office limit enforced |
| U28 | `remoteUdpReservePath_remainsBudgetNeutral` | Remote UDP reservation path exercised | Budget unchanged on requester side (remote path) |

### `UDPServerCompatibilityTest.java`

| Test ID | Test Method | Scenario | Expected Outcome |
|---------|-------------|----------|-----------------|
| U29 | `legacyPayload_isHandledWithoutProtocolAckFraming` | Legacy payload without ACK framing sent to UDP server | Handled gracefully; no crash or protocol error |
