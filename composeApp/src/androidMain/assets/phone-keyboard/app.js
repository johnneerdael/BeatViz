(() => {
  'use strict';
  const { gcm, randomBytes } = window.nobleCiphers;
  const $ = (id) => document.getElementById(id);
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  const b64u = {
    enc: (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''),
    dec: (text) => Uint8Array.from(atob(text.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((text.length + 3) % 4)), (c) => c.charCodeAt(0)),
  };
  const setStatus = (text) => { $('status').textContent = text; };
  const STORE = 'beatviz-phone-keyboard';

  // The pairing secret arrives once in the QR fragment and is then kept in
  // this origin's storage, so reopening the page reconnects without a scan.
  let pairing = location.hash.slice(1);
  if (pairing.split('.').length === 2) {
    try { localStorage.setItem(STORE, pairing); } catch (e) { /* private mode: session only */ }
    history.replaceState(null, '', location.pathname);
  } else {
    try { pairing = localStorage.getItem(STORE) || ''; } catch (e) { pairing = ''; }
  }
  const parts = pairing.split('.');
  if (parts.length !== 2) { setStatus('Scan the QR code on the TV again.'); return; }
  const sessionId = b64u.dec(parts[0]);
  const key = b64u.dec(parts[1]);

  const aad = (direction) => {
    const tail = enc.encode(direction);
    const out = new Uint8Array(sessionId.length + tail.length);
    out.set(sessionId);
    out.set(tail, sessionId.length);
    return out;
  };

  // Strictly increasing across page reloads, which the TV enforces against replays.
  let seq = Date.now();
  let field = null;
  let rev = -1;
  let lastTyped = 0;
  let sendTimer = null;

  async function send(type, value) {
    const nonce = randomBytes(12);
    const body = enc.encode(JSON.stringify({ seq: ++seq, type, field: field ? field.id : null, value }));
    const sealed = gcm(key, nonce, aad('c2s')).encrypt(body);
    const res = await fetch('/input', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ n: b64u.enc(nonce), c: b64u.enc(sealed) }),
    });
    if (!res.ok) throw new Error(res.status === 403 ? 'rejected' : 'failed');
  }

  function report(e) {
    setStatus(e.message === 'rejected'
      ? 'The TV refused this phone. Open Phone keyboard on the TV and scan the QR code again.'
      : 'Can’t reach the TV — same Wi-Fi?');
  }

  function render() {
    const input = $('text');
    if (!field) {
      document.body.classList.remove('active');
      setStatus('Select a text field on the TV (Search, or the Spotify sign-in page).');
      return;
    }
    document.body.classList.add('active');
    $('label').textContent = field.label;
    input.placeholder = field.hint || '';
    $('showRow').hidden = !field.obscure;
    input.type = field.obscure && !$('show').checked ? 'password' : 'text';
    input.enterKeyHint = field.action === 'search' ? 'search' : (field.action === 'go' ? 'go' : 'done');
    // Last writer wins, but never overwrite what is being typed here.
    if (Date.now() - lastTyped > 1000 && input.value !== field.value) input.value = field.value;
    setStatus('Typing into the TV.');
  }

  $('text').addEventListener('input', () => {
    lastTyped = Date.now();
    clearTimeout(sendTimer);
    sendTimer = setTimeout(() => { send('text', $('text').value).catch(report); }, 150);
  });

  async function submit() {
    clearTimeout(sendTimer);
    try {
      await send('text', $('text').value);
      await send('key', 'ENTER');
    } catch (e) { report(e); }
  }
  $('enter').addEventListener('click', submit);
  $('text').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); submit(); } });
  $('show').addEventListener('change', render);

  async function ping() {
    try { await send('ping', ''); } catch (e) { report(e); }
    setTimeout(ping, 30000);
  }

  async function poll() {
    let res;
    try {
      res = await fetch('/status?rev=' + rev, { cache: 'no-store' });
    } catch (e) {
      res = null;
    }
    if (!res || !res.ok) {
      setStatus('Can\u2019t reach the TV \u2014 same Wi-Fi?');
      setTimeout(poll, 2000);
      return;
    }
    let status;
    try {
      const env = await res.json();
      status = JSON.parse(dec.decode(gcm(key, b64u.dec(env.n), aad('s2c')).decrypt(b64u.dec(env.c))));
    } catch (e) {
      // The TV answers but with another key: this phone was forgotten there.
      field = null;
      render();
      report(new Error('rejected'));
      setTimeout(poll, 10000);
      return;
    }
    const before = field ? field.id : null;
    rev = status.rev;
    field = status.field;
    if ((field ? field.id : null) !== before) { lastTyped = 0; $('text').value = field ? field.value : ''; }
    render();
    setTimeout(poll, 0);
  }

  ping();
  poll();
})();
