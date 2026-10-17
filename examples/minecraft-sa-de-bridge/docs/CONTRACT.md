# UMSA diagnostic mailbox v1

Status: candidate adapters, synthetic transport tested; retail games untested.
Host: Windows, San Andreas DE exe 1.0.113.21181, CLEO Redux 1.5.1 x64 +
IniFiles plugin. This exe is documented for Steam; store needs confirmation.
Guest: Minecraft Java 1.20.1, Forge 47.x, JDK17; build baseline Forge 47.2.0.
Route: two independent real games, diagnostic state exchange only.

## Ownership

| System | Current owner | Handover in v1 |
|---|---|---|
| CJ position, movement, physics | GTA | None; optional read-only coordinates |
| Minecraft player | Minecraft | None; read-only coordinates in single-player |
| Camera and input | Each game | User changes focus manually |
| Blocks, inventory, redstone | Minecraft | No synchronization to GTA yet |
| Host → guest collision | GTA | No export |
| Guest → host collision | Minecraft | No export |
| NPCs, vehicles | Their own game | No synchronization |
| Damage, health, TNT | Their own game | No cross-game commands |
| Saves | Their own game | No conversion or write by bridge |
| Menus, pause, loading, death | Their own game | No control or teleport |
| Missions, cutscenes, driving | GTA | No control or teleport |

## Units and axes

Telemetry is **not transformed between games**. Each triple is in its own game's
native axis order: qx = round(10*x), qy = round(10*y), qz = round(10*z).
Resolution 0.1 native units; bounded to ±100,000,000 native units (saturated).
No physical ratio between SA DE script coordinates, Unreal renderer coordinates
and Minecraft blocks is assumed. Do not reuse GTA V's map or multiply by 100
merely because SA DE renders through Unreal. No yaw, health scaling or damage
conversion exists in v1. A future map requires measured origins, east/north/up,
handedness, yaw sign, scale and roundtrip anchor tests on this executable.

## Wire and storage

Directory: `C:/UMBridge/sa-de-v1`, local writable disk; not a synced/network folder.
One Minecraft process and one CLEO script only. No concurrent writers to a file.
`minecraft.ini`: written by Java worker; `gta.ini`: written by CLEO IniFiles.
ASCII, CRLF, one section/key, no BOM. Example:

```ini
[bridge]
packet=UMSA1|123|7|456|5|1|100|640|-200
```

Packet fields: magic `UMSA1`, sender session, sender sequence, acknowledged peer
session, acknowledged peer sequence, playing flag (0/1), qx, qy, qz.
All numbers are decimal integers, absolute value ≤1,000,000,000. Sessions and
sequences are positive; ACK pair is both zero (none) or both positive. Leading
zeros, plus signs, fractional numbers and extra fields are rejected. Packet ≤120
ASCII characters (IniFiles SDK buffer is 128 bytes). Java rejects files >512 bytes,
malformed/partial snapshots and unknown protocol IDs. Missing input is normal.
Strings outside this grammar never become game commands or file paths.

Each side runs at roughly 2 Hz; this is a latest-value snapshot, not a reliable
event queue. It can discard intermediate packets. No TNT, block edits, chat
commands, script evaluation or inventory writes are accepted. The root folder
is not an authentication boundary against other local programs.

Java writes a sibling temp file and atomically replaces its own mailbox. A
Windows sharing/permissions failure is logged and retried at the next interval.
CLEO updates one key with WritePrivateProfileString. Its disk/cache behaviour
with Java's ReadString/rename must be checked on Windows; Node's shim cannot
validate this. Syntactically valid partial corruption is not integrity-protected;
never use this v1 channel for destructive events. There is no in-place Java
fallback that could disguise atomic-replace failures.

## Freshness, ACKs and restarts

New random positive session per process/script instance; sequence increments
only after successful writes. Counter exhaustion rotates session. A changed
peer session is a restart. Same-session duplicate/regressed sequences do not
refresh the timer. An unchanged mailbox becomes stale after 3 seconds. Java
uses monotonic elapsed time; CLEO uses Date.now, so wall-clock adjustments can
affect its stale label (sequence/ACK distance still limits roundtrip status).

`roundtrip` requires a fresh advancing peer and an ACK of this session's sent
sequence no more than six packets behind. `peer-seen` alone is one-way evidence.
Fresh peer packets are echoed as ACKs; stale peers get a zero ACK. No replayed
command can occur because no command channel exists. A peer session change
requires a new ACK exchange before roundtrip is reestablished. Logs include
session/sequence/ACK/pose and a status transition, then periodic status.

`playing=1` for Minecraft means a client player/level with an integrated server;
it does not prove unpaused simulation. Multiplayer telemetry is disabled.
For CLEO, `playing=0` by default because READ_CJ=false. Stage B optionally uses
Player.isPlaying → getChar → getCoordinates. That is not a claim CJ is out of
a vehicle/cutscene or safe for a future teleport. Coordinates are zero when
playing=0. Heartbeat can continue while menus/pause stop simulation.

## Lifecycle

Either game can start first. Menus/no peer produce waiting or stale; there is
no watchdog action on gameplay. World reload changes telemetry, not ownership.
Worker is daemon and shuts down with JVM; CLEO owns its script lifecycle. Exiting
one game leaves an inert stale file. A restarted side has a new session. Do not
manually rewrite mailboxes while either side is running. Remove adapters with
both games closed as described in README. No live processes are assumed to
survive a future cloud task.

## Outside this prototype

No portal, CJ model in Minecraft, blocks in GTA, inventory overlay, shared
camera, renderer, input forwarding, ground export, TNT effects, vehicle impulses,
redstone replication, shared health or saved cross-game world. See FEASIBILITY.md.
