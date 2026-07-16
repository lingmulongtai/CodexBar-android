import os from 'node:os';
import pty from 'node-pty';
import { parseGeminiQuotaOutput } from './quota-parser.js';

const MAX_CAPTURE_LENGTH = 256 * 1024;

export async function collectGeminiQuota({
  command = 'gemini',
  startupDelayMillis = 6_000,
  timeoutMillis = 30_000,
  now = new Date()
} = {}) {
  return new Promise((resolve, reject) => {
    let settled = false;
    let commandSent = false;
    let capture = '';
    const terminal = pty.spawn(command, ['--screen-reader'], {
      name: 'xterm-color',
      cols: 120,
      rows: 60,
      cwd: os.homedir(),
      env: {
        ...process.env,
        NO_COLOR: '1'
      }
    });

    const finish = (error, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(sendTimer);
      clearTimeout(timeoutTimer);
      try {
        terminal.kill();
      } catch {
        // Process already exited.
      }
      if (error) reject(error);
      else resolve(value);
    };

    const sendTimer = setTimeout(() => {
      commandSent = true;
      terminal.write('/stats\r');
    }, startupDelayMillis);
    const timeoutTimer = setTimeout(() => {
      finish(new Error('Gemini CLI did not return model quota data before the timeout'));
    }, timeoutMillis);

    terminal.onData((data) => {
      capture = (capture + data).slice(-MAX_CAPTURE_LENGTH);
      if (!commandSent) return;
      const parsed = parseGeminiQuotaOutput(capture, now);
      if (parsed != null) finish(null, parsed);
    });
    terminal.onExit(({ exitCode }) => {
      if (!settled) {
        finish(new Error(`Gemini CLI exited before quota data was available (${exitCode})`));
      }
    });
  });
}
