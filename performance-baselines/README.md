# Repeatable performance baselines

Run `./gradlew :performance-baselines:run` on a fixed JDK/runner. The harness warms
each scenario, records 20 samples, and reports median and approximate 95th-percentile
wall-clock latency and per-thread allocation estimates as CSV. Results are deliberately informational: CI does not compare
small timing differences across dissimilar hosts.

Scenarios prefixed `harness.` are collection/allocation calibration and are never product claims.
The production-domain scenarios separately exercise conflict and secure-path policies,
search indexing/query/update, recursive scanning, transfer/sync planning, remote
paging/enumeration, cold/warm thumbnail metadata work, archive open/enumeration/filter,
vault framing/enumeration, and Nearby session transitions. Device-only rendering,
decoding, provider I/O, allocation, and main-thread measurements remain in AndroidX
Benchmark lanes rather than being conflated with these host baselines.

For statistically controlled planner and reducer measurements, run
`./gradlew :performance-baselines:jmh`. JMH performs forked warmup and 20 measurement
iterations and records GC allocation data. Keep results informational until at least 20 runs have
been collected on the same runner class. Android browser binding, thumbnail decoding, and startup
must use an AndroidX Benchmark device lane; host collection stand-ins are not release gates.

For an auditable artifact containing the CSV plus commit, timestamp, OS, Java, and processor
metadata, run `scripts/collect-performance-baselines.sh [output-directory]`. The scheduled
hardening workflow retains this artifact for 30 days and validates its schema and basic invariants;
it intentionally does not compare timings from unrelated GitHub-hosted runners.
