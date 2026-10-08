# Validator profiling

Profiling began with sequential function validation in `ModuleValidator`. The
measurements below track subsequent changes, including queued body validation.
The following harness runs modules sequentially, without Gradle test concurrency. It parses
all inputs before warmup and records only repeated validation, excluding text
conversion, binary parsing, instantiation, and execution. JVM compiler and GC
threads still run normally.

From the repository root (Java 25 used for this measurement):

```sh
./gradlew :app:installDist --no-parallel --max-workers=1
python3 profiling/validator/prepare-corpus.py /tmp/jawawasm-validator-corpus
javac -d /tmp -cp 'app/build/install/app/lib/*' profiling/validator/ValidatorProfile.java
java -Xms512m -Xmx512m -cp '/tmp:app/build/install/app/lib/*' ValidatorProfile /tmp/jawawasm-validator-corpus/corpus.txt /tmp/validator.jfr
jfr print --json --events jdk.ExecutionSample,jdk.ObjectAllocationSample,jdk.GarbageCollection /tmp/validator.jfr > /tmp/validator-events.json
```

The corpus contains ordinary module/module_definition commands from the top-level
spec test scripts, excluding assertion modules. Every selected module must parse
and validate successfully; failures terminate the harness. Additional tests and
proposal subdirectories are not part of this profiling corpus.

## Measurements (2026-10-07)

Local OpenJDK 25.0.2, 512 MiB heap, 2,243 pre-parsed modules, five warmup passes,
30-second JFR recording with 1 ms execution sampling:

| Measurement | Sequential baseline |
| --- | ---: |
| Full corpus passes | 2,687 |
| Average validation time per corpus | 11.166 ms |
| Main-thread CPU samples | 27,081 |
| Sampled allocation weight across JVM | 177.78 GB |
| Garbage collections during recording | 554 |

CPU sample attribution:

* `Context.copy()` appears in 41.62% of main-thread samples.
* Its `other.refs.addAll(refs)` line alone appears in 31.08%.
* `HashMap.resize` accounts for 12.66% of leaf samples and `HashMap.putVal`
  for 11.84%. Copying sets repeatedly rehashes and allocates their entries.
* `TypeUnroll.unroll` appears in 6.10% of samples; `resolveResultType` is
  the leaf in 4.60%. Type expansion recreates records and stream pipelines.
* `ArrayList.addAll` accounts for 5.78% of leaf samples. Context copying
  duplicates module tables, locals, and labels for every function and block.

Allocation sampling attributes 43.86% of weight to HashMap nodes and node arrays,
and 15.49% to Object arrays. `TypeResolver.resolveResultType` appears in allocation
stacks representing 16.74% of weight. These are statistical estimates, not exact
allocation counts. Inclusive stack percentages overlap, and truncated JFR stacks
underattribute outer callers. CPU samples do not directly quantify GC CPU time.

## Recommended changes, in priority order

1. **Share the declared-function reference set after collection.** ReferenceWalker
   finishes collecting references before function validation starts. Functions and
   blocks only query them. A temporary experiment replacing
   `other.refs.addAll(refs)` with `other.refs = refs` completed 4,609 corpus passes
   at **6.510 ms/pass**, about **42% less time / 1.72x throughput**. This experiment
   was reverted. A production implementation should make the read-only phase
   explicit, preventing future mutations from leaking between contexts.
2. **Separate module state from function/block state.** Share types, functions,
   tables, memories, tags, globals, elements, and references after module setup.
   Copy only state whose scope requires isolation. Today F function contexts copy
   an F-entry function table, introducing quadratic work for many-function modules;
   each nested block copies that table again. Keep global initialization ordering
   intact: module state is built incrementally during validation.
3. **Cache expanded composite types per validation invocation.** `requireFuncType`
   and `getCompositeType` expand the same definition repeatedly, as do calls and
   block signatures. Cache by type index in shared module state, after a definition
   is added. Avoid a global cache retaining modules indefinitely. Reprofile after
   context sharing to measure the new relative cost.
4. **Reduce scoped-state copying.** Use a BitSet for initialized-local indices and
   share the local type table within a function. Consider a control-frame stack or
   persistent labels rather than copying label lists and inserting at index zero.
   Preserve branch/if initialization isolation; this requires correctness coverage
   for non-defaultable locals and nested control flow.

No expected gain is assigned to recommendations 2–4 without measurements.
The reference-sharing comparison is one process per variant, on small spec-suite
modules; it is evidence of a useful optimization, not a production benchmark.
Measure larger real-world modules and several fresh JVM forks before claiming a
stable speedup. There is no parallel-vs-sequential timing claim in this report.

## Validation and artifacts

Both app test suites passed with one Gradle test fork and JUnit parallel execution
disabled: 259 class-loader tests plus 259 interpreter tests, zero failures/errors.
The reference-sharing experiment passed validation of all 2,243 corpus modules;
it was not subjected to the full assertion suites and is not retained.

Session recordings are `/tmp/jawawasm-profile/validator.jfr` and
`/tmp/jawawasm-profile/shared-refs.jfr`. The only production edit retained is
sequential function validation. Existing user edits were preserved.

