# Vessel BPBC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. User approved direct inline execution; unavailable auxiliary skills do not change the approved scope.

**Goal:** Deliver a readable Java/COPT reproduction of the paper's base BPBC with verified results and a GitHub repository.

**Architecture:** Explicit best-bound tree with nested vessel CG, pilot CG and Benders separation. Small records carry routes, dual cuts and restrictions; pricing is independent of COPT. Every completed node must have a certified full LP bound.

**Tech Stack:** Java 17, COPT 8 Java API, PowerShell build scripts.

**Spec:** ../specs/2026-10-05-vessel-bpbc-design.md

## Global Constraints

- Java 17; COPT 8. No additional runtime dependencies.
- Base BPBC, both pricing procedures and four branching families; no DCG/LBL/VF/WS/PH.
- Own source code; no solver binaries, license files, author source code or paper PDF in Git.
- Fail explicitly on numerical or unsupported states; never label a timeout optimal.
- Publish a standalone repository vessel-bpbc-java-copt after verification.

## Task 1: Data and pricing

Files: src/Instance.java, src/Routes.java, src/Restrictions.java, src/VesselPricing.java, src/PilotPricing.java, test/Verify.java, build.ps1.

Interfaces: Instance.read(Path), Instance.synthetic(int,int), Routes.Vessel, Routes.Pilot; VesselPricing.price(...) and PilotPricing.price(...) return actual column objects.

- [x] Write independent small-instance checks and run build to observe missing-class failure.
- [x] Parse the author's numeric C++ array format, validate dimensions/costs, and generate explicitly synthetic fixtures.
- [x] Implement vessel enumeration and EC.2 bidirectional labels with legal rest joining.
- [x] Compare negative reduced-cost routes with exhaustive enumeration, including shift and arc restrictions.

## Task 2: Certified Benders node LP

Files: src/Master.java, src/PilotProblem.java, src/Bpbc.java, test/Verify.java.

Interfaces: Master.solveCg(), Master.addCut(Routes.Cut), PilotProblem.solve(double[]), Bpbc.solveNode(Restrictions).

- [x] Build resource rows (38)–(41), artificial Phase I, incremental columns and objective-phase CG.
- [x] Build PBSP rows, Phase I, sparse pricing and full-network dual certification.
- [x] Generate feasibility and optimality cuts, then repeat vessel CG to node convergence.
- [x] Enumerate the complete small M2 LP and compare root values; enumerate pilot routes to check each dual inequality.

## Task 3: Integer search, real run and delivery

Files: src/Bpbc.java, src/Main.java, run.ps1, scripts/download-data.ps1, README.md, docs/method.md, results/verification.md, .gitignore, LICENSE.

- [x] Implement inherited pricing restrictions for Λ, Ξ, Π and Ψ and best-bound node order.
- [x] Compare final solutions to independent exhaustive integer search on seeded synthetic fixtures and forced branches.
- [x] Build and run the author's L1-B10-V10-01 with a stated time limit; record exact status, bounds and time.
- [x] Document equation-to-file mapping and minimal commands; inspect tracked files for secrets/binaries.
- [ ] Commit verified files, upload to GitHub, and verify remote contents.

Self-review: every spec requirement maps to the three tasks above. Verification must compare against independent enumeration rather than assert values produced by the same implementation.
