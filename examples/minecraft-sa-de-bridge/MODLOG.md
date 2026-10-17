# Minecraft Forge 1.20.1 × San Andreas DE — MODLOG

## 2026-10-17 — scope and route before implementation

Requested host: GTA San Andreas Definitive Edition, exe 1.0.113.21181,
CLEO Redux 1.5.1 x64 (game detection and plugin loading reported by user).
Guest: Minecraft Java 1.20.1, Forge 47.x; exact installed Forge patch unknown.
User later described the host installation source as “torrent”. Retail store and
unmodified build identity are unverified; the Steam support-table row does not
establish compatibility of that binary. No binary-specific patches are proposed.
Cloud has neither retail game nor access to the user's Windows machine.
Goal: CJ, portal hand-over, real Minecraft blocks/inventory in GTA, TNT affecting
cars; pistons and levers later. This iteration is research + a diagnostic bridge,
not a playable mashup. No installs into game folders, input automation or publication.

Read AGENTS.md, skills/mod-any-game/SKILL.md, skills/mashup-mods/SKILL.md,
Minecraft engine playbook, bridge contract note, GTA V passthrough README and
its HostLink.java/script.cpp/build files. `um kb search` queries: San Andreas,
CLEO Redux, Minecraft GTA, Definitive Edition. The search is token-based: the
San Andreas hits were unrelated Bannerlord notes, not evidence of SA support.
No matching CLEO Redux/SA DE bridge note found. Relevant prior art: GTA V,
BeamNG, Portal 2 and bridge ownership/lifecycle notes.

Decision: first a low-rate, two-file, single-writer INI mailbox. Reuse existing
CLEO IniFiles plugin; no new DLL, memory offsets, native hooks, sockets or
render injection. Forge client owns its own world; GTA owns CJ. Exchange only
heartbeat/ACK and optional read-only telemetry. Reason: isolate transport and
loader compatibility before camera/physics/rendering risks. This is a diagnostic
transport; it is unsuitable for frame/camera streaming and queued TNT events.
See docs/CONTRACT.md for the exact protocol and lifecycle.

## Sources inspected

- CLEO Redux tag 1.5.1, commit bbf6773fe8cc1bfa95c73509a25928f97b0d8d13:
  `docs/en/the-definitive-edition-faq.md` explicitly lists Steam SA DE exe
  1.0.113.21181, not that build for RGL/Epic. Store not yet confirmed by user.
  `docs/en/definitions.md`, `script-lifecycle.md`, `permissions.md`, `api.md`;
  `plugins/IniFiles/dllmain.cpp` implements ReadString/WriteString using Win32
  profile APIs and requires fs permission. SDK STR_MAX_LEN is 128, so wire
  packets are ASCII and at most 120 characters.
- Sanny Builder library HEAD 34bedee0be2f57047ed8f0705c81f137ede3d3ce:
  sa_unreal.json has IS_PLAYER_PLAYING (0256), GET_PLAYER_CHAR (01F5),
  GET_CHAR_COORDINATES (00A0), GET_CHAR_HEADING (0172), ADD_EXPLOSION (020C).
  unknown_x64.json defines IniFile.ReadString(path,section,key) and
  IniFile.WriteString(value,path,section,key). Presence in definitions is
  API evidence, not proof those commands work on this user's executable.
- MinecraftForge 1.20.1 branch HEAD d13dd327bdc75c010f3358992e80e7e92f51e030:
  TickEvent.ClientTickEvent, MDK @Mod/EventBusSubscriber examples. Candidate
  build baseline 1.20.1-47.2.0 with Java 17, not a claim about user's patch.
- Official documentation site re.cleo.li and Maven maven.minecraftforge.net
  returned HTTP 403 through this cloud's egress proxy (Forge CONNECT denied).
  Read CLEO docs from official GitHub instead. Forge dependency download/build
  remains blocked here; do not call Forge adapter compiled or in-game tested.

## GTA V example compatibility audit

The example targets GTA V Legacy build 3889 + ScriptHookV 3889.0 + ReShade 6.8,
Minecraft 26.3/Fabric/Java 25. Target here is SA DE/CLEO x64 + 1.20.1/Forge/Java17.
ScriptHookV natives/hashes, RAGE entity handles and camera/ground probes are
host-specific. The Fabric callbacks, mixins and render readback are guest-version
specific. None are copied as a purported working SA DE implementation. The ideas
of two running simulations, loopback IPC, separate control/frame channels and
staged synthetic oracles are reusable. Axes, scale, reversed-Z assumptions,
render API and any ReShade depth access require measurement in SA DE.

