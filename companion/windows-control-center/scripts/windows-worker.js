// The tray adds this gated process to a Windows Job Object before any provider
// module can start. All provider children and their CLI/PTY descendants stay owned.
const provider = process.argv[2];
if (!['claude', 'antigravity', 'gemini', 'codex'].includes(provider)) process.exit(2);
const timeout = setTimeout(() => process.exit(1), 30_000);
let input = '';
let started = false;
process.stdin.setEncoding('utf8');
process.stdin.on('end', () => { if (!started) process.exit(1); });
process.stdin.on('data', async chunk => {
  if (started) return;
  input += chunk;
  if (input.length > 16) process.exit(1);
  if (!input.includes('\n')) return;
  if (input.trim() !== 'start') process.exit(1);
  started = true;
  clearTimeout(timeout);
  process.stdin.pause();
  process.argv.splice(2, 1);
  try { await import(`../../${provider}/src/index.js`); }
  catch { process.exit(1); }
});
