import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { startHealthServer, markReady } from './health.js';

function get(port) {
  return new Promise((resolve, reject) => {
    http.get(`http://localhost:${port}`, (res) => resolve(res.statusCode)).on('error', reject);
  });
}

test('health server returns 503 until markReady is called, then 200', async () => {
  const server = startHealthServer(3000);
  try {
    assert.equal(await get(3000), 503);
    markReady();
    assert.equal(await get(3000), 200);
  } finally {
    server.close();
  }
});
