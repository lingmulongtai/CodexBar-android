#!/usr/bin/env node
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import qrcode from 'qrcode-terminal';
import { loadOrCreateIdentity } from './config.js';
import { chooseLocalAddress } from './network.js';
import { collectCodexTelemetry } from './scanner.js';
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
    latestSnapshot = await collectCodexTelemetry({ codexHome: options.codexHome });
    safeStatus(`Telemetry refreshed (${latestSnapshot.tokenUsage.daily.length} daily bucket(s)).`);
  } catch (error) {
    safeStatus(`Telemetry refresh failed: ${safeErrorMessage(error)}`);
  } finally {
    refreshing = false;
  }
}

await refreshSnapshot();
if (latestSnapshot == null) throw new Error('Could not build an initial Codex telemetry snapshot');
const server = await startSnapshotServer({
  address,
  port: options.port,
  identity,
  getSnapshot: () => latestSnapshot
});
const actualPort = server.address().port;
const pairingUri = buildPairingUri({ address, port: actualPort, identity });

process.stdout.write('\nCodexBar Codex telemetry companion is ready.\n');
process.stdout.write(`Listening only on ${address}:${actualPort}\n`);
process.stdout.write('Scan this QR code with the system camera, then confirm pairing in CodexBar.\n\n');
qrcode.generate(pairingUri, { small: true });
process.stdout.write(`\nPairing code (keep private):\n${pairingUri}\n\n`);
process.stdout.write('Only aggregate token counts, model labels, dates, and current context size are served.\n');
process.stdout.write('Prompts, responses, file paths, session IDs, credentials, and source files never leave this computer.\n');

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
    port: 43822,
    intervalMinutes: 2,
    codexHome: process.env.CODEX_HOME || path.join(os.homedir(), '.codex')
  };
  for (let index = 0; index < args.length; index += 1) {
    const name = args[index];
    const value = args[index + 1];
    if (name === '--address' && value) parsed.address = value;
    else if (name === '--port' && value && Number.isInteger(Number(value))) parsed.port = Number(value);
    else if (name === '--interval-minutes' && value && Number.isInteger(Number(value))) parsed.intervalMinutes = Number(value);
    else if (name === '--codex-home' && value) parsed.codexHome = value;
    else throw new Error(`Unknown or incomplete argument: ${name}`);
    index += 1;
  }
  if (parsed.port < 1024 || parsed.port > 65535) throw new Error('Port must be between 1024 and 65535');
  if (parsed.intervalMinutes < 1 || parsed.intervalMinutes > 60) throw new Error('Refresh interval must be 1 to 60 minutes');
  if (typeof parsed.codexHome !== 'string' || parsed.codexHome.length < 1 || parsed.codexHome.length > 1024 || parsed.codexHome.includes('\0')) {
    throw new Error('Invalid Codex home path');
  }
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
  return `codexbar://codex-telemetry-pair?${query.toString()}`;
}

function safeStatus(message) {
  process.stdout.write(`[${new Date().toISOString()}] ${message}\n`);
}

function safeErrorMessage(error) {
  const message = error instanceof Error ? error.message : 'Unknown error';
  return message.replace(/[\r\n\t]/g, ' ').slice(0, 180);
}
