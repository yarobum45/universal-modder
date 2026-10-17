package dev.universalmodder.sabridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ThreadLocalRandom;

/** No game dependencies. One writer per file; messages never execute commands. */
public final class BridgeCore {
    public static final long TTL_MS = 3000;
    public static final int MAX = 1_000_000_000;
    public record Pose(int playing, int x, int y, int z) {
        public static final Pose NONE = new Pose(0, 0, 0, 0);
    }
    public record Packet(int session, int seq, int ackSession, int ackSeq, Pose pose) {
        public String encode() {
            String text = "UMSA1|" + session + "|" + seq + "|" + ackSession + "|" + ackSeq
                + "|" + pose.playing + "|" + pose.x + "|" + pose.y + "|" + pose.z;
            // Apply exactly the same validation to locally produced packets.
            decode(text);
            return text;
        }
        public static Packet decode(String text) {
            if (text.length() > 120) throw new IllegalArgumentException("packet too long");
            String[] p = text.split("\\|", -1);
            if (p.length != 9 || !p[0].equals("UMSA1"))
                throw new IllegalArgumentException("wrong protocol or field count");
            int[] n = new int[8];
            for (int i = 0; i < n.length; i++) {
                if (!p[i + 1].matches("-?(0|[1-9][0-9]*)"))
                    throw new IllegalArgumentException("not a canonical integer");
                n[i] = Integer.parseInt(p[i + 1]);
                if (n[i] < -MAX || n[i] > MAX) throw new IllegalArgumentException("out of range");
            }
            if (n[0] < 1 || n[1] < 1 || n[2] < 0 || n[3] < 0 || n[4] < 0 || n[4] > 1
                || ((n[2] == 0) != (n[3] == 0)))
                throw new IllegalArgumentException("invalid identity, ACK or playing flag");
            return new Packet(n[0], n[1], n[2], n[3], new Pose(n[4], n[5], n[6], n[7]));
        }
    }
    public record Status(String state, int session, int sent, Packet peer, long ageMs) {
        @Override public String toString() {
            return "state=" + state + " session=" + session + " sent=" + sent
                + " peer=" + (peer == null ? "none" : peer.encode()) + " ageMs=" + ageMs;
        }
    }

    private final Path outgoing, incoming;
    private int session, sent;
    private Packet peer;
    private long receivedAt;

    public BridgeCore(Path directory, int session) throws IOException {
        if (session < 1 || session > MAX) throw new IllegalArgumentException("bad session");
        Files.createDirectories(directory);
        this.outgoing = directory.resolve("minecraft.ini");
        this.incoming = directory.resolve("gta.ini");
        this.session = session;
    }
    public static int newSession() { return ThreadLocalRandom.current().nextInt(1, MAX); }

    /** Call at 2 Hz from a worker, never from a game tick. nowMs must be monotonic. */
    public Status step(Pose pose, long nowMs) throws IOException {
        Packet next = read(incoming);
        if (next != null && (peer == null || next.session != peer.session || next.seq > peer.seq)) {
            peer = next;
            receivedAt = nowMs;
        }
        long age = peer == null ? -1 : Math.max(0, nowMs - receivedAt);
        boolean fresh = peer != null && age < TTL_MS;
        boolean roundtrip = fresh && peer.ackSession == session && peer.ackSeq > 0
            && peer.ackSeq <= sent && sent - peer.ackSeq <= 6;
        String state = peer == null ? "waiting" : !fresh ? "stale" : roundtrip ? "roundtrip" : "peer-seen";
        // Practically unreachable, but never wrap an INI integer into a negative sequence.
        if (sent == MAX) { session = newSession(); sent = 0; state = "peer-seen"; }
        int nextSeq = sent + 1;
        Packet packet = new Packet(session, nextSeq, fresh ? peer.session : 0,
            fresh ? peer.seq : 0, pose);
        write(outgoing, packet);
        sent = nextSeq;
        return new Status(state, session, sent, peer, age);
    }

    public static Packet read(Path path) throws IOException {
        try {
            if (Files.size(path) > 512) return null;
            String contents = Files.readString(path, StandardCharsets.US_ASCII);
            // Exactly one section and one key. Reject partial/foreign files instead of guessing.
            String[] lines = contents.replace("\r", "").strip().split("\n");
            if (lines.length != 2 || !lines[0].equalsIgnoreCase("[bridge]")
                || !lines[1].startsWith("packet=")) return null;
            return Packet.decode(lines[1].substring(7));
        } catch (NoSuchFileException | IllegalArgumentException e) {
            return null;
        }
    }

    public static void write(Path path, Packet packet) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, "[bridge]\r\npacket=" + packet.encode() + "\r\n",
            StandardCharsets.US_ASCII);
        // Fail visibly if replacement is unsupported or Windows denies sharing. Retry next tick.
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
