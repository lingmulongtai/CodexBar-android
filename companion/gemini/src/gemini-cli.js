import os from 'node:os';
import path from 'node:path';
import { mkdirSync } from 'node:fs';
import pty from 'node-pty';
import { parseGeminiQuotaOutput, sanitizeTerminalOutput } from './quota-parser.js';

const MAX_CAPTURE_LENGTH = 256 * 1024;

export async function collectGeminiQuota({
  command = 'gemini',
  startupDelayMillis = 6_000,
  timeoutMillis = 30_000,
  now = new Date(),
  homeDirectory = os.homedir(),
  spawn = pty.spawn
} = {}) {
  const workingDirectory = path.join(homeDirectory, '.codexbar', 'gemini-workspace');
  mkdirSync(workingDirectory, { recursive: true, mode: 0o700 });
  return new Promise((resolve, reject) => {
    let settled = false;
    let commandSent = false;
    let capture = '';
    const startedAt = Date.now();
    const terminal = spawn(command, ['--screen-reader'], {
      name: 'xterm-color',
      cols: 120,
      rows: 60,
      cwd: workingDirectory,
      env: {
        ...process.env,
        NO_COLOR: '1'
      }
    });

    const finish = (error, value) => {
      if (settled) return;
      settled = true;
      clearInterval(sendTimer);
      clearTimeout(timeoutTimer);
      try {
        terminal.kill();
      } catch {
        // Process already exited.
      }
      if (error) reject(error);
      else resolve(value);
    };

    const sendTimer = setInterval(() => {
      if (Date.now() < startedAt + startupDelayMillis || commandSent) return;
      const screen = sanitizeTerminalOutput(capture).toLowerCase();
      const readyAt = screen.lastIndexOf('type your message');
      const setupAt = Math.max(
        screen.lastIndexOf('do you trust the files'),
        screen.lastIndexOf('waiting for authentication'),
        screen.lastIndexOf('choose your authentication method'),
        screen.lastIndexOf('sign in to gemini'),
        screen.lastIndexOf('select a login method')
      );
      if (setupAt > readyAt) {
        const error = new Error('Complete Gemini CLI sign-in and folder trust in the dedicated workspace');
        error.code = 'SETUP_REQUIRED';
        finish(error);
        return;
      }
      if (readyAt < 0) return;
      commandSent = true;
      terminal.write('/model\r');
    }, 250);
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