## Follow-up: append-only context lists

Reran the same harness and corpus after introducing AppendOnlyList, removing its
source-side shared flag in clone, and removing the addAll input snapshot. The
CopyOnWriteMap is not used by validation yet. Test module descriptors do not
change this classpath-based benchmark.

Two fresh JVM processes per variant, in current/baseline/current/baseline order:

| Variant | Run 1 (ms/corpus) | Run 2 (ms/corpus) | Mean |
| --- | ---: | ---: | ---: |
| Original ArrayList context | 9.663 | 10.416 | 10.040 |
| Current AppendOnlyList context | 30.087 | 29.412 | 29.750 |

Current validation takes **2.96x as long**, a regression. These fresh baseline
measurements supersede using the historical 11.166 ms measurement for this
comparison. Both variants successfully validate all 2,243 selected modules.

The baseline was reconstructed by compiling the saved original Context.java into
`/tmp/jawawasm-profile/baseline-classes`, prepended to the current installed app's
classpath. Thus both variants use the same remaining classes, dependencies, heap,
and harness; no production files were switched during the comparison.

The cause is capacity growth on every copy-on-write detachment:

```java
int capacity = Math.max(required, elements.length + elements.length / 2);
```

Nested blocks clone their context and immediately append a label. Even when the
array already has ample space, each detached descendant allocates an array 50%
larger than its parent. Capacity therefore grows exponentially with nesting depth,
while the number of labels grows linearly. This is a flaw in the current list
implementation, not an inherent cost of copy-on-write.

In the first current run, `ensureWritable` array copies represent 197.23 GB of the
273.37 GB sampled allocation weight (72%). Estimated allocation per corpus is
about 274 MB versus 66 MB for the fresh baseline. GC events rose from 641 in the
baseline's 3,105 passes to 2,542 in the current run's 998 passes. The second current
run shows similar allocation pressure. Allocation weights remain statistical
estimates; short samples and JIT inlining affect method attribution.

Fix: when detaching shared storage that has enough capacity, retain that capacity.
Apply geometric growth only when required exceeds existing capacity, using
non-overflowing arithmetic. Reprofile after that fix; no timing for a fixed variant
is claimed here. Reference-set copying also remains an optimization opportunity.

Recordings: `/tmp/jawawasm-profile/current-1.jfr`, `current-2.jfr`,
`baseline-1.jfr`, and `baseline-2.jfr`.

## Fix: retain capacity on detachment

`AppendOnlyList.ensureWritable` now retains the existing capacity when detaching
and grows only if the required size exceeds capacity. Growth uses long arithmetic
and caps the result at Integer.MAX_VALUE, preventing integer wraparound. A
regression test exercises 100 successive clone-and-append operations; all 11
engine tests and 518 app tests passed.

Two fresh JVM runs with the same harness produced **11.023** and **10.951 ms/corpus**
(mean **10.987 ms**), versus **29.750 ms** for the faulty version: about **63% less
time**, or **2.71x throughput**. Relative to the fresh ArrayList baseline's
10.040 ms, this is still approximately 9% slower; these two runs do not establish
a speedup over the original implementation.

The first fixed recording sampled 184.09 GB allocation across 2,722 passes,
roughly **68 MB/corpus**, versus roughly 274 MB before the fix and 66 MB for the
baseline. It recorded 574 GC events. Context.copy remains in about 40% of CPU
samples, with hash-set copying still prominent. CopyOnWriteMap remains unused by
validation.

Recordings: `/tmp/jawawasm-profile/fixed-1.jfr` and `fixed-2.jfr`.

## Follow-up: copy-on-write context sets

References and initialized locals now use CopyOnWriteSet; Context.copy clones
both instead of rebuilding HashSets. Two fresh JVM runs of the same 2,243-module,
30-second validation-only workload measured **5.216** and **4.895 ms/corpus**, mean
**5.056 ms**. All selected modules validated successfully in both runs.

| Version | Mean ms/corpus |
| --- | ---: |
| Original ArrayList/HashSet context (earlier two runs) | 10.040 |
| Fixed AppendOnlyList with HashSet (earlier two runs) | 10.987 |
| AppendOnlyList with CopyOnWriteSet (new two runs) | 5.056 |

This is approximately **54% less time / 2.17x throughput** than the immediately
preceding version, and **50% less time / 1.99x throughput** than the original
sequential context. Baselines are the earlier measurements above, not new runs
interleaved with these two; all use the same harness, corpus, JVM and heap settings.
The two new runs differ by about 6%, so interpret small percentage differences
cautiously. No parallel-vs-sequential comparison is implied.

In the first new recording, Context.copy appears in **6.10%** of main-thread CPU
samples, versus about 40% before. TypeUnroll.unroll appears in **11.85%**;
TypeResolver.resolveResultType is the leaf in **8.47%**; Context.addLocalsInit
appears in **8.95%**. Inclusive percentages overlap and are limited by stack
truncation. Repeated type expansion and parameter-local initialization are useful
next profiling targets.

