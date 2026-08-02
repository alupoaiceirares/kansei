import { test } from 'node:test';
import assert from 'node:assert/strict';
import { render } from './templates.js';

test('render(email-verification) returns the right subject and interpolates vars', () => {
  const { subject, html } = render('email-verification', {
    username: 'rares',
    link: 'http://localhost:3000/verify-email?token=abc123',
  });

  assert.equal(subject, 'Confirm your email');
  assert.match(html, /Hi rares/);
  assert.match(html, /verify-email\?token(=|&#x3D;)abc123/);
});

test('render(password-reset) returns the right subject and interpolates vars', () => {
  const { subject, html } = render('password-reset', {
    username: 'rares',
    link: 'http://localhost:3000/reset-password?token=xyz789',
  });

  assert.equal(subject, 'Reset your password');
  assert.match(html, /Hi rares/);
  assert.match(html, /reset-password\?token(=|&#x3D;)xyz789/);
});

test('render(unknown template) throws', () => {
  assert.throws(() => render('not-a-real-template', {}), /Unknown mail template/);
});

test('render caches the compiled template - repeated calls stay consistent', () => {
  const first = render('email-verification', { username: 'a', link: 'http://x' });
  const second = render('email-verification', { username: 'b', link: 'http://y' });

  assert.match(first.html, /Hi a/);
  assert.match(second.html, /Hi b/);
});
