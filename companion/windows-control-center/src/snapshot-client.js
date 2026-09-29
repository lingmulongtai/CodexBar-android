import { createDecipheriv, createHmac, randomBytes } from 'node:crypto';
import net from 'node:net';
import { PROVIDERS } from './providers.js';

const MAX_RESPONSE_BYTES = 64 * 1024;

export async function fetchSnapshot({ provider, address, port, identity }) {
  const protocol = PROVIDERS[provider]?.protocol;
  if (!protocol || net.isIP(address) !== 4 || !Number.isInteger(port) || port < 1024 || port > 65535) {
    throw new Error('Invalid local bridge');
  }
  const masterKey = protocol.decodeMasterKey(identity.sharedKey);
  const authKey = protocol.deriveKey(masterKey, protocol.AUTH_KEY_CONTEXT);
  const encryptionKey = protocol.deriveKey(masterKey, protocol.ENCRYPTION_KEY_CONTEXT);
  const request = {
    protocolVersion: protocol.PROTOCOL_VERSION,
    companionId: identity.companionId,
    requestedAtEpochSeconds: Math.floor(Date.now() / 1000),
    nonce: randomBytes(16).toString('base64url')
  };
  request.signature = createHmac('sha256', authKey).update(protocol.requestCanonical(request), 'utf8').digest('base64url');
  const envelope = await exchange(address, port, request);
  const now = Math.floor(Date.now() / 1000);
  if (envelope?.protocolVersion !== protocol.PROTOCOL_VERSION ||
      envelope.companionId !== identity.companionId || envelope.requestNonce !== request.nonce ||
      !Number.isSafeInteger(envelope.sentAtEpochSeconds) || Math.abs(envelope.sentAtEpochSeconds - now) > 120) {
    throw new Error('Invalid bridge response');
  }
  const iv = Buffer.from(envelope.iv, 'base64url');
  const encrypted = Buffer.from(envelope.ciphertext, 'base64url');
  if (iv.length !== 12 || encrypted.length < 16) throw new Error('Invalid encrypted response');
  const decipher = createDecipheriv('aes-256-gcm', encryptionKey, iv);
  decipher.setAAD(Buffer.from(protocol.responseCanonical(envelope), 'utf8'));
  decipher.setAuthTag(encrypted.subarray(-16));
  const snapshot = JSON.parse(Buffer.concat([decipher.update(encrypted.subarray(0, -16)), decipher.final()]).toString('utf8'));
  if (!Number.isSafeInteger(snapshot.generatedAtEpochSeconds) ||
      now - snapshot.generatedAtEpochSeconds > 2 * 3600 || snapshot.generatedAtEpochSeconds > now + 120) {
    throw new Error('Stale bridge snapshot');
  }
  return snapshot;
}

function exchange(address, port, request) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: address, port });
    let content = '';
    let settled = false;
    const finish = (error, result) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      if (error) reject(error); else resolve(result);
    };
    socket.setTimeout(4000, () => finish(new Error('Bridge timed out')));
    socket.on('connect', () => socket.write(`${JSON.stringify(request)}\n`));
    socket.on('data', chunk => {
      content += chunk.toString('utf8');
      if (Buffer.byteLength(content, 'utf8') > MAX_RESPONSE_BYTES) return finish(new Error('Bridge response too large'));
      const newline = content.indexOf('\n');
      if (newline < 0) return;
      try { finish(null, JSON.parse(content.slice(0, newline))); }
      catch { finish(new Error('Invalid bridge response')); }
    });
    socket.on('end', () => finish(new Error('Bridge has no fresh snapshot')));
    socket.on('error', () => finish(new Error('Bridge unavailable')));
  });
}

export function projectSnapshot(provider, snapshot) {
  if (provider === 'codex') {
    const today = snapshot?.tokenUsage?.today?.totalTokens;
    const week = snapshot?.tokenUsage?.last7Days?.totalTokens;
    if (!Number.isSafeInteger(today) || today < 0 || !Number.isSafeInteger(week) || week < 0) throw new Error('Invalid telemetry');
    return { kind: 'telemetry', todayTokens: today, last7DaysTokens: week, generatedAtEpochSeconds: snapshot.generatedAtEpochSeconds };
  }
  if (!Array.isArray(snapshot?.windows) || snapshot.windows.length < 1 || snapshot.windows.length > 32) throw new Error('Missing quotas');
  const windows = snapshot.windows.map(window => {
    if (typeof window.label !== 'string' || window.label.length < 1 || window.label.length > 64 ||
        /[\x00-\x1f\x7f]/.test(window.label) || typeof window.usedFraction !== 'number' ||
        !Number.isFinite(window.usedFraction) || window.usedFraction < 0 || window.usedFraction > 1) throw new Error('Invalid quota');
    return { label: window.label, usedPercent: Math.round(window.usedFraction * 100),
      ...(Number.isSafeInteger(window.resetsAtEpochSeconds) ? { resetsAtEpochSeconds: window.resetsAtEpochSeconds } : {}) };
  });
  return { kind: 'quotas', windows, generatedAtEpochSeconds: snapshot.generatedAtEpochSeconds };
}
