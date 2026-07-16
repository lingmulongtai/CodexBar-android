import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { createDecipheriv, createHmac } from 'node:crypto';
import {
  AUTH_KEY_CONTEXT,
  ENCRYPTION_KEY_CONTEXT,
  deriveKey,
  requestCanonical,
  responseCanonical
} from '../src/protocol.js';
import { startSnapshotServer } from '../src/server.js';

test('serves one authenticated encrypted snapshot and rejects nonce replay', async (context) => {
  const companionId = '5b017391-6dc4-4ab7-b0ad-2255dada62d7';
  const masterKey = Buffer.alloc(32, 11);
  const identity = {
    companionId,
    sharedKey: masterKey.toString('base64url')
  };
  const snapshot = {
    schemaVersion: 1,
    source: 'gemini-cli-terminal',
    generatedAtEpochSeconds: Math.floor(Date.now() / 1000),
    cliVersion: 'test',
    windows: [{ label: 'Pro', usedFraction: 0.25 }]
  };
  const server = await startSnapshotServer({
    address: '127.0.0.1',
    port: 0,
    identity,
    getSnapshot: () => snapshot
  });
  context.after(() => new Promise((resolve) => server.close(resolve)));
  const address = server.address();
  assert.notEqual(typeof address, 'string');
  assert.ok(address);

  const requestedAtEpochSeconds = Math.floor(Date.now() / 1000);
  const request = {
    protocolVersion: 1,
    companionId,
    requestedAtEpochSeconds,
    nonce: Buffer.alloc(16, 9).toString('base64url'),
    signature: ''
  };
  const authKey = deriveKey(masterKey, AUTH_KEY_CONTEXT);
  request.signature = createHmac('sha256', authKey)
    .update(requestCanonical(request), 'utf8')
    .digest('base64url');

  const response = await exchange(address.port, request);
  const envelope = JSON.parse(response);
  const encrypted = Buffer.from(envelope.ciphertext, 'base64url');
  const encryptionKey = deriveKey(masterKey, ENCRYPTION_KEY_CONTEXT);
  const decipher = createDecipheriv(
    'aes-256-gcm',
    encryptionKey,
    Buffer.from(envelope.iv, 'base64url')
  );
  decipher.setAAD(Buffer.from(responseCanonical(envelope), 'utf8'));
  decipher.setAuthTag(encrypted.subarray(encrypted.length - 16));
  const plaintext = Buffer.concat([
    decipher.update(encrypted.subarray(0, encrypted.length - 16)),
    decipher.final()
  ]);
  assert.deepEqual(JSON.parse(plaintext.toString('utf8')), snapshot);

  assert.equal(await exchange(address.port, request), '');
});

function exchange(port, request) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: '127.0.0.1', port });
    let output = '';
    socket.setEncoding('utf8');
    socket.on('connect', () => socket.write(`${JSON.stringify(request)}\n`));
    socket.on('data', (chunk) => { output += chunk; });
    socket.on('end', () => resolve(output.trim()));
    socket.on('error', reject);
  });
}
