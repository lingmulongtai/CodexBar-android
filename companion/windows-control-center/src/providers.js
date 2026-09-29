import os from 'node:os';
import path from 'node:path';
import * as claudeProtocol from '../../claude/src/protocol.js';
import * as antigravityProtocol from '../../antigravity/src/protocol.js';
import * as geminiProtocol from '../../gemini/src/protocol.js';
import * as codexProtocol from '../../codex/src/protocol.js';

export const PROVIDERS = Object.freeze({
  claude: { name: 'Claude', port: 43823, identityFile: 'claude-companion.json', protocol: claudeProtocol },
  antigravity: { name: 'Antigravity', port: 43824, identityFile: 'antigravity-companion.json', protocol: antigravityProtocol },
  gemini: { name: 'Gemini CLI', port: 43821, identityFile: 'gemini-companion.json', protocol: geminiProtocol },
  codex: { name: 'Codex telemetry', port: 43822, identityFile: 'codex-telemetry-companion.json', protocol: codexProtocol }
});

export function readIdentity(provider, homeDirectory = os.homedir()) {
  const definition = PROVIDERS[provider];
  if (!definition) throw new Error('Unknown provider');
  const file = path.join(homeDirectory, '.codexbar', definition.identityFile);
  return file;
}

export function pairingCode(provider, address, port, identity) {
  if (!PROVIDERS[provider] || !identity || !/^[0-9a-f-]{36}$/i.test(identity.companionId) ||
      !/^[A-Za-z0-9_-]{43}$/.test(identity.sharedKey)) throw new Error('Invalid pairing identity');
  if (provider === 'claude') return ['CBCLAUDE1', address, port, identity.companionId, identity.sharedKey].join('|');
  if (provider === 'antigravity') return ['CBANTIGRAVITY1', address, port, identity.companionId, identity.sharedKey].join('|');
  const query = new URLSearchParams({ v: '1', address, port: String(port), id: identity.companionId, key: identity.sharedKey });
  return `codexbar://${provider === 'gemini' ? 'gemini-pair' : 'codex-telemetry-pair'}?${query.toString()}`;
}