Sampled allocation was 254.60 GB across 5,752 corpus passes, approximately
**44 MB/corpus**, down from about 68 MB before the sets (35% reduction). The run
recorded 794 GC events, approximately 0.138 per corpus versus 0.211 in the first
fixed-list recording. These allocation figures are statistical estimates.

Recordings: `/tmp/jawawasm-profile/cow-set-1.jfr` and `cow-set-2.jfr`.

## Follow-up: cached type expansion

Each validator type-table entry now holds its definition and a lazy cached
CompositeType. Context clones share entries and therefore share expansions.
Function/struct/array kind checks use getCompositeType, so checking a kind and
then fetching the type no longer expands twice. The cache is local to one module
validation; it does not retain modules globally or bypass type-index checks.

All 11 engine tests and 518 app tests passed. Two fresh JVM runs on the unchanged
2,243-module corpus measured **4.128** and **4.151 ms/corpus**, mean **4.140 ms**,
versus the previous **5.056 ms** mean: approximately **18% less time / 1.22x
throughput**. This is approximately **59% less time / 2.43x throughput** than the
original sequential context's 10.040 ms. Comparisons use earlier baseline runs
with the same harness, JVM, corpus, and heap settings.

The first recording sampled 232.83 GB allocation across 7,267 corpus passes,
approximately **32 MB/corpus**, versus 44 MB before caching (about 28% less).
There were 727 GC events. TypeResolver.resolveResultType remains a CPU leaf in
5.26% of samples; type rolling and one-time expansions still require resolution.
Context construction and copying are now more prominent profiling targets.

Recordings: `/tmp/jawawasm-profile/type-cache-1.jfr` and `type-cache-2.jfr`.

## Follow-up: initialized-local prefix and copy-on-write bits

Function parameters are initialized through a prefix count rather than one
LocalIdx/hash-set entry per parameter. Defaultable locals are always readable and
no longer generate initialization entries. Explicit initialization of remaining
locals uses a lazily allocated BitSet, shared across Context copies and cloned
before setting a new bit. Both contexts are marked shared on copy when a bitset
exists, preserving isolation in both mutation directions.

All 11 engine tests and 520 app tests passed. The additional
`local-init-copy-on-write.wast` cases check already-initialized locals across blocks
and reject leakage of another local's initialization out of a block or into an
else branch. Both interpreter and class-loader suites ran the new script.

Two fresh JVM runs of the unchanged 2,243-module profiling corpus measured
**3.974** and **3.839 ms/corpus**, mean **3.907 ms**, compared with the previous
**4.140 ms** mean: approximately **5.6% less time / 1.06x throughput**. This is a
modest improvement based on two runs per version; small differences need more
forks to establish confidence. Compared with the original sequential 10.040 ms,
current validation takes about **61% less time / 2.57x throughput**.

First-run allocation sampling estimated 224.63 GB across 7,550 corpus passes,
approximately **29.8 MB/corpus**, versus 32.0 MB previously (about 7% less). There
were 701 GC events. Context.copy and Context construction remain prominent CPU
leaves (8.16% and 5.32%, respectively). Avoiding construction of collections that
are immediately replaced during copying remains a candidate.

Recordings: `/tmp/jawawasm-profile/local-bits-1.jfr` and `local-bits-2.jfr`.

## Follow-up: direct context copy construction

Context has a copy constructor that initializes its collection fields directly
from clones instead of constructing empty collections and overwriting them.
Collection fields are now final. Cached types and reference storage remain shared;
initialized-local bitsets retain the same copy-on-write behavior.

All 11 engine tests and 520 app tests passed. Two fresh JVM runs measured **3.580**
and **3.721 ms/corpus**, mean **3.651 ms**, versus **3.907 ms** previously: about
**6.5% less time / 1.07x throughput**. As before, these are two runs per version
with earlier comparison measurements; modest timing differences need more forks
for confidence. Current validation takes approximately **64% less time / 2.75x
throughput** than the original sequential context (10.040 ms).

First-run sampled allocation totaled 194.75 GB across 8,379 corpus passes,
approximately **23.2 MB/corpus**, versus 29.8 MB before (about 22% less). The run
recorded 608 GC events. AppendOnlyList.clone is now a prominent CPU leaf (7.32%);
its own construction still creates an empty backing array before replacing it.

Recordings: `/tmp/jawawasm-profile/context-copy-1.jfr` and `context-copy-2.jfr`.

## Follow-up: direct AppendOnlyList copy construction

AppendOnlyList.clone now uses a private copy constructor that shares the backing
array directly, avoiding construction of a throwaway empty array. CopyOnWriteSet
has not received the equivalent constructor change.

Two fresh JVM runs on the same corpus measured **3.398** and **3.214 ms/corpus**,
mean **3.306 ms**, versus the previous **3.651 ms** mean: approximately **9.4% less
time / 1.10x throughput**. Against the original sequential context's 10.040 ms,
current throughput is approximately **3.04x** (67% less validation time).
Comparisons use earlier baseline measurements with the same harness/JVM/heap;
these two new runs differ by about 6%, so small gains need additional interleaved
forks to establish confidence. All 2,243 corpus modules validated successfully.

