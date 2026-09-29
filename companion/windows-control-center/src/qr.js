#!/usr/bin/env node
import fs from 'node:fs';
import net from 'node:net';
import qrcode from 'qrcode';
import { PROVIDERS, pairingCode, readIdentity } from './providers.js';

const [provider, address] = process.argv.slice(2);
if (!PROVIDERS[provider] || net.isIP(address) !== 4) process.exit(2);
try {
  const identity = JSON.parse(fs.readFileSync(readIdentity(provider), 'utf8'));
  const code = pairingCode(provider, address, PROVIDERS[provider].port, identity);
  const png = await qrcode.toBuffer(code, { type: 'png', errorCorrectionLevel: 'M', margin: 3, width: 300,
    color: { dark: '#17202b', light: '#ffffff' } });
  process.stdout.write(png.toString('base64'));
} catch {
  process.exit(1);
}
