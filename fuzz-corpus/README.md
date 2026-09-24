# Reproducible hostile-input corpus

Text seeds are one case per line; binary seeds use lowercase hexadecimal. The JVM and
instrumented fuzz tests use fixed RNG seeds and these named cases are the minimized
regressions retained after review. Run `./gradlew :core-files-api:test
:app:testPureDebugUnitTest` for host policies and `:app:connectedDebugAndroidTest` for
JNI boundaries.

Run `scripts/replay-hostile-corpus.sh` to replay every checked-in host seed with bounded wall-clock
budgets. A new text or hexadecimal seed is picked up automatically; malformed corpus encoding is a
test failure. Minimize a failure by removing bytes while the replay command still fails, preserve the
smallest reproducer here, and record the parser fix in a deterministic unit test. Android JNI seeds
are replayed by the connected hostile-input tests; host ASan/UBSan builds remain library-specific
because the vendored Android libraries do not all expose portable host build targets.
