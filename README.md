# TerrainGraph

An independent Minecraft 1.20.1 Forge mod that reduces repeated density graph
conversion during `NoiseChunk` initialization. Java 17 and Forge 47.x are required

## Installation

Put `terraingraph-1.20.1-forge-0.1.3.jar` in the server's `mods` directory and
restart. Dedicated server clients do not need this mod. For single-player,
install it in the client's `mods` directory to optimize its integrated server.
Other Minecraft versions and Fabric are not supported by this build.

## Scope

Structure placement repeatedly calls `getBaseHeight` and `getBaseColumn`. Each
query can build a noise context and recursively transform the same density
subgraphs. TerrainGraph remembers already transformed source nodes during one
`NoiseRouter.mapAll` traversal in the `NoiseChunk` constructor. It also memoizes
the second final-density traversal using a separate cache, through the vanilla
entry or supported ByePregen 1.1.2.4 redirect.

The original node implementations and wrapping visitor run for the first visit.
The original wrapping map still uses structural equality. Cached results are
only reused for the same source object during that traversal. Mutable noise
contexts are never shared between chunks, threads, dimensions, or worlds.
New nodes beyond the configured bound follow the original conversion path.

This version does not change teleport scheduling, structure selection, terrain
formulas, or Minecraft's networking. It does not overwrite other mods' node
implementations. Only known vanilla concrete
density node classes are memoized; custom nodes continue running their own code.

## Compatibility

TerrainGraph preserves visitors with additional interfaces and skips memoization
for that traversal. This keeps marker interfaces visible to custom density nodes,
including C2ME DFC's blending-aware visitor contract. The guard needs no C2ME
dependency and does not disable C2ME. If another mod wraps TerrainGraph's visitor
on the outside, memoization hooks do not recognize that outer visitor as ours.

The second traversal is optional and does not require ByePregen. After mixins
have applied, TerrainGraph checks which constructor entry remains:

- The unchanged vanilla `cacheAllInCell(...).mapAll(this::wrap)` entry is
  supported with or without ByePregen, including when its DFC is disabled.
- A redirect from ByePregen exactly 1.1.2.4 is supported after checking its
  origin, signature, constructor caller, and initial mapping instructions.
  Its verification root, interpolator capture, and DFC binding are preserved.
- Unknown redirects, altered visitor injections, changed source factories, or
  ambiguous constructor calls keep their existing second traversal.

Only one entry is wrapped. No modified ByePregen JAR is required.
The first-traversal optimization remains available when the second is skipped.
Startup logs and `/terraingraph stats` report whether the second hook was attached.

Tests cover the current ByePregen/ModernFix/ZFastNoise modpack and visitor marker
preservation. Startup, terrain equivalence, and performance with a matching C2ME
DFC build have not been verified. This JAR remains Forge 1.20.1 only; it cannot
be installed on NeoForge 1.21.1.

## Configuration

The first launch creates `config/terraingraph-common.toml`:

```toml
enabled = true
reuseFinalDensity = true
maxNodesPerTraversal = 8192
collectMetrics = false
```

- `enabled`: enables conversion reuse. Restart after changing it.
- `reuseFinalDensity`: enables the vanilla or supported ByePregen second traversal. Set
  false to retain only the first-traversal optimization. Restart after changing it.
- `maxNodesPerTraversal`: bounds cached source nodes, from 64 to 65536.
- `collectMetrics`: enables counters for `/terraingraph stats`; leave it off
  during normal play. Counters are cumulative for the current process.

`traversals` counts both traversals; `finalTraversals` counts the second ones.
`finalSpecialVisitors` counts second traversals skipped to preserve visitor
interfaces. `reused` and `converted` count recognized node lookup hits and misses,
not elapsed time, unique nodes, or every custom density node.

The startup argument `-Dterraingraph.disabled=true` forces the original traversal
path for troubleshooting. Removing the JAR removes all TerrainGraph injections.
TerrainGraph writes no persistent cache or custom world data. The diagnostic
original path disables both traversals.

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat test build
```

The reobfuscated JAR is written to `build/libs/`. Dependency downloads require
network access on the first build.

## Verification

Version 0.1.3 adds the vanilla entry. Java 17 runtime checks cover vanilla,
vanilla with the second traversal disabled, and ByePregen DFC on and off.
The earlier 0.1.2 measurements below apply to the ByePregen DFC environment;
they do not establish performance gains without ByePregen.

The 0.1.2 production JAR was measured with stock ByePregen 1.1.2.4 and Zeta JARs.
Across four runs of 192 requested FULL chunks, the second traversal reduced mean
generation time from 23.81 to 17.37 seconds (27.1%), process CPU by 30.6%, and
measured thread allocation by 64.0%, relative to the first-traversal optimization
alone. These figures apply to the tested modpack, seed, hardware, and route.
Full generation in this modpack is not identical between
JVM launches even with unchanged optimization settings.

The benchmark harness used during development restores an isolated world for
each JVM launch and compares an uninstalled baseline against an installed
variant in ABBA order. The published branch contains the mod source and build
files; generated benchmark data is kept outside the release source tree.

For developer diagnostics only, start with `-Dterraingraph.verify=true` to expose
`/terraingraph verify`. It compares every heightmap type and complete block
columns through both paths at twelve coordinates in each noise-generated
dimension, on a background worker. It also compares in-memory noise-filled
chunks and their biomes for dimensions using the base noise generator. That
comparison has no structure beardifier,
carving, surface pass, decoration, or saved chunks. It is not a complete
third-party dimension pipeline test. Custom generator subclasses are checked
for height and column queries, with their noise filling marked as unverified.
Diagnostic generator accessors are absent unless the
verification startup flag is enabled. `-Dterraingraph.verify.report=<absolute path>`
writes the comparison report. The command is absent from normal installations.

Successful sampling does not establish compatibility with every third-party
density function implementation or world-generation mod.
