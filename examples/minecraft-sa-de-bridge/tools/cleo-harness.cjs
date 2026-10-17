// Runs the ACTUAL CLEO candidate script with simulated IniFile/Player/wait APIs.
// Does not test CLEO, Win32 profile caching, executable compatibility or GTA.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const [script, directory, seconds, telemetry = 'false'] = process.argv.slice(2);
const stop = new Error('HARNESS_STOP');
const end = Date.now() + Number(seconds) * 1000;
const sleep = new Int32Array(new SharedArrayBuffer(4));
let writes = 0, reads = 0;
fs.mkdirSync(directory, {recursive: true});
function resolve(p) { return path.join(directory, path.basename(p)); }
let source = fs.readFileSync(script, 'utf8');
if (telemetry === 'true') source = source.replace('const READ_CJ = false;', 'const READ_CJ = true;');
try {
    vm.runInNewContext(source, {
        HOST: 'sa_unreal',
        log: text => console.log('SIMULATED_CLEO ' + text),
        wait(ms) {
            if (Date.now() >= end) throw stop;
            Atomics.wait(sleep, 0, 0, ms);
        },
        Player: class {
            isPlaying() { return true; }
            getChar() { return {getCoordinates: () => ({x: 123.4, y: -456.7, z: 89})}; }
        },
        IniFile: {
            ReadString(p, section, key) {
                reads++;
                try {
                    const contents = fs.readFileSync(resolve(p), 'ascii');
                    const m = contents.match(/^\[bridge\]\r?\npacket=([^\r\n]*)\r?\n?$/i);
                    return m ? m[1] : '';
                } catch (e) { if (e.code === 'ENOENT') return ''; throw e; }
            },
            WriteString(value, p, section, key) {
                if (section !== 'bridge' || key !== 'packet' || value.length > 120)
                    throw new Error('wrong SDK invocation or packet length');
                writes++;
                // Simulated whole-value profile write, not a claim of Win32 atomicity.
                fs.writeFileSync(resolve(p), '[bridge]\r\npacket=' + value + '\r\n', 'ascii');
                return true;
            }
        }
    }, {filename: script});
} catch (e) { if (e !== stop) throw e; }
if (writes < 2 || reads < 2) throw new Error('script did not exchange messages');
console.log('SIMULATED_CLEO completed writes=' + writes + ' reads=' + reads);