## Validation

Pending implementation and actual commands/results below. Windows/retail-game
checks are explicitly unrun until logs from the user's PC exist.

## Implementation and observed validation results

Created `cleo/umsa-bridge[fs].js`, independent Java `BridgeCore`, candidate
Forge client adapter `SaBridge`, Gradle project, Windows build helper, source
and cross-process test harnesses. Default GTA side is heartbeat-only; optional
CJ getter is a separate explicit Stage B. No position setters, commands,
explosions, portal, world changes or rendering are implemented.

Cloud toolchain diagnosis: the provided Java21 runtime contains jdk.compiler
but lacks release-17 symbols (`release version 17 not supported`). Downloaded
full Temurin JDK17.0.16+8 from official adoptium/temurin17-binaries release and
verified its published SHA-256:
`166774efcf0f722f2ee18eba0039de2d685b350ee14d7b69e6f83437dafd2af1`.
Extracted outside checkout, under /workspace/.cache/umsa-jdk17. This JDK is a
local compile test tool, not a bundled binary or a required patch level for users.
Downloaded official Gradle8.1.1 and verified published SHA-256:
`e111cb9948407e26351227dabce49822fb88c37ee72f1d1582a69c68af2e702f`.

Executed (with full JDK17 on PATH): `python3 tools/check-local.py` twice after
harness refinements. Final results:
- PASS: 23 real Java core assertions: grammar/bounds, no-peer and one-way state,
  ACK of sender session/sequence, future/foreign ACK rejection, stale timeout,
  sequence regression rejection, session restart, partial/oversized input.
- PASS: actual CLEO candidate source parses under Node.
- PASS: independent Java and Node processes exchange packets both ways; known
  synthetic coordinate values arrive unchanged; actual JS with simulated CLEO
  APIs reconnects under a different session; Java marks stopped peer stale.
- NOT TESTED by that harness: Forge adapter, CLEO runtime, Win32 profile cache
  and locking, installed games, rendering or gameplay. No Forge stubs used.

Attempted full Forge build with verified JDK17 + Gradle8.1.1:
`gradle --no-daemon --console=plain -p mc build`.
Failed during ForgeGradle resolution before compiling adapter. Investigated
with --info and repeated after explicitly configuring Java to use the provided
HTTPS proxy (Java does not automatically use HTTPS_PROXY). Observed:
`HTTP/1.1 403 Forbidden` for the ForgeGradle 6.0.24 plugin-marker POM at
maven.minecraftforge.net. This diagnoses an egress/access blocker, not a
successful Forge build or a demonstrated Java API error. Build log outside
checkout: /tmp/umsa-forge-proxy-build.log. No jar produced; do not distribute an
invented artifact. TLS/checksum verification remained enabled.

Knowledge capture: added in-progress field note
`knowledge/games/gta-san-andreas-de/forge-1-20-1-cleo-diagnostic-bridge.md` and
regenerated knowledge/INDEX.md and index.json. `um kb check --index` passed
264 notes. `um publish check examples/minecraft-sa-de-bridge` passed 24 scanned
files, zero failures/warnings. No PR, push or publication performed.

Windows requirements and external validation still outstanding:
1. User's exact Forge patch; store/build identity is unverified (torrent source).
2. Run candidate build with full JDK17 and Maven Forge connectivity; preserve log
   and resulting jar hash. PowerShell helper was reviewed, not executed here.
3. Stage A: real-game logs prove current-session roundtrip, stale and reconnect.
4. Stage B: verify CJ read-only coordinates on actual exe and MC telemetry.
5. Only then choose/measure rendering, collision and coordinate mapping. Portal,
   inventory/blocks in GTA, TNT effects, pistons/levers are future slices.

Final repository checks: 153 tests passed, 1 Git Bash/MSYS-specific test skipped,
1 pre-existing Pillow deprecation warning. Full `um publish check .`: 698 files,
zero failures, three existing documentation-path warnings. `git diff --check`
passed. Existing application/toolkit code and example GTA V files were untouched;
only new bridge/field-note files and generated knowledge indexes were added.

A source-only ZIP is available at artifacts/umsa-diagnostic-source.zip (ignored
output). It contains this example's source/docs/helpers, no game assets, JDK,
Gradle, jar, DLL/ASI or compiled mod. Main next gate remains the user's actual
Windows build and in-game evidence; this cloud result must not be promoted to
“working mod” or “SA DE compatible” without those checks.
