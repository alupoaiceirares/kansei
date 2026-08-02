import http from 'node:http';

const PORT = 3000;

let ready = false;

export function markReady() {
  ready = true;
}

export function startHealthServer() {
  const server = http.createServer((req, res) => {
    if (ready) {
      res.writeHead(200, { 'Content-Type': 'text/plain' });
      res.end('ok');
    } else {
      res.writeHead(503, { 'Content-Type': 'text/plain' });
      res.end('not ready');
    }
  });
  server.listen(PORT);
  return server;
}
