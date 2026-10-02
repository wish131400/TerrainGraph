# TerrainGraph 0.1.3 for NeoForge 1.21.1

TerrainGraph reuses repeated density graph conversions while initializing a
noise chunk. Both the initial NoiseRouter conversion and the final-density
conversion are supported.

## Installation

Requires Minecraft **1.21.1**, NeoForge **21.1.232 or later**, and Java **21 or later**.
Use `terraingraph-1.21.1-neoforge-0.1.3.jar` from `build/libs`.

Dedicated servers install it in the server's `mods` directory. Connecting clients
do not need the mod. For single-player, install it in the client's `mods` directory.
This JAR is not compatible with Minecraft 1.20.1, Forge, or Fabric.

## Configuration

The first launch creates `config/terraingraph-common.toml`:

```toml
enabled = true
reuseFinalDensity = true
maxNodesPerTraversal = 8192
collectMetrics = false
```

- `enabled` enables both conversion reuse passes.
- `reuseFinalDensity = false` retains only the first pass.
- `maxNodesPerTraversal` bounds each independent identity cache.
- `collectMetrics` enables `/terraingraph stats`; leave it off for normal use.

Restart after changing these settings. `-Dterraingraph.disabled=true` forces both
passes off. Removing the mod does not require converting the save.

## Compatibility

The second pass recognizes the native constructor call and the verified
NeoForge ByePregen 1.1.2.4 expression capture. Its scalar verification and DFC
binding remain in place. With ByePregen DFC disabled, the native entry is used.
Ambiguous calls, changed source factories, changed visitors, and unknown result
handlers retain their existing second-pass conversion.

Each cache belongs to one traversal. No cache is shared across chunks, worlds,
dimensions, or threads. Visitors with additional interfaces retain their identity
and skip memoization. No terrain formula, teleport scheduling, or network code is
modified. C2ME DFC integration has not been tested in this port.

## Build And Verification

Run `./scripts/build-terraingraph-neoforge.ps1` from the repository root,
or run `gradlew test build` here
with Java 21. Runtime verification uses an isolated NeoForge server and the
unchanged production JAR, not the development classpath.

`-Dterraingraph.verify=true` exposes the opt-in `/terraingraph verify` command.
It compares optimized and original height queries, complete block columns,
noise-filled chunks, and noise biomes in memory. It does not replay structures,
surface generation, carving, decoration, lighting, or old-world blending.

Port validation is recorded in `reports/terraingraph-neoforge-2026-10-02`.
The previous Forge 1.20.1 performance percentages have not been measured on
Minecraft 1.21.1 and should not be treated as this version's expected gain.