First-run sampled allocation was 191.01 GB across 8,828 corpus passes, roughly
**21.6 MB/corpus**, versus 23.2 MB previously (about 7% less). There were 596 GC
events. AppendOnlyList.clone remains a CPU leaf in 5.64% of samples, down from
7.32%; clones still allocate list objects. TypeResolver.resolveResultType remains
prominent (5.88% of leaf samples).

Recordings: `/tmp/jawawasm-profile/list-copy-1.jfr` and `list-copy-2.jfr`.

## Follow-up: reuse unchanged immutable types

TypeResolver now returns the original immutable type record when resolution
leaves its children unchanged. List traversal uses loops, retains the original
ImmutableList on an unchanged path, and allocates a builder only at the first
changed element. Changed references rebuild only their containing records and
lists; unchanged siblings are shared. Identity checks avoid structural equality
traversals. This applies to recursive types, subtypes, signatures, aggregate
fields, reference types, table types, and global types.

All 14 engine tests and 520 app tests passed. Three new tests verify identity reuse
for unchanged signatures/aggregates/external references and correct transformation
of recursive references while preserving unchanged siblings.

Unlike earlier follow-ups, this comparison uses interleaved fresh JVM runs of
both variants. The prior TypeResolver.java was compiled into
`/tmp/jawawasm-profile/reuse-baseline-classes` and prepended to the classpath for
baseline runs. All other installed classes/dependencies remain identical.

| Variant | Run 1 ms/corpus | Run 2 ms/corpus | Mean |
| --- | ---: | ---: | ---: |
| Previous resolver | 3.302 | 3.543 | 3.423 |
| Reuse unchanged types | 2.562 | 2.611 | 2.587 |

That is approximately **24% less time / 1.32x throughput** on the unchanged
2,243-module corpus. Every selected module validated in all four runs. JVM/heap,
warmup, and recording settings remain unchanged. Two runs per variant still
provide limited statistical confidence; the baseline variation was about 7%.
Current validation takes approximately **74% less time / 3.88x throughput** than
the historical original sequential baseline (10.040 ms).

First-run allocation sampling estimated 154.27 GB over 11,710 passes, or
**13.2 MB/corpus**, versus 196.73 GB over 9,086 passes (**21.7 MB/corpus**) for the
fresh baseline: about **39% less allocation**. GC events were 482 and 614,
respectively. TypeResolver.resolveList is now a CPU leaf in 3.54% of samples;
Context.copy and operand-stack validation remain prominent targets.

Recordings: `/tmp/jawawasm-profile/reuse-1.jfr`, `reuse-2.jfr`,
`before-reuse-1.jfr`, and `before-reuse-2.jfr`.

## Follow-up: direct CopyOnWriteSet copy construction

CopyOnWriteSet.clone now shares its HashSet through a private copy constructor,
avoiding construction of a throwaway HashSet and its internal HashMap. Both
source and clone remain marked shared to preserve mutation isolation.

Two fresh processes per variant ran in current/previous/current/previous order,
using the unchanged 2,243-module corpus and harness/JVM/heap settings. The previous
set implementation was reconstructed in
`/tmp/jawawasm-profile/set-copy-baseline-classes` and prepended to the classpath;
all other classes and dependencies were identical. All corpus modules validated
successfully in all four runs.

| Variant | Run 1 ms/corpus | Run 2 ms/corpus | Mean |
| --- | ---: | ---: | ---: |
| Previous set clone | 2.477 | 2.467 | 2.472 |
| Copy constructor | 2.424 | 2.479 | 2.452 |

The measured mean is only **0.8% lower**, within the observed run variation.
There is **no convincing timing improvement** from these runs. Comparing only
with the older 2.587 ms historical measurement would overstate the gain.

First-run sampled allocation was 154.30 GB over 12,379 corpus passes, roughly
**12.5 MB/corpus**, versus 162.26 GB over 12,113 passes (**13.4 MB/corpus**) for the
fresh baseline: about **7% less allocation**. GC events per corpus were about
0.039 versus 0.042. These are statistical allocation estimates.

Recordings: `/tmp/jawawasm-profile/set-copy-1.jfr`, `set-copy-2.jfr`,
`before-set-copy-1.jfr`, and `before-set-copy-2.jfr`.

## Final comparison: restore parallel function validation

Parallel function validation is restored using the original parallelStream helper
and validation-exception handling. The lazy expanded-type cache now publishes
through a volatile field and synchronizes the first expansion of each entry,
preventing concurrent functions from racing cache initialization. No further
optimization changes were made. All 14 engine tests and 520 app tests passed.

Two fresh JVMs per variant ran in parallel/sequential/parallel/sequential order.
The sequential ModuleValidator saved immediately before restoration was compiled
into `/tmp/jawawasm-profile/parallel-baseline-classes` and prepended to the baseline
classpath. Both variants therefore use the same optimized collections, resolver,
thread-safe cache, dependencies, and 512 MiB heap. The workload remains 2,243
pre-parsed modules processed sequentially, with only functions within each module
eligible for parallel execution. All selected modules validated in all four runs.

