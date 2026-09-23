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

test('render(wirehood-disable-request) returns the right subject and interpolates vars', () => {
  const { subject, html } = render('wirehood-disable-request', {
    username: 'rares',
    userId: '11111111-1111-1111-1111-111111111111',
  });

  assert.equal(subject, 'Wirehood account disable request');
  assert.match(html, /rares/);
  assert.match(html, /11111111-1111-1111-1111-111111111111/);
});


test('render(tailwind-disable-request) returns the right subject and interpolates vars', () => {
  const { subject, html } = render('tailwind-disable-request', {
    username: 'rares',
    userId: '22222222-2222-2222-2222-222222222222',
  });

  assert.equal(subject, 'Tailwind account disable request');
  assert.match(html, /rares/);
  assert.match(html, /22222222-2222-2222-2222-222222222222/);
});

test('render(tailwind-yearly-recap) fills the numbers and the links', () => {
  const { subject, html } = render('tailwind-yearly-recap', {
    username: 'rares',
    year: '2025',
    flights: '12',
    distance: '23,410 km',
    earthLaps: '0.6',
    moonTrips: '0.06',
    hours: '31',
    countries: '5',
    newCountries: 'Japan',
    longestRoute: 'OTP - HND',
    longestDistance: '8,900 km',
    topAirline: 'Tarom',
    recapUrl: 'http://localhost:5173/recap?year=2025',
    profileUrl: 'http://localhost:5173/profile',
  });

  assert.equal(subject, 'Your year in the air');
  assert.match(html, /Your 2025 in the air/);
  assert.match(html, /23,410 km/);
  assert.match(html, /new ones: Japan/);
  assert.match(html, /recap\?year(=|&#x3D;)2025/);
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
