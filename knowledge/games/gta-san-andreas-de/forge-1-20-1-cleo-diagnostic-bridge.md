---
kind: game
title: Forge 1.20.1 diagnostic bridge via CLEO Redux INI mailboxes
game: 'Grand Theft Auto: San Andreas – The Definitive Edition'
games_also: ['Minecraft Java Edition']
game_version: 'Target exe 1.0.113.21181; Minecraft 1.20.1; neither retail game tested'
platform: windows
engine: unreal
route: passthrough
tools: ['CLEO Redux 1.5.1 x64', 'IniFiles', 'candidate Forge 47.2.0', 'Java 17', 'Gradle 8.1.1', 'Node']
anti_cheat: 'Single-player research only; no game runtime inspected'
status: in-progress
agents: ['Codex (GPT-6)']
humans: []
date: '2026-10-17'
links:
- https://github.com/cleolibrary/CLEO-Redux/blob/bbf6773fe8cc1bfa95c73509a25928f97b0d8d13/docs/en/the-definitive-edition-faq.md
- https://github.com/cleolibrary/CLEO-Redux/blob/bbf6773fe8cc1bfa95c73509a25928f97b0d8d13/plugins/IniFiles/dllmain.cpp
- https://github.com/sannybuilder/library/blob/34bedee0be2f57047ed8f0705c81f137ede3d3ce/sa_unreal/sa_unreal.json
tags: [minecraft, forge, cleo-redux, diagnostic-bridge, ini, unverified-runtime]
---
# Forge 1.20.1 diagnostic bridge via CLEO Redux INI mailboxes

A diagnostic candidate exchanges heartbeat, session/sequence ACK and read-only
coordinates between a Forge client and a CLEO script. The Java core and actual
JavaScript source passed synthetic cross-process checks. **Neither game was
available; the Forge adapter did not complete a build. This is not a working
Minecraft-in-SA-DE mod.** Sources, Windows procedure and journal are in
[examples/minecraft-sa-de-bridge](../../../examples/minecraft-sa-de-bridge/README.md).

## Setup

Target: SA DE exe 1.0.113.21181 with CLEO Redux 1.5.1 x64. The user reported
successful game detection and plugin loading; installation source was described
as torrent, so retail store and binary identity are unverified. The official
CLEO support table lists this exe version for Steam. It does not certify another
binary carrying that version string. Exact user's Forge patch remains unknown;
the candidate uses Minecraft 1.20.1, Forge 47.2.0, full JDK17 and Gradle 8.1.1.
Use separate profiles/saves, back them up, and keep existing loaders intact.

## Route and why

Two real games retaining their simulations is a candidate for real inventory,
TNT and later redstone. First isolate loader/IPC: two single-writer local INI
mailboxes, no network sockets, hooks, address patches, command execution or
render injection. Only 2 Hz diagnostic snapshots. Do not use this channel as
an event queue for TNT, block edits or inventory transactions.

The GTA V example targets ScriptHookV/RAGE + Fabric/26.3/Java25. Its native
hashes, camera probes, frame mixins, rendering assumptions and coordinate map
are not compatible merely because both hosts are GTA games. Only architectural
ideas and staged tests were reused.

## How the game works

Documented API facts, not observations of the retail exe:

- CLEO 1.5.1 sources expose IniFile.ReadString(path,section,key) and
  WriteString(value,path,section,key) via the IniFiles plugin and fs permission.
  They call Win32 profile APIs. SDK STR_MAX_LEN is 128; packets are ASCII ≤120
  characters and paths must be short.
- SA Unreal definitions list Player.IsPlaying, GetChar, Char.GetCoordinates
  and Fx.AddExplosion. Their existence in metadata does not validate a particular
  executable. The prototype defaults to heartbeat-only on GTA; CJ reads are a
  separate manual test. It never calls the explosion command.
- Raw coordinates remain in each game's own axes, quantized to tenths. No
  scale, yaw, Unreal-centimeter ratio or GTA V axis transformation is asserted.
- Minecraft reads player state on its client tick; a daemon worker owns file
  I/O. Session/ACK and freshness distinguish an unchanged file from a live peer.

## Build steps

See the example README. On Windows, with full JDK17, run tools/Build-Forge.ps1.
It verifies a pinned official Gradle SHA-256 and builds the candidate adapter;
it never installs anything into a game. Only after a successful current build,
manually copy the jar into a clean Forge profile and the reviewed [fs] script
into the existing CLEO directory. Check heartbeat/ACK first; optional CJ telemetry
second. No retail assets, runtime binaries or compiled game code are included.

## Verification

Cloud: 23 Java core checks passed, including malformed input, ACK session and
sequence checks, stale/regressed packets and restart. An independent Java process
exchanged packets with the actual CLEO JS source running in Node with simulated
IniFile/Player/wait APIs. Bidirectional telemetry, new session after restart and
stale timeout passed. The Node shim does not test Win32 INI cache/locking.

A complete JDK17 and Gradle8.1.1 were downloaded from official distribution
sources and SHA-256 verified. Full Forge build stopped resolving ForgeGradle;
the cloud proxy denied direct access to Maven Forge (HTTP CONNECT 403). No
Forge stubs were used as a substitute. Windows PowerShell helper, CLEO runtime,
both games, camera/renderer, portal, blocks/inventory in GTA and TNT/vehicle effects
are untested. Real success requires matching roundtrip evidence in both actual
game logs, then live CJ/MC coordinate changes and disconnect/restart checks.

## Gotchas

1. **Version number mistaken for identity.** The CLEO support table is store
   specific. Record the user's store/build, exe SHA-256 and runtime log before
   claiming compatibility; no arbitrary offsets or executable patches.
2. **INI strings exceed the SDK buffer.** Keep payload ≤120 ASCII characters,
   one key per snapshot, validate bounds, and avoid long file paths.
3. **Old files look alive.** Freshness must depend on advancing sequence, and
   roundtrip must echo the current sender session. File existence is insufficient.
4. **Synthetic pass mistaken for in-game pass.** Node replaces the profile API;
   Windows rename/sharing/caching may still fail. Keep this a separate required gate.
5. **Cloud JRE contains compiler but no release symbols.** The supplied Java21
   runtime rejected --release 17. A checksum-verified full JDK17 fixed compilation
   of the real standalone core; it did not remove the blocked Forge dependency.

## Open questions

Exact Forge patch and binary provenance; real Windows profile API roundtrip;
CJ getter validity; script units/axes; object and collision limits; camera,
render API and usable depth; rendering choice (native objects, meshes or frame
compositing); safe control handover. Portal, TNT and piston/lever integration
require additional independently verified slices, not extrapolation from IPC.
