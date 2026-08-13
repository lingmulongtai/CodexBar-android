import assert from 'node:assert/strict';
import { createDecipheriv, createHmac, randomBytes } from 'node:crypto';
import test from 'node:test';
import {
  AUTH_KEY_CONTEXT,
  ENCRYPTION_KEY_CONTEXT,
  deriveKey,
  encryptSnapshot,
  requestCanonical,
  responseCanonical,
  verifyRequest
} from '../src/protocol.js';

test('authenticates once and encrypts a bound response envelope', () => {
  const masterKey = randomBytes(32);
  const companionId = '5b017391-6dc4-4ab7-b0ad-2255dada62d7';
  const now = 1_750_000_000;
  const request = {
    protocolVersion: 1,
    companionId,
    requestedAtEpochSeconds: now,
    nonce: randomBytes(16).toString('base64url'),
    signature: ''
  };
  const authKey = deriveKey(masterKey, AUTH_KEY_CONTEXT);
  request.signature = createHmac('sha256', authKey)
    .update(requestCanonical(request), 'utf8')
    .digest('base64url');
  const seenNonces = new Map();

  assert.equal(verifyRequest(request, companionId, authKey, now, seenNonces), true);
  assert.equal(verifyRequest(request, companionId, authKey, now, seenNonces), false);

  const snapshot = { schemaVersion: 1, source: 'codex-cli-local-jsonl', secret: 'encrypted' };
  const encryptionKey = deriveKey(masterKey, ENCRYPTION_KEY_CONTEXT);
  const envelope = encryptSnapshot(snapshot, request, companionId, encryptionKey, now);
  const encrypted = Buffer.from(envelope.ciphertext, 'base64url');
  const decipher = createDecipheriv('aes-256-gcm', encryptionKey, Buffer.from(envelope.iv, 'base64url'));
  decipher.setAAD(Buffer.from(responseCanonical(envelope), 'utf8'));
  decipher.setAuthTag(encrypted.subarray(encrypted.length - 16));
  const plaintext = Buffer.concat([
    decipher.update(encrypted.subarray(0, encrypted.length - 16)),
    decipher.final()
  ]);

  assert.deepEqual(JSON.parse(plaintext.toString('utf8')), snapshot);
});

test('rejects unknown fields, stale requests, and altered signatures', () => {
  const key = randomBytes(32);
  const request = {
    protocolVersion: 1,
    companionId: '5b017391-6dc4-4ab7-b0ad-2255dada62d7',
    requestedAtEpochSeconds: 1_750_000_000,
    nonce: randomBytes(16).toString('base64url'),
    signature: randomBytes(32).toString('base64url'),
    redirect: 'https://evil.test'
  };

  assert.equal(verifyRequest(request, request.companionId, key, 1_750_000_000, new Map()), false);
  delete request.redirect;
  assert.equal(verifyRequest(request, request.companionId, key, 1_750_000_000, new Map()), false);
  assert.equal(verifyRequest(request, request.companionId, key, 1_750_001_000, new Map()), false);
});
