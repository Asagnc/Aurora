#!/usr/bin/env python3
"""Temporary loopback CONNECT fixture for fixed official tool downloads only.

Pair with an explicitly owned adb reverse mapping and the opt-in installation
fixture. TLS remains between Android and the official host: no CA, interception,
credentials, archive cache or device/global proxy configuration is involved.
The orchestrator must terminate this process and remove its own reverse mapping.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import select
import socket

HOSTS = frozenset(("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
                  "downloads.mitmproxy.org", "dl.google.com", "maven.google.com", "repo.maven.apache.org", "plugins.gradle.org",
                  "plugins-artifacts.gradle.org", "repo.gradle.org", "services.gradle.org", "downloads.gradle.org"))


class Tunnel(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_args):
        pass  # Signed redirect URLs and HTTP headers never enter logs.

    def do_CONNECT(self):
        pieces = self.path.rsplit(":", 1)
        if len(pieces) != 2 or pieces[0] not in HOSTS or pieces[1] != "443":
            host = pieces[0] if pieces and all(c in "abcdefghijklmnopqrstuvwxyz0123456789.-" for c in pieces[0]) else "invalid_authority"
            print("CONNECT_DENIED " + host, flush=True)
            self.send_error(403)
            return
        self.close_connection = True
        try:
            with socket.create_connection((pieces[0], 443), timeout=15) as upstream:
                print("CONNECT_READY " + pieces[0], flush=True)
                self.connection.settimeout(30)
                upstream.settimeout(30)
                self.send_response(200, "Connection established")
                self.end_headers()
                self.wfile.flush()
                self.relay(upstream)
        except (OSError, TimeoutError) as failure:
            print("CONNECT_FAILED " + pieces[0] + " " + type(failure).__name__, flush=True)

    def relay(self, upstream):
        peers = (self.connection, upstream)
        while True:
            readable, _, _ = select.select(peers, (), (), 30)
            if not readable:
                return
            for source in readable:
                data = source.recv(65536)
                if not data:
                    return
                (upstream if source is self.connection else self.connection).sendall(data)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=0)
    args = parser.parse_args()
    with ThreadingHTTPServer(("127.0.0.1", args.port), Tunnel) as server:
        server.daemon_threads = True
        print("READY " + str(server.server_port), flush=True)
        server.serve_forever()
