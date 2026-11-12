"""Jednoduchy LAN server pro HQS Wi-Fi Navi (bez zavislosti).

Port 8080: HTTP download. Port 5001: prijem TCP uploadu.
Spoustet pouze na duveryhodne siti LAN; nema autentizaci.
"""
import argparse
import http.server
import socket
import socketserver
import threading
import time

CHUNK = b"HQS-WIFI-TEST-" * 4681  # cca 64 KiB (deterministicka data)

class DownloadHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?", 1)[0] != "/download":
            self.send_error(404, "Pouzijte /download")
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(1024 * 1024 * 1024))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        remaining = 1024 * 1024 * 1024
        try:
            while remaining > 0:
                data = CHUNK[:min(len(CHUNK), remaining)]
                self.wfile.write(data)
                remaining -= len(data)
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass  # Telefon zamerne ukonci mereni po 10 s.

    def log_message(self, fmt, *args):
        print("[HTTP]", self.address_string(), fmt % args)

class HTTPServer(http.server.ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def tcp_receiver(host, port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((host, port))
        server.listen(5)
        print(f"[TCP ] Pripraveno: {host}:{port}", flush=True)
        while True:
            conn, peer = server.accept()
            threading.Thread(target=receive_one, args=(conn, peer), daemon=True).start()


def receive_one(conn, peer):
    with conn:
        conn.settimeout(30)
        count = 0
        start = time.monotonic()
        try:
            while True:
                block = conn.recv(256 * 1024)
                if not block:
                    break
                count += len(block)
        except (ConnectionError, TimeoutError, OSError):
            pass
        sec = max(time.monotonic() - start, 0.001)
        print(f"[TCP ] {peer[0]}: {count / 1048576:.1f} MiB / {sec:.2f} s = {count * 8 / sec / 1e6:.1f} Mbit/s", flush=True)


def main():
    ap = argparse.ArgumentParser(description="LAN server pro HQS Wi-Fi Navi")
    ap.add_argument("--host", default="0.0.0.0", help="Bind IP PC; vychozi vsechna rozhrani")
    ap.add_argument("--http-port", type=int, default=8080)
    ap.add_argument("--tcp-port", type=int, default=5001)
    args = ap.parse_args()
    t = threading.Thread(target=tcp_receiver, args=(args.host, args.tcp_port), daemon=True)
    t.start()
    httpd = HTTPServer((args.host, args.http_port), DownloadHandler)
    print(f"[HTTP] Pripraveno: http://IP_VASEHO_PC:{args.http_port}/download")
    print("Zastavite Ctrl+C. Povolte komunikaci ve firewallu pouze pro privatni sit.")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()

if __name__ == "__main__":
    main()
