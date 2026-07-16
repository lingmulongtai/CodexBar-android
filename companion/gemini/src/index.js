#!/usr/bin/env node
import process from 'node:process';
import qrcode from 'qrcode-terminal';
import { loadOrCreateIdentity } from './config.js';
import { collectGeminiQuota } from './gemini-cli.js';
import { chooseLocalAddress } from './network.js';
import { SNAPSHOT_SCHEMA_VERSION, SNAPSHOT_SOURCE } from './protocol.js';
import { startSnapshotServer } from './server.js';

const options = parseArguments(process.argv.slice(2));
const identity = loadOrCreateIdentity();
const address = chooseLocalAddress(options.address);
let latestSnapshot = null;
let refreshing = false;

async function refreshSnapshot() {
  if (refreshing) return;
  refreshing = true;
  try {
    const parsed = await collectGeminiQuota({ command: options.geminiCommand });
    latestSnapshot = {
      schemaVersion: SNAPSHOT_SCHEMA_VERSION,
      source: SNAPSHOT_SOURCE,
      generatedAtEpochSeconds: Math.floor(Date.now() / 1000),
      cliVersion: options.cliVersion,
      ...(parsed.tier == null ? {} : { tier: parsed.tier }),
      windows: parsed.windows
    };
    safeStatus(`Quota snapshot refreshed (${parsed.windows.length} window${parsed.windows.length === 1 ? '' : 's'}).`);
  } catch (error) {
    safeStatus(`Quota refresh failed: ${safeErrorMessage(error)}`);
  } finally {
    refreshing = false;
  }
}

await refreshSnapshot();
const server = await startSnapshotServer({
  address,
  port: options.port,
  identity,
  getSnapshot: () => latestSnapshot
});
const actualPort = server.address().port;
const pairingUri = buildPairingUri({ address, port: actualPort, identity });

process.stdout.write('\nCodexBar Gemini companion is ready.\n');
process.stdout.write(`Listening only on ${address}:${actualPort}\n`);
process.stdout.write('Scan this QR code with the system camera, then confirm pairing in CodexBar.\n\n');
qrcode.generate(pairingUri, { small: true });
process.stdout.write(`\nPairing code (keep private):\n${pairingUri}\n\n`);
process.stdout.write('No Google token, prompt, file, email address, or CLI session content is served.\n');

const refreshTimer = setInterval(refreshSnapshot, options.intervalMinutes * 60_000);
refreshTimer.unref();
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => {
    clearInterval(refreshTimer);
    server.close(() => process.exit(0));
  });
}

function parseArguments(args) {
  const parsed = {
    address: null,
    port: 43821,
    intervalMinutes: 5,
    geminiCommand: process.platform === 'win32' ? 'gemini.cmd' : 'gemini',
    cliVersion: 'official-cli'
  };
  for (let index = 0; index < args.length; index += 1) {
    const name = args[index];
    const value = args[index + 1];
    if (name === '--address' && value) parsed.address = value;
    else if (name === '--port' && value && Number.isInteger(Number(value))) parsed.port = Number(value);
    else if (name === '--interval-minutes' && value && Number.isInteger(Number(value))) parsed.intervalMinutes = Number(value);
    else if (name === '--gemini-command' && value) parsed.geminiCommand = value;
    else if (name === '--cli-version' && value) parsed.cliVersion = value;
    else throw new Error(`Unknown or incomplete argument: ${name}`);
    index += 1;
  }
  if (parsed.port < 1024 || parsed.port > 65535) throw new Error('Port must be between 1024 and 65535');
  if (parsed.intervalMinutes < 1 || parsed.intervalMinutes > 60) throw new Error('Refresh interval must be 1 to 60 minutes');
  if (!/^[A-Za-z0-9._+\-/\\:]{1,260}$/.test(parsed.geminiCommand)) throw new Error('Invalid Gemini command path');
  if (!/^[A-Za-z0-9._+-]{1,64}$/.test(parsed.cliVersion)) throw new Error('Invalid CLI version label');
  return parsed;
}

function buildPairingUri({ address, port, identity }) {
  const query = new URLSearchParams({
    v: '1',
    address,
    port: String(port),
    id: identity.companionId,
    key: identity.sharedKey
  });
  return `codexbar://gemini-pair?${query.toString()}`;
}

function safeStatus(message) {
  process.stdout.write(`[${new Date().toISOString()}] ${message}\n`);
}

function safeErrorMessage(error) {
  const message = error instanceof Error ? error.message : 'Unknown error';
  return message.replace(/[\r\n\t]/g, ' ').slice(0, 180);
}