| Function validation | Run 1 ms/corpus | Run 2 ms/corpus | Mean |
| --- | ---: | ---: | ---: |
| Sequential | 2.694 | 2.616 | 2.655 |
| Parallel | 5.955 | 6.292 | 6.124 |

Restored parallel validation takes **2.31x as long** on this corpus. This machine
exposes 32 logical processors; the first parallel recording sampled 31 common-pool
worker threads. ForkJoinPool.signalWork, deactivate, and helpComplete collectively
account for about **31% of leaf CPU samples across main and common-pool workers**.
This supports scheduling/coordination overhead as the explanation: most spec-suite
functions are too small to amortize parallel dispatch. It does not establish the
result for large production modules with substantial independent functions.

First-run sampled allocation per corpus was approximately **15.2 MB parallel**
(76.67 GB over 5,038 passes) versus **12.5 MB sequential** (138.85 GB over 11,138
passes), about 22% higher. Allocation weights are statistical estimates. JFR was
active for both variants; no unprofiled timing claim is made.

Parallel validation remains enabled as requested. The benchmark does not imply
that parallel validation should be removed universally; workload-dependent
thresholds would require separate measurements and were not implemented.

Recordings: `/tmp/jawawasm-profile/parallel-final-1.jfr`, `parallel-final-2.jfr`,
`sequential-final-1.jfr`, and `sequential-final-2.jfr`.

## Follow-up: findAny versus findFirst

The parallel validation helper now uses findAny. It can report any failing
function rather than preserving the earliest failing function in list order.
All 14 engine tests and 520 app tests passed.

Two fresh JVMs per variant ran in findAny/findFirst/findAny/findFirst order.
The saved previous ModuleValidator was compiled into
`/tmp/jawawasm-profile/findany-baseline-classes` and prepended to the baseline
classpath. All other classes, the corpus, and profiling settings were identical.

| Terminal operation | Run 1 ms/corpus | Run 2 ms/corpus | Mean |
| --- | ---: | ---: | ---: |
| findFirst | 5.954 | 6.291 | 6.123 |
| findAny | 6.097 | 6.078 | 6.088 |

The mean differs by about **0.6%**, within run variation: there is **no convincing
speedup** on this valid-module workload. Every function must be checked to prove
there is no validation error; this benchmark does not measure cancellation after
an invalid function is found. All 2,243 selected modules validated in all runs.
findAny remains enabled as requested.

The Java Stream API provides no minimum-batch-size setter. Inspection of the
installed OpenJDK 25 source (java.base/java/util/stream/AbstractTask.java) shows
suggestTargetSize dividing the estimated source size by getLeafTarget, normally
four times pool parallelism, with a minimum target of one. This is an implementation
detail, not an API guarantee. A custom Spliterator can refuse splitting below a
chosen range size; alternatively, stream explicit batches and validate each batch
sequentially. Lowering pool parallelism changes the target indirectly but also
reduces concurrency. No batching change was implemented in this experiment.

API references:
https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Stream.html
https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Spliterator.html

Recordings: `/tmp/jawawasm-profile/findany-1.jfr`, `findany-2.jfr`,
`findfirst-1.jfr`, and `findfirst-2.jfr`.

## Follow-up: sequential validation below 100 functions

parallelFor now directly loops over lists with fewer than 100 items; lists with
100 or more retain parallelStream/findAny. The preceding change passed all 534
tests. This follow-up measures the installed implementation without further code
changes.

Two fresh JVMs per variant ran in threshold/always-parallel/threshold/always-parallel
order. The previous helper (without the size check) was reconstructed in
`/tmp/jawawasm-profile/threshold-baseline-classes` and prepended for baseline runs.
All remaining classes, dependencies, corpus, heap, and JFR settings were identical.
All selected modules validated successfully in all four runs.

| Variant | Run 1 ms/corpus | Run 2 ms/corpus | Mean |
| --- | ---: | ---: | ---: |
| Always parallel with findAny | 5.867 | 5.145 | 5.506 |
| Sequential below 100 functions | 2.762 | 2.684 | 2.723 |

The threshold takes about **51% less time / 2.02x throughput** on this corpus.
The baseline runs varied by about 13%, but both comparisons show a substantial
improvement. This is close to the earlier fully sequential 2.655 ms mean; that
fully sequential comparison was not rerun alongside these measurements.

Corpus counts: **2 of 2,243 modules** have at least 100 defined functions. The corpus
contains 8,764 defined functions total; 609 are in these two parallel-eligible
modules (130 and 479 functions). Thus this result primarily measures avoiding
parallel overhead on small modules. It does not establish that 100 is the optimal
threshold or characterize modules with thousands of substantial functions.

First-run sampled allocation was approximately **12.8 MB/corpus** for the threshold
(139.23 GB across 10,860 passes) versus **15.0 MB/corpus** always parallel
(76.81 GB across 5,113 passes), roughly 15% less. These remain statistical estimates.

Recordings: `/tmp/jawawasm-profile/threshold-1.jfr`, `threshold-2.jfr`,
`always-parallel-1.jfr`, and `always-parallel-2.jfr`.

