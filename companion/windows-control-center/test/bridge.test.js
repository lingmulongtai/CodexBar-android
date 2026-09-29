import assert from 'node:assert/strict';
import { randomBytes, randomUUID } from 'node:crypto';
import test from 'node:test';
import { startSnapshotServer } from '../../claude/src/server.js';
import { pairingCode } from '../src/providers.js';
import { fetchSnapshot, projectSnapshot } from '../src/snapshot-client.js';

const identity = () => ({ protocolVersion: 1, companionId: randomUUID(), sharedKey: randomBytes(32).toString('base64url') });

test('reads authenticated encrypted bridge data and exposes only quota fields', async () => {
  const account = identity();
  const now = Math.floor(Date.now() / 1000);
  const server = await startSnapshotServer({ address: '127.0.0.1', port: 0, identity: account,
    getSnapshot: () => ({ generatedAtEpochSeconds: now, windows: [
      { label: '5-Hour', usedFraction: 0.25, resetsAtEpochSeconds: now + 3600 }
    ], email: 'private@example.test', secret: 'never-export' }) });
  try {
    const snapshot = await fetchSnapshot({ provider: 'claude', address: '127.0.0.1', port: server.address().port, identity: account });
    assert.deepEqual(projectSnapshot('claude', snapshot), { kind: 'quotas', generatedAtEpochSeconds: now, windows: [
      { label: '5-Hour', usedPercent: 25, resetsAtEpochSeconds: now + 3600 }
    ] });
    await assert.rejects(fetchSnapshot({ provider: 'claude', address: '127.0.0.1', port: server.address().port,
      identity: { ...account, sharedKey: randomBytes(32).toString('base64url') } }));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});

test('QR payloads match the four Android pairing formats', () => {
  const account = identity();
  for (const [provider, prefix] of Object.entries({
    claude: 'CBCLAUDE1|100.74.102.71|43823|',
    antigravity: 'CBANTIGRAVITY1|100.74.102.71|43824|',
    gemini: 'codexbar://gemini-pair?',
    codex: 'codexbar://codex-telemetry-pair?'
  })) {
    const port = { claude: 43823, antigravity: 43824, gemini: 43821, codex: 43822 }[provider];
    const code = pairingCode(provider, '100.74.102.71', port, account);
    assert.ok(code.startsWith(prefix));
    assert.ok(code.includes(account.sharedKey));
  }
});

test('refuses missing and fabricated usage values', () => {
  assert.throws(() => projectSnapshot('gemini', { windows: [{ label: 'Unknown', usedFraction: null }] }));
  assert.throws(() => projectSnapshot('codex', { tokenUsage: { today: { totalTokens: 0 } } }));
});
