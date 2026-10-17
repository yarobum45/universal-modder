import dev.universalmodder.sabridge.BridgeCore;
import java.nio.file.Path;

/** Synthetic Minecraft endpoint; deliberately not packaged in the mod jar. */
public final class BridgeHarness {
    public static void main(String[] args) throws Exception {
        BridgeCore core = new BridgeCore(Path.of(args[0]), BridgeCore.newSession());
        long begin = System.nanoTime();
        long duration = Long.parseLong(args[1]) * 1000;
        while ((System.nanoTime() - begin) / 1_000_000 < duration) {
            long elapsed = (System.nanoTime() - begin) / 1_000_000;
            System.out.println("SIMULATED_MC " + core.step(new BridgeCore.Pose(1, 100, 640, -200), elapsed));
            Thread.sleep(500);
        }
    }
}