## Larger real-world modules: Z3 and Argon

Input files under
`/home/john/devel/argon/argon-rust/dist/arch/wasm32-unknown-unknown/`:

| Module | Bytes | Defined functions | Imported functions | Decoded function instructions |
| --- | ---: | ---: | ---: | ---: |
| z3.wasm | 29,408,772 | 36,869 | 8 | 9,153,258 |
| argon_wasm_bg.wasm | 12,523,880 | 9,380 | 142 | 4,009,787 |

The instruction count includes nested function bodies and their control
instructions, excluding binary end markers and non-function initialization
expressions. Function-body validation parallelizes defined functions only.

LargeValidatorProfile parses one module and computes size statistics before six
validation warmup passes. It then records repeated validation for 30 seconds with
JFR profile configuration and 1 ms execution sampling. Tests use OpenJDK 25.0.2,
32 visible logical processors, common-pool parallelism 31, -Xms512m -Xmx4g. Each
module is measured in parallel/sequential/parallel/sequential order with fresh
JVMs. Sequential runs prepend the saved sequential ModuleValidator class in
`/tmp/jawawasm-profile/parallel-baseline-classes`; all other classes are identical.
Both modules exceed the 100-function threshold, so current code takes the
parallel path. Every successfully timed run validated the module repeatedly.

| Module | Sequential run 1 / run 2 ms | Sequential mean ms | Parallel run 1 / run 2 ms | Parallel mean ms | Speedup |
| --- | ---: | ---: | ---: | ---: | ---: |
| z3.wasm | 227.007 / 227.642 | 227.325 | 24.201 / 24.385 | 24.293 | 9.36x |
| argon_wasm_bg.wasm | 76.642 / 76.318 | 76.480 | 27.784 / 27.571 | 27.678 | 2.76x |

Argon's initial parallel validation attempt failed with StackOverflowError in
nested block validation before timing. The measured Argon runs therefore use
**-Xss16m in both modes**. Z3 runs use the default thread stack. Argon reaches
**1,504 nested control levels**. This exposes a remaining recursion-depth
limitation of the validator; increasing the stack was a benchmark setting, not a
production-code fix. The heap settings also differ from earlier small-corpus
benchmarks (4 GiB maximum here versus 512 MiB there), so compare modes within
each module rather than treating all absolute timings as identical conditions.

| Function-size statistic | Z3 | Argon |
| --- | ---: | ---: |
| Mean instructions | 248.3 | 427.5 |
| Median instructions | 108 | 111 |
| 95th percentile instructions | 993 | 1,055 |
| Largest function instructions | 12,993 | 246,473 |

Z3 has about 3.93x as many defined functions and 2.28x as many instructions,
yet its parallel validation is slightly faster. Argon's much larger outlier and
deep nesting are consistent with less evenly distributed work and a longer
critical path. This is an inference from structure and timing, not a measurement
of individual function costs. Function count alone therefore does not predict
parallel speedup. These results support parallel validation for sufficiently
large modules, while the small-corpus results favor sequential validation. They
do not establish an optimal function-count threshold.

Reproduce a parallel run from the repository root:

```sh
./gradlew :app:installDist --no-parallel --max-workers=1
javac -d /tmp -cp 'app/build/install/app/lib/*' profiling/validator/LargeValidatorProfile.java
java -Xms512m -Xmx4g -Xss16m -cp '/tmp:app/build/install/app/lib/*' LargeValidatorProfile /home/john/devel/argon/argon-rust/dist/arch/wasm32-unknown-unknown/argon_wasm_bg.wasm /tmp/argon-validator.jfr
```

Sequential comparison overrides ModuleValidator only, retaining all current
optimizations and concurrent-cache publication semantics. No production validator
behavior or input files were changed for this experiment.

Recordings: `/tmp/jawawasm-profile/z3-{parallel,sequential}-{1,2}.jfr` and
`/tmp/jawawasm-profile/argon_wasm_bg-{parallel,sequential}-{1,2}-stack16m.jfr`.
The initial Argon failure is saved in `argon_wasm_bg-parallel-1.txt`.

## Queue-based nested-body validation

Reviewed and completed the supplied CodeValidatorQueue, and integrated it with
ModuleValidator and InstrValidator. Function bodies and nested block, loop, if,
and try_table bodies are jobs containing their signature and a caller-cloned
context. The queue does not clone contexts. Initial expression checking remains
synchronous; nested bodies it discovers enter the same module queue.

Completion is based on an empty queue **and no active batches**; an empty queue
alone cannot terminate workers because an active job may enqueue more bodies.
Worker failures are propagated, pending work is discarded on failure, and run
waits for all workers before returning. Caller interruption cancels pending work,
waits for workers to finish, and restores the interrupt flag. A queue is drained
once. Modules starting with fewer than 100 jobs use a sequential loop; otherwise
workers are virtual threads, with batches of up to 100 jobs. Child submissions are
collected in each worker's local batch and published together to avoid acquiring
the shared queue lock per nested block.

