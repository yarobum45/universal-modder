// Diagnostic candidate for CLEO Redux 1.5.1 x64 / SA DE. No Node/browser APIs.
// Keep [fs] in the filename. See README before copying into CLEO.
(function () {
    if (HOST !== "sa_unreal") throw new Error("UMSA requires HOST=sa_unreal");
    if (typeof IniFile === "undefined") throw new Error("UMSA requires the x64 IniFiles plugin");

    const ROOT = "C:/UMBridge/sa-de-v1"; // Must already exist; match -Dumsa.dir on Java.
    const READ_CJ = false; // Stage B only: enable after heartbeat-only Stage A succeeds.
    const MAX = 1000000000;
    let session = 1 + Math.floor(Math.random() * (MAX - 1));
    let sent = 0, peer = null, receivedAt = 0, previous = "", reports = 0;
    let failed = false;

    function decode(text) {
        if (typeof text !== "string" || text.length > 120) return null;
        const p = text.split("|");
        if (p.length !== 9 || p[0] !== "UMSA1") return null;
        const n = [];
        for (let i = 1; i < p.length; i++) {
            if (!/^-?(0|[1-9][0-9]*)$/.test(p[i])) return null;
            const v = Number(p[i]);
            if (!Number.isInteger(v) || v < -MAX || v > MAX) return null;
            n.push(v);
        }
        if (n[0] < 1 || n[1] < 1 || n[2] < 0 || n[3] < 0 || n[4] < 0 || n[4] > 1
            || ((n[2] === 0) !== (n[3] === 0))) return null;
        return n;
    }
    function q(n) {
        if (!Number.isFinite(n)) throw new Error("non-finite coordinate");
        return Math.max(-MAX, Math.min(MAX, Math.round(n * 10)));
    }

    log("[UMSA] diagnostic only; directory=" + ROOT + " READ_CJ=" + READ_CJ);
    while (true) {
        wait(500); // 2 Hz; never busy-wait, even on missing files or denied permissions.
        try {
            const now = Date.now();
            const next = decode(IniFile.ReadString(ROOT + "/minecraft.ini", "bridge", "packet"));
            if (next && (!peer || next[0] !== peer[0] || next[1] > peer[1])) {
                peer = next; receivedAt = now;
            }
            const age = peer ? Math.max(0, now - receivedAt) : -1;
            const fresh = peer && age < 3000;
            const roundtrip = fresh && peer[2] === session && peer[3] > 0
                && peer[3] <= sent && sent - peer[3] <= 6;
            let state = !peer ? "waiting" : !fresh ? "stale" : roundtrip ? "roundtrip" : "peer-seen";
            let playing = 0, x = 0, y = 0, z = 0;
            if (READ_CJ) {
                const player = new Player(0);
                if (player.isPlaying()) {
                    const pos = player.getChar().getCoordinates();
                    playing = 1; x = q(pos.x); y = q(pos.y); z = q(pos.z);
                }
            }
            if (sent === MAX) { session = 1 + Math.floor(Math.random() * (MAX - 1)); sent = 0; state = "peer-seen"; }
            const nextSeq = sent + 1;
            const text = ["UMSA1", session, nextSeq, fresh ? peer[0] : 0, fresh ? peer[1] : 0,
                playing, x, y, z].join("|");
            if (!decode(text)) throw new Error("invalid outgoing packet");
            if (!IniFile.WriteString(text, ROOT + "/gta.ini", "bridge", "packet"))
                throw new Error("WriteString failed: check folder and fs permission");
            sent = nextSeq;
            if (failed || state !== previous || ++reports % 10 === 0)
                log("[UMSA] state=" + state + " session=" + session + " sent=" + sent
                    + " peer=" + (peer ? "UMSA1|" + peer.join("|") : "none") + " ageMs=" + age);
            previous = state; failed = false;
        } catch (e) {
            if (!failed) log("[UMSA] error; retrying without game changes: " + String(e));
            failed = true;
        }
    }
})();
