import os from 'node:os';
import net from 'node:net';

export function chooseLocalAddress(requestedAddress) {
  if (requestedAddress != null) {
    if (!isAllowedAddress(requestedAddress)) {
      throw new Error('The --address value must be a numeric private or loopback IPv4 address');
    }
    return requestedAddress;
  }

  const candidates = Object.values(os.networkInterfaces())
    .flatMap((entries) => entries ?? [])
    .filter((entry) => entry.family === 'IPv4' && !entry.internal && isAllowedAddress(entry.address))
    .map((entry) => entry.address)
    .sort(addressPreference);
  if (candidates.length === 0) {
    throw new Error('No private IPv4 address found. Connect both devices to the same Wi-Fi or pass --address.');
  }
  return candidates[0];
}

export function isAllowedAddress(address) {
  if (net.isIP(address) !== 4) return false;
  const parts = address.split('.').map(Number);
  return parts[0] === 10 ||
    (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) ||
    (parts[0] === 192 && parts[1] === 168) ||
    (parts[0] === 169 && parts[1] === 254) ||
    parts[0] === 127 ||
    (parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127);
}

function addressPreference(left, right) {
  const rank = (value) => value.startsWith('192.168.') ? 0 : value.startsWith('10.') ? 1 : 2;
  return rank(left) - rank(right) || left.localeCompare(right, 'en');
}