All **18 engine tests and 520 app tests** passed. New queue integration tests cover
2,000-level nesting in both modes, errors in deferred children, a populated local
initialization bitset that must not expose later parent initialization to a child,
and interruption. The previous spec and additional local-isolation tests also
passed.

Both real-world modules now validate with the **default thread stack**, including
Argon's 1,504-level nesting. Benchmarks retain -Xms512m -Xmx4g and the same
30-second JFR validation-only harness. The final batched version has **one fresh
JVM measurement per module**, compared below with earlier two-run means; timing
comparisons are preliminary, not interleaved fresh baselines.

| Module | Earlier recursive/function-parallel mean ms | Batched queue ms |
| --- | ---: | ---: |
| z3.wasm | 24.293 | 46.054 |
| argon_wasm_bg.wasm | 27.678 | 21.506 |

Argon no longer needs the 16 MiB stack used for its earlier baseline, and its
observed time is about 22% lower. Z3 regresses by about 90%, so queue integration
is not a universal speedup. The initial corrected queue, which locked for every
child submission, measured 131.599/131.784 ms for Z3 and 36.541/32.271 ms for Argon;
worker-local publication substantially reduced that overhead. Queue granularity,
locality, worker startup, and serial preparation of initial function jobs remain
tradeoffs requiring separate measurements.

Final recordings: `/tmp/jawawasm-profile/batched-queue-z3.jfr` and
`batched-queue-argon_wasm_bg.jfr`. Initial queue diagnostics are
`queue-z3-{1,2}.jfr` and `queue-argon_wasm_bg-{1,2}.jfr`.

## Worker-local queues

Parallel workers now keep pending jobs in ConcurrentLinkedDeque instances.
A worker consumes its newest jobs first; an idle worker steals older jobs from
other workers. Child jobs enter these deques directly in add(), so they are
available for stealing while their parent is still running. Initial function
jobs still come from the shared queue in batches of up to 100. The threshold
for sequential execution remains 100 initial jobs, and callers still own
context cloning.

Completion is checked under the coordinator lock after scanning pending work:
there must be no pending jobs and no active workers that can produce children.
Failure and interruption still join all workers; all local queues are cleared
when run finishes. The local execution path avoids the coordinator lock except
when waking waiting workers; concurrent deque operations support stealing.

All **19 engine tests and 520 app tests** passed. A new branching-work test
exercises one substantial root alongside empty functions, repeated draining,
and failure in deferred children.

Fresh JVM validation-only profiles use the same six warmup passes, 30-second
JFR harness, heap settings, and default stack as the preceding queue measurements.
Each result below is one run; comparisons with earlier runs are preliminary.

| Module | Batched shared queue ms | Worker-local queues ms | Reduction |
| --- | ---: | ---: | ---: |
| z3.wasm | 46.054 | 33.371 | 27.5% |
| argon_wasm_bg.wasm | 21.506 | 9.983 | 53.6% |

Z3 remains slower than the earlier recursive/function-parallel mean of 24.293 ms.
Argon improves further and continues to validate without an enlarged stack.
Recordings: `/tmp/jawawasm-profile/local-queue-z3.jfr` and
`/tmp/jawawasm-profile/local-queue-argon_wasm_bg.jfr`.

## Owner-only deques with threshold publication

Replaced concurrent worker deques and stealing with owner-only ArrayDeque
instances. When add() brings the local pending count to 100, the worker publishes
its oldest 50 jobs to the synchronized global queue, retaining newer work for
local processing. Workers refill from the global queue with up to 50 jobs,
leaving space for new children. There is no early publication merely because
other workers are idle. Local deque operations therefore need neither atomic
operations nor per-entry linked nodes; idle workers access only the global queue.
Local jobs remain covered by the active-worker count until the owner drains them.
Caller context cloning, sequential execution below 100 initial jobs, error
propagation, joining, and interruption handling are unchanged.

All **19 engine tests and 520 app tests** passed, including deep nesting,
branching work exceeding the publication threshold, deferred failures, and
context isolation. Fresh JVM timings use the same validation-only JFR harness,
six warmup passes, 30-second recording, heap settings, and default stack.

| Module | Concurrent local deques / stealing ms | Owner-only threshold publication ms |
| --- | ---: | ---: |
| z3.wasm | 33.371 | 32.672 |
| argon_wasm_bg.wasm | 9.983 | 25.392 / 26.179 (mean 25.786) |

Z3 changes by only about 2%, which is too small to conclude improvement from
single non-interleaved runs. Argon's regression persists in a second fresh JVM:
about 2.58x the preceding stealing implementation's time. Reduced local queue
cost does not compensate for the loss of fine-grained sharing on this module.
The results are consistent with worse load balancing when fewer than 100 pending
jobs remain private; they do not directly measure worker utilization or isolate
the effect of changing refill size from 100 to 50. The threshold-only approach is
retained for review; early publication when workers are idle remains a candidate.

Recordings: `/tmp/jawawasm-profile/array-local-queue-z3.jfr`,
`/tmp/jawawasm-profile/array-local-queue-argon_wasm_bg.jfr`, and
`/tmp/jawawasm-profile/array-local-queue-argon_wasm_bg-2.jfr`.

