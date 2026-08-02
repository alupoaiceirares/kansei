import { test } from 'node:test';
import assert from 'node:assert/strict';
import { handleMailEvent } from './index.js';

test('handleMailEvent renders the template and sends the result', async () => {
  const sent = [];
  const fakeDeps = {
    render: (template, vars) => ({ subject: `subject-for-${template}`, html: `<p>${vars.username}</p>` }),
    sendMail: async (mail) => { sent.push(mail); },
  };

  await handleMailEvent(
    { to: 'user@example.com', template: 'email-verification', vars: { username: 'rares', link: 'http://x' } },
    fakeDeps
  );

  assert.equal(sent.length, 1);
  assert.deepEqual(sent[0], {
    to: 'user@example.com',
    subject: 'subject-for-email-verification',
    html: '<p>rares</p>',
  });
});

test('handleMailEvent propagates a render failure without calling sendMail', async () => {
  let sendMailCalled = false;
  const fakeDeps = {
    render: () => { throw new Error('boom'); },
    sendMail: async () => { sendMailCalled = true; },
  };

  await assert.rejects(
    () => handleMailEvent({ to: 'user@example.com', template: 'x', vars: {} }, fakeDeps),
    /boom/
  );
  assert.equal(sendMailCalled, false);
});

test('handleMailEvent propagates a sendMail failure', async () => {
  const fakeDeps = {
    render: () => ({ subject: 's', html: 'h' }),
    sendMail: async () => { throw new Error('smtp down'); },
  };

  await assert.rejects(
    () => handleMailEvent({ to: 'user@example.com', template: 'x', vars: {} }, fakeDeps),
    /smtp down/
  );
});
