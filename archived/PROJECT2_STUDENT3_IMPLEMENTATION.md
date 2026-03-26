# PROJECT 2 STUDENT3 IMPLEMENTATION (SEQUENCER)

## Role and Purpose
Owner: Student 3

This guide defines Student 3 work for Sequencer across:
- Phase 1 baseline responsibilities
- Phase 2 role extension responsibilities

## Alignment Sources
- `Project.6231w26.txt` (failure-free Sequencer, unique sequence numbers, reliable multicast)
- `FT-DVRMS_Project2_Design_v3.3.docx` (total-order and replay behavior)
- `FT-DVRMS_Project2_Design_v3.3_ALIGNMENT_ADDENDUM.md` (message formats and replica ID contracts)
- `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md`

## A3 Guardrail (Mandatory)
- Sequencer controls order/reliability only; it must not alter A3 business semantics.
- Any behavior that can affect operation semantics must be justified with a Necessary-Change Note.

## Entry Condition
Shared baseline gate in `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md` is complete (G1 CLOSED, G2 CLOSED — 2026-03-25).

## Audit Sync Status (Workflow + Group Audit, 2026-03-25)
- Authoritative source: `PROJECT2_GROUP_BASE_AUDIT.md` section 9 (STUDENT 3).
- [x] D1: add `CRASH_SUSPECT` notification to all RMs when `sender.send()` returns `false` in `multicast()`. DONE (`Sequencer.java:93-125`)
- [x] G3 co-owner: Sequencer now sends `ACK:REPLICA_READY:<replicaID>` after registration in `handleReplicaReady()`. DONE (`Sequencer.java:204-217`)
- [x] G4: enable 3 `@Disabled` tests in `SequencerTest.java` with real assertions. DONE (`mvn -q -Dtest=unit.Sequencer.SequencerTest test`, 2026-03-25)
- [x] Mark Student 3 Phase-2 done after remaining open item(s) above are complete. DONE

## Phase 1 Baseline Responsibilities (Sequencer)
Target file: `src/main/java/server/Sequencer.java`

- Receive FE `REQUEST` messages.
- Assign monotonically increasing sequence numbers.
- Reliably multicast `EXECUTE` messages to replicas.
- Maintain history for replay.
- Handle `NACK` and recovered-replica replay (`REPLICA_READY`) paths.

Phase 1 Sequencer done criteria:
Phase 1 Sequencer criteria are complete (see Group Audit section 9).

## Phase 2 Extension Responsibilities (Sequencer)
- Harden ACK tracking and replica address update behavior.
- Explicitly keep `replicaAddresses` synchronized when `REPLICA_READY` updates a recovered replica endpoint.
- Ensure replay targets correct requester only (NACK path).
- Ensure replay targets recovered replica only (`REPLICA_READY` path).
- **G3 APPLIED (co-owner: Student 3):** Sequencer now sends `ACK:REPLICA_READY:<replicaID>` after registering the recovered replica (`Sequencer.java:204-217`). Keep this behavior stable with RM reliable-channel flow.
- **D1 APPLIED (owner: Student 3):** Sequencer now notifies all RMs with `CRASH_SUSPECT:reqID:seqNum:replicaID` when multicast retries are exhausted (`Sequencer.java:93-125`), satisfying design §3.3.
- **G4 FOLLOW-UP (owner: Student 3):** Complete Sequencer unit tests in:
  - `src/test/java/unit/Sequencer/SequencerTest.java` (3 tests were `@Disabled`, now enabled with assertions)

Phase 2 Sequencer done criteria:
- [x] D1: `CRASH_SUSPECT` notification sent to all RMs when `sender.send()` returns `false` in `multicast()` (`Sequencer.java:93`) — design §3.3 explicit requirement
- [x] G3 co-owner: Sequencer sends `ACK:REPLICA_READY:<replicaID>` after registering the recovered replica in `handleReplicaReady()`
- [x] G4: 3 `@Disabled` unit tests in `SequencerTest.java` enabled with passing assertions covering seq assignment, NACK replay, and replica-ready replay
- [x] Sequencer remains non-blocking enough for concurrent request flow (evidence: `mvn -q -Dtest=integration.ReplicationIntegrationTest#t4_concurrentRequests test`, 2026-03-25)

## Cross-Student Handoff (Inputs/Outputs)
Inputs Student 3 needs:
- FE request contract and correlation fields (Student 1)
- RM recovery ready message contract (`REPLICA_READY`) (Student 2)

Outputs Student 3 provides:
- Stable order/replay behavior for Student 4 integration/failure tests
- Stable sequence+replay expectations for FE and RM coordination

## Traceability Checklist
| Item | Source |
|---|---|
| Sequencer assigns unique sequence numbers | Requirement Project 2 Sequencer bullet |
| Reliable multicast to all replicas | Requirement + design protocol |
| Total-order/replay behavior | Design total-order and recovery sections |
| NACK/READY message handling | Addendum canonical message contracts |
