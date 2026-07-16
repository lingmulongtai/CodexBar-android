import test from 'node:test';
import assert from 'node:assert/strict';
import { chooseLocalAddress, isAllowedAddress } from '../src/network.js';

test('accepts only numeric non-public IPv4 addresses', () => {
  for (const address of ['10.0.0.1', '172.16.0.1', '192.168.1.2', '169.254.2.3', '127.0.0.1']) {
    assert.equal(isAllowedAddress(address), true, address);
    assert.equal(chooseLocalAddress(address), address);
  }
  for (const address of ['0.0.0.0', '8.8.8.8', 'example.test', '::1']) {
    assert.equal(isAllowedAddress(address), false, address);
    assert.throws(() => chooseLocalAddress(address));
  }
});
