#!/usr/bin/env python3
"""Static server for the viewers (view.sh): like `python3 -m http.server`, but tells the browser never to cache, so a
git pull (new clips, viewer or baker code) shows up on a plain reload.

    python3 serve.py PORT --bind ADDR --directory REPO
"""
import argparse
import functools
import http.server

class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, *args):
        pass

p = argparse.ArgumentParser()
p.add_argument('port', type=int)
p.add_argument('--bind', default='127.0.0.1')
p.add_argument('--directory', required=True)
a = p.parse_args()
http.server.ThreadingHTTPServer((a.bind, a.port), functools.partial(NoCache, directory=a.directory)).serve_forever()
