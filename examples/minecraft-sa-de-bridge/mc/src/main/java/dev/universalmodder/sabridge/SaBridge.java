package dev.universalmodder.sabridge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import java.nio.file.Path;
import java.util.concurrent.*;

/** Candidate Forge adapter: must be built and validated in a real 1.20.1 client. */
@Mod(SaBridge.ID)
public final class SaBridge {
    public static final String ID = "um_sa_bridge";

    @Mod.EventBusSubscriber(modid = ID, value = Dist.CLIENT)
    public static final class Client {
        private static final Logger LOG = LogUtils.getLogger();
        private static volatile BridgeCore.Pose pose = BridgeCore.Pose.NONE;
        private static boolean started;
        private static final long BEGIN = System.nanoTime();

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            // Read game objects only on the client thread. Never move or modify the player.
            if (mc.player == null || mc.level == null || !mc.hasSingleplayerServer()) {
                pose = BridgeCore.Pose.NONE;
            } else {
                pose = new BridgeCore.Pose(1, quantize(mc.player.getX()),
                    quantize(mc.player.getY()), quantize(mc.player.getZ()));
            }
            if (!started) {
                started = true;
                startWorker();
            }
        }
        private static int quantize(double n) {
            return (int)Math.max(-BridgeCore.MAX, Math.min(BridgeCore.MAX, Math.round(n * 10)));
        }
        private static void startWorker() {
            Path dir = Path.of(System.getProperty("umsa.dir", "C:/UMBridge/sa-de-v1")).toAbsolutePath();
            if (!System.getProperty("os.name", "").startsWith("Windows")
                && System.getProperty("umsa.dir") == null) {
                LOG.error("[UMSA] Set -Dumsa.dir to an absolute lab directory on non-Windows; bridge disabled");
                return;
            }
            ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "umsa-diagnostic-mailbox"); t.setDaemon(true); return t;
            });
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdownNow, "umsa-stop"));
            worker.scheduleWithFixedDelay(new Runnable() {
                private BridgeCore core;
                private String previous = "";
                private int reports;
                private boolean failed;
                @Override public void run() {
                    try {
                        if (core == null) {
                            core = new BridgeCore(dir, BridgeCore.newSession());
                            LOG.info("[UMSA] diagnostic only; directory={}", dir);
                        }
                        BridgeCore.Status status = core.step(pose, (System.nanoTime() - BEGIN) / 1_000_000);
                        if (failed || !status.state().equals(previous) || ++reports % 10 == 0)
                            LOG.info("[UMSA] {}", status);
                        previous = status.state(); failed = false;
                    } catch (Exception e) {
                        if (!failed) LOG.error("[UMSA] mailbox I/O/protocol failure; retrying, no game changes", e);
                        failed = true;
                    }
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        }
    }
}
