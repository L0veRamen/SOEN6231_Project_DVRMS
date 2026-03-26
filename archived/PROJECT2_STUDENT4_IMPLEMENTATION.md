# PROJECT 2 STUDENT4 IMPLEMENTATION (INTEGRATION TESTS)

## Role and Purpose
Owner: Student 4

This guide defines Student 4 work for integration testing and test client behavior across:
- Phase 1 baseline responsibilities
- Phase 2 role extension responsibilities

## Alignment Sources
- `Project.6231w26.txt` (Student 4: proper test cases for all failure situations)
- `FT-DVRMS_Project2_Design_v3.3.docx` (test scenario oracle and failure simulations)
- `FT-DVRMS_Project2_Design_v3.3_ALIGNMENT_ADDENDUM.md` (canonical contracts used in tests)
- `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md`

## A3 Guardrail (Mandatory)
- Integration tests validate replication and recovery around A3 business logic.
- Test implementation must not redefine business rules; it verifies expected A3-compatible behavior.
- Any expected behavior that differs from A3 must include a Necessary-Change Note.

## Entry Condition
Shared baseline gate in `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md` is complete (G1 CLOSED, G2 CLOSED — 2026-03-25).

## Audit Sync Status (Workflow + Group Audit, 2026-03-25)
- Authoritative source: `PROJECT2_GROUP_BASE_AUDIT.md` section 9 (STUDENT 4).
- [x] G4 Phase-2 priority order: T2, T3, T6-T10, T11-T14, T15.
- [x] Mark Student 4 Phase-2 done. DONE
- Current verification snapshot: `t1`-`t21` are assertion-based tests in `src/test/java/integration/ReplicationIntegrationTest.java`.

## Phase 1 Baseline Responsibilities (Integration)
Target file: `src/test/java/integration/ReplicationIntegrationTest.java`

- Maintain T1-T21 scaffold with correct scenario names and ordering.
- ~~Ensure startup strategy is consistent with recovery testing (no duplicate direct replica + RM launch in same run).~~ DONE — direct replica launch removed; RMs launch replicas via ProcessBuilder; `rm.stop()` in `@AfterAll` (`ReplicationIntegrationTest.java`). (G2 CLOSED)
- Keep at least one end-to-end asserted flow valid through FE->Sequencer->Replica->FE path.

Phase 1 Integration done criteria:
Phase 1 integration criteria are complete (see Group Audit section 9).

## Phase 2 Extension Responsibilities (Integration)
- Implement full asserted T1-T21 behaviors.
- Add Byzantine and crash simulation helpers for failure scenarios.
- Verify state consistency after replacements/recovery.
- Confirm both normal and failure paths against expected outcomes.
- **G4 FOLLOW-UP (owner: Student 4):** Convert T2-T21 TODO stubs to asserted behavior cases. See `PROJECT2_SHARED_CONFIG_IMPLEMENTATION.md §6 G4`. Priority test scenarios per design §5:
  - T2 `t2_localReservation`: reserve + verify budget deducted — **validates NCN-RM-1** (budget enforcement in `*_LOCAL` path)
  - T3 `t3_crossOfficeReservation`: reserve remote-office vehicle + verify cross-office limit — **validates NCN-FE-1** (single-office routing parity)
  - T6-T10: Byzantine detection, 3-strike REPLACE_REQUEST, replica replacement, state transfer, post-recovery correctness
  - T11-T14: Crash detection via heartbeat, replacement, state transfer, post-recovery operation
  - T15 (core scenario): simultaneous 1 crash + 1 Byzantine — FE returns correct result from 2 matching

Phase 2 Integration done criteria:
- [x] T1-T21 contain real assertions (not TODO-only bodies)
- [x] Failure simulation paths are deterministic enough for reproducible runs
- [x] Phase 2 test scope (normal + failure paths) is complete for Student 4 closeout

## Cross-Student Handoff (Inputs/Outputs)
Inputs Student 4 needs:
- FE final behavior and response contract (Student 1)
- RM replacement and state-transfer behavior (Student 2)
- Sequencer replay/order behavior (Student 3)

Outputs Student 4 provides:
- End-to-end validation evidence for group sign-off
- Reproducible failure scenario coverage for demo readiness

## Traceability Checklist
| Item | Source |
|---|---|
| Student 4 owns all failure-scenario tests | Requirement Project 2 Student 4 bullet |
| Normal and failure scenario definitions | Design test scenario sections |
| Crash/Byzantine simulation approach | Requirement + design failure simulation notes |
| Protocol-consistent assertions | Addendum canonical contracts |
