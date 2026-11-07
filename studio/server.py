"""StagePilot Studio: local web app, SQLite and explicit Android synchronization."""
import argparse
import json
import mimetypes
import secrets
import socket
import sys
import threading
import webbrowser
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
from library import Library
from metadata import MetadataLookup
from transfers import TransferHub

ROOT = Path(__file__).resolve().parents[1]
EMPTY = {'v': 4, 'curBand': 'b1', 'opt': {}, 'bands': [{'id': 'b1', 'name': 'COPYCUT', 'me': 'Tutti', 'cur': 'set1', 'songs': [], 'sets': [{'id': 'set1', 'name': 'La mia prima scaletta', 'items': []}], 'events': [], 'rehearsals': []}]}

def lan_ip():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            s.connect(('192.0.2.1', 1))
            return s.getsockname()[0]
        except OSError: return '127.0.0.1'

def make_server(port=8765, data_dir=None):
    directory = Path(data_dir or ROOT/'studio/data')
    library = Library(directory/'stagepilot.sqlite', EMPTY)
    metadata = MetadataLookup()
    transfers = TransferHub()
    with library.connection() as c:
        row=c.execute("SELECT value FROM config WHERE key='pair_code'").fetchone()
        code=row[0] if row else ''.join(secrets.choice('0123456789') for _ in range(6))
        c.execute("INSERT OR IGNORE INTO config VALUES('pair_code',?)", (code,))
    class Handler(BaseHTTPRequestHandler):
        server_version='StagePilot/0.1'
        def log_message(self, *_): pass
        def local(self): return self.client_address[0] in ('127.0.0.1','::1')
        def authorized(self): return self.local() or secrets.compare_digest(self.headers.get('X-Code',''), code)
        def reply(self, data, status=200):
            raw=json.dumps(data,ensure_ascii=False).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type','application/json; charset=utf-8')
            self.send_header('Content-Length',str(len(raw)))
            self.send_header('Cache-Control','no-store')
            self.send_header('X-Content-Type-Options','nosniff')
            self.end_headers(); self.wfile.write(raw)
        def valid_host(self):
            host=urlsplit('http://'+self.headers.get('Host','')).hostname
            return host in ('127.0.0.1','localhost','::1',lan_ip())
        def do_GET(self):
            if not self.valid_host(): return self.reply({'error':'Indirizzo non valido.'},403)
            path=urlsplit(self.path).path
            if path=='/api/info':
                data={'name':'StagePilot', 'address': f'{lan_ip()}:{self.server.server_port}', 'requiresCode': not self.local()}
                if self.local(): data['code']=code
                return self.reply(data)
            if path.startswith('/api/'):
                if not self.authorized(): return self.reply({'error':'Inserisci il codice mostrato sul PC.'},403)
                if path=='/api/state': return self.reply(library.get())
                if path=='/api/transfer': return self.reply(transfers.status())
                if path=='/api/backup': return self.reply(library.get()['db'])
                return self.reply({'error':'Funzione non trovata.'},404)
            public=ROOT/'web/dist'
            resource=(public/('index.html' if path=='/' else path.lstrip('/'))).resolve()
            if not resource.is_relative_to(public.resolve()) or not resource.is_file(): return self.reply({'error':'Pagina non trovata. Compila lo studio web con npm run build dalla cartella web.'},404)
            raw=resource.read_bytes()
            mime=mimetypes.guess_type(resource)[0] or 'application/octet-stream'
            self.send_response(200); self.send_header('Content-Type',mime+'; charset=utf-8' if mime.startswith('text/') else mime)
            self.send_header('Content-Length',str(len(raw))); self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('Cache-Control','no-cache'); self.end_headers(); self.wfile.write(raw)
        def do_POST(self):
            if not self.valid_host() or not self.authorized(): return self.reply({'error':'Codice o indirizzo non valido.'},403)
            origin=self.headers.get('Origin')
            if origin and origin != 'http://'+self.headers.get('Host',''): return self.reply({'error':'Origine non valida.'},403)
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0 < length <= 12_000_000: return self.reply({'error':'File troppo grande o vuoto.'},413)
                data=json.loads(self.rfile.read(length).decode('utf-8'))
                if not isinstance(data,dict): raise ValueError('Richiesta non valida.')
                path=urlsplit(self.path).path
                if path=='/api/metadata': return self.reply(metadata.identify(data))
                if path=='/api/transfer/request': return self.reply(transfers.request(library.get()['revision']))
                if path=='/api/transfer/poll': return self.reply(transfers.poll(data))
                if path=='/api/transfer/finish': return self.reply(transfers.finish(data))
                if path=='/api/state': return self.reply(library.save(data['db'],data['revision']))
                if path=='/api/undo': return self.reply(library.undo(data['revision']))
                if path=='/api/sync':
                    result,status=library.sync(data.get('base'),data['db'],data.get('policy'),data.get('revision'),data.get('delivery',False))
                    return self.reply(result,status)
                return self.reply({'error':'Funzione non trovata.'},404)
            except (ValueError,KeyError,TypeError,UnicodeDecodeError) as e: return self.reply({'error':str(e)},409 if 'cambiata' in str(e) else 400)
            except Exception: return self.reply({'error':'Salvataggio non riuscito. I dati precedenti restano disponibili.'},500)
    server=ThreadingHTTPServer(('0.0.0.0',port),Handler)
    server.daemon_threads=True
    server.library=library
    return server

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--port',type=int,default=8765)
    parser.add_argument('--data-dir')
    parser.add_argument('--no-browser',action='store_true')
    args=parser.parse_args()
    try:
        server=make_server(args.port,args.data_dir)
    except OSError:
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{args.port}/api/info', timeout=1) as response:
                if json.load(response).get('name') == 'StagePilot':
                    print('StagePilot è già aperto su questo PC.',flush=True)
                    if not args.no_browser: webbrowser.open(f'http://127.0.0.1:{args.port}')
                    return
        except (OSError,ValueError): pass
        sys.exit(f'Non riesco ad aprire la porta {args.port}. Controlla se è usata da un altro programma o avvia con --port 8766.')
    url=f'http://127.0.0.1:{server.server_port}'
    print(f'StagePilot Studio: {url}',flush=True)
    print('Lascia questa finestra aperta. Ctrl+C per chiudere.',flush=True)
    if not args.no_browser: threading.Timer(.5,lambda:webbrowser.open(url)).start()
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()

if __name__=='__main__': main()
