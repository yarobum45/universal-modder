import dev.universalmodder.sabridge.BridgeCore;
import dev.universalmodder.sabridge.BridgeCore.Packet;
import dev.universalmodder.sabridge.BridgeCore.Pose;
import java.nio.file.*;

/** Uses the real Java core, no Minecraft/Forge stubs. */
public final class CoreChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        BridgeCore core = new BridgeCore(dir, 123);
        check(core.step(Pose.NONE, 0).state().equals("waiting"), "missing peer");
        Path gta = dir.resolve("gta.ini");
        Packet p = new Packet(456, 1, 0, 0, new Pose(1, -123, 456, 789));
        BridgeCore.write(gta, p);
        check(core.step(Pose.NONE, 500).state().equals("peer-seen"), "one-way is not roundtrip");
        Packet mine = BridgeCore.read(dir.resolve("minecraft.ini"));
        check(mine.ackSession() == 456 && mine.ackSeq() == 1, "ACK peer identity and sequence");
        BridgeCore.write(gta, new Packet(456, 2, 123, mine.seq(), p.pose()));
        check(core.step(Pose.NONE, 1000).state().equals("roundtrip"), "two-way ACK");
        check(core.step(Pose.NONE, 4000).state().equals("stale"), "unchanged file expires");
        check(BridgeCore.read(dir.resolve("minecraft.ini")).ackSeq() == 0, "stale peer not ACKed");
        BridgeCore.write(gta, new Packet(456, 1, 123, 1, p.pose()));
        check(core.step(Pose.NONE, 4500).state().equals("stale"), "sequence regression is not activity");
        BridgeCore.write(gta, new Packet(777, 1, 0, 0, Pose.NONE));
        check(core.step(Pose.NONE, 5000).state().equals("peer-seen"), "peer restart needs new ACK");
        BridgeCore.write(gta, new Packet(777, 2, 999, 1, Pose.NONE));
        check(core.step(Pose.NONE, 5500).state().equals("peer-seen"), "foreign session ACK rejected");
        BridgeCore.write(gta, new Packet(777, 3, 123, 999, Pose.NONE));
        check(core.step(Pose.NONE, 6000).state().equals("peer-seen"), "future ACK rejected");
        Packet limits = new Packet(BridgeCore.MAX, BridgeCore.MAX, BridgeCore.MAX, BridgeCore.MAX,
            new Pose(1, -BridgeCore.MAX, -BridgeCore.MAX, -BridgeCore.MAX));
        check(limits.encode().length() <= 120, "SDK 128-byte string limit");
        check(Packet.decode(limits.encode()).equals(limits), "wire boundary roundtrip");
        String[] bad = {"", "UMSA2|1|1|0|0|0|0|0|0", "UMSA1|0|1|0|0|0|0|0|0",
            "UMSA1|1|1|0|2|0|0|0|0", "UMSA1|1|1|0|0|2|0|0|0", "UMSA1|1|1|0|0|0|NaN|0|0",
            "UMSA1|1|1|0|0|0|1000000001|0|0", "UMSA1|1|1|0|0|0|00|0|0", "x".repeat(121)};
        for (String text : bad) {
            boolean rejected = false;
            try { Packet.decode(text); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "malformed packet accepted: " + text);
        }
        Files.writeString(gta, "[bridge]\npacket=UMSA1|1|");
        check(BridgeCore.read(gta) == null, "partial write ignored");
        Files.writeString(gta, "x".repeat(513));
        check(BridgeCore.read(gta) == null, "oversized file ignored");
        System.out.println("PASS " + checks + " Java core checks (no game runtime)");
    }
}
