import test from 'node:test';
import assert from 'node:assert/strict';
import { createDecipheriv, createHmac } from 'node:crypto';
import {
  AUTH_KEY_CONTEXT,
  ENCRYPTION_KEY_CONTEXT,
  deriveKey,
  encryptSnapshot,
  requestCanonical,
  responseCanonical,
  verifyRequest
} from '../src/protocol.js';

test('verifies a request once and encrypts a nonce-bound response', () => {
  const masterKey = Buffer.alloc(32, 7);
  const authKey = deriveKey(masterKey, AUTH_KEY_CONTEXT);
  const encryptionKey = deriveKey(masterKey, ENCRYPTION_KEY_CONTEXT);
  const request = {
    protocolVersion: 1,
    companionId: '5b017391-6dc4-4ab7-b0ad-2255dada62d7',
    requestedAtEpochSeconds: 1_750_000_000,
    nonce: Buffer.alloc(16, 3).toString('base64url'),
    signature: ''
  };
  request.signature = createHmac('sha256', authKey)
    .update(requestCanonical(request), 'utf8')
    .digest('base64url');
  const seen = new Map();

  assert.equal(verifyRequest(request, request.companionId, authKey, 1_750_000_000, seen), true);
  assert.equal(typeof seen.get(request.nonce), 'number');
  assert.equal(verifyRequest(request, request.companionId, authKey, 1_750_000_000, seen), false);

  assert.equal(
    verifyRequest({ ...request, nonce: Buffer.alloc(16, 4).toString('base64url'), extra: true }, request.companionId, authKey, 1_750_000_000, new Map()),
    false
  );

  const snapshot = { schemaVersion: 1, source: 'gemini-cli-terminal', windows: [] };
  const envelope = encryptSnapshot(
    snapshot,
    request,
    request.companionId,
    encryptionKey,
    1_750_000_001
  );
  const encrypted = Buffer.from(envelope.ciphertext, 'base64url');
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
});