Instruction validators now receive an explicit `Consumer<CodeValidateJob>` job
sink. Parallel workers implement that sink and perform local enqueueing and
threshold publication directly. Sequential and initial expression validators
receive the global queue's add method. This removes the worker ThreadLocal and
queue-owner lookup without changing the scheduling policy. All 539 tests passed;
the timing table above predates this refactor.

## Explicit job sink versus ThreadLocal

Compared the explicit job sink with a rebuilt pre-refactor ThreadLocal version
in temporary classes (CodeValidatorQueue, InstrValidator, and ModuleValidator).
The baseline restores ThreadLocal worker routing and passes the queue directly
to instruction validators; both versions use owner-only ArrayDeque instances,
publish 50 older jobs at 100 pending items, and refill with up to 50 jobs.
Repository sources were not switched for baseline runs.

Each module was measured in ThreadLocal / explicit / explicit / ThreadLocal
order, using a fresh JVM for each run, six warmup passes, and the same 30-second
validation-only JFR harness, -Xms512m -Xmx4g, and default stack.

| Module | ThreadLocal runs ms | ThreadLocal mean ms | Explicit sink runs ms | Explicit mean ms | Mean change |
| --- | ---: | ---: | ---: | ---: | ---: |
| z3.wasm | 30.809 / 31.476 | 31.143 | 33.703 / 30.873 | 32.288 | +3.7% |
| argon_wasm_bg.wasm | 23.887 / 23.907 | 23.897 | 24.063 / 25.064 | 24.564 | +2.8% |

There is no observed speedup: explicit sinks are slightly slower in these runs.
Two runs per variant cannot establish whether these small differences are
reproducible; Z3's run ranges overlap. Removing ThreadLocal lookup simplifies
routing but does not address the much larger threshold-publication regression
relative to concurrent deques with stealing. No scheduling changes were made
for this comparison.

Recordings and stdout are `/tmp/jawawasm-profile/sink-compare-{module}-{mode}-{run}.{jfr,txt}`,
where module is z3 or argon_wasm_bg, mode is threadlocal or explicit, and run is
1 or 2. Baseline sources/classes are in
`/tmp/jawawasm-profile/threadlocal-baseline-{src,classes}`.

## Batch size 20

Reduced BATCH_SIZE from 100 to 20. Local workers now publish their oldest 10
jobs at 20 pending items and refill with up to 10 jobs. The same constant controls
the parallel cutoff, as requested: fewer than 20 initial jobs run sequentially.
Tests now cover 19, 20, and 21 initial jobs for deferred failures and context
isolation, plus deep nesting at the new cutoff.

Compared fresh JVM runs in size 100 / 20 / 20 / 100 order for each module,
with the same explicit sinks, six warmup passes, 30-second validation-only JFR
harness, -Xms512m -Xmx4g, and default stack. A temporary compiled queue preserves
the size-100 baseline. The size-20 benchmark build initially retained the
100-job parallel cutoff; both input modules exceed either cutoff, so changing
it to 20 afterward does not change their validation path. Small-module timing
under the lowered cutoff was not measured in this experiment.

| Module | Size 100 runs ms | Size 100 mean ms | Size 20 runs ms | Size 20 mean ms | Mean change |
| --- | ---: | ---: | ---: | ---: | ---: |
| z3.wasm | 33.239 / 32.399 | 32.819 | 33.343 / 36.499 | 34.921 | +6.4% |
| argon_wasm_bg.wasm | 25.545 / 25.596 | 25.571 | 17.597 / 16.286 | 16.942 | -33.7% |

Argon improves in both runs, consistent with earlier publication helping uneven
workloads. Z3's modest regression is consistent with more frequent shared queue
access, but these timings do not isolate the cause. Size 20 remains slower on
Argon than the earlier concurrent-deque/stealing result of 9.983 ms; that older
result was a single run, not a fresh baseline in this experiment.

Recordings and stdout: `/tmp/jawawasm-profile/batch-compare-{module}-{size}-{run}.{jfr,txt}`,
with module z3 or argon_wasm_bg, size 100 or 20, and run 1 or 2. The size-100
baseline source/classes are in `/tmp/jawawasm-profile/batch100-{src,classes}`.

Final size-20 code with the shared parallel cutoff passed all **539 tests**.

The queue now accepts a single list of initial jobs in its constructor and
exposes no add method. ModuleValidator prepares function jobs with owned
contexts before constructing the function queue. It holds no Worker field:
each expression validation enqueues its root in a fresh queue and drains it
before advancing to the next module definition. CodeValidateJob carries a const
flag; queue workers check constant-expression restrictions before type checking
flagged bodies. Function and nested control-body jobs are not flagged constant. InstrValidator uses a dedicated Worker interface;
parallel workers enqueue locally, and the sequential worker appends directly to
its calling thread's deque. Sequential startup, draining, child enqueueing, and
cleanup acquire no queue monitor. Parallel completion uses the job-thread and
waiter counters; workers are not retained in an array. All 540 tests passed.
