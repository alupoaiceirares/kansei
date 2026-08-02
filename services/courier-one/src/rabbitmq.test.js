import { test } from 'node:test';
import assert from 'node:assert/strict';
import { retryCount } from './rabbitmq.js';

function fakeMessage(headers) {
  return { properties: { headers } };
}

test('retryCount returns 0 when there is no x-death header', () => {
  assert.equal(retryCount(fakeMessage({})), 0);
  assert.equal(retryCount(fakeMessage(undefined)), 0);
});

test('retryCount returns 0 when x-death has no entry for courier-one.queue/rejected', () => {
  const msg = fakeMessage({
    'x-death': [{ queue: 'some-other-queue', reason: 'rejected', count: 5 }],
  });
  assert.equal(retryCount(msg), 0);
});

test('retryCount reads the count from the matching courier-one.queue/rejected entry', () => {
  const msg = fakeMessage({
    'x-death': [
      { queue: 'courier-one.queue.retry', reason: 'expired', count: 2 },
      { queue: 'courier-one.queue', reason: 'rejected', count: 3 },
    ],
  });
  assert.equal(retryCount(msg), 3);
});
