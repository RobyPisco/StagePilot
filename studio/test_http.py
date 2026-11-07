import copy
import json
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from unittest.mock import patch
from server import make_server, EMPTY

class HttpTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.server=make_server(0,self.tmp.name)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.url=f'http://127.0.0.1:{self.server.server_port}'
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.tmp.cleanup()
    def call(self,path,data=None,headers=None):
        req=urllib.request.Request(self.url+path,data=None if data is None else json.dumps(data).encode(),headers=headers or {})
        try:
            with urllib.request.urlopen(req,timeout=5) as response:return response.status,json.load(response)
        except urllib.error.HTTPError as error:return error.code,json.load(error)
    def test_crud_persists_and_stale_browser_cannot_overwrite(self):
        snap=self.call('/api/state')[1];db=snap['db'];b=db['bands'][0]
        b['songs']=[{'id':'s1','title':'Test','artist':'Band','body':'[Am]hello','bpm':120}]
        b['sets'][0]['items']=[{'type':'song','id':'s1'}]
        self.assertEqual(200,self.call('/api/state',{'revision':1,'db':db})[0])
        self.assertEqual(409,self.call('/api/state',{'revision':1,'db':EMPTY})[0])
        self.assertEqual('Test',self.call('/api/state')[1]['db']['bands'][0]['songs'][0]['title'])
        self.assertEqual('Test',self.call('/api/backup')[1]['bands'][0]['songs'][0]['title'])
    def test_remote_client_needs_code_and_foreign_origin_is_rejected(self):
        info=self.call('/api/info')[1]
        with patch.object(self.server.RequestHandlerClass,'local',return_value=False):
            self.assertEqual(403,self.call('/api/state')[0])
            self.assertEqual(200,self.call('/api/state',headers={'X-Code':info['code']})[0])
            self.assertNotIn('code',self.call('/api/info')[1])
        self.assertEqual(403,self.call('/api/state',{'db':EMPTY,'revision':1},{'Origin':'https://unrelated.example'})[0])
    def test_invalid_set_reference_does_not_change_library(self):
        bad=copy.deepcopy(EMPTY);bad['bands'][0]['sets'][0]['items']=[{'type':'song','id':'missing'}]
        self.assertEqual(400,self.call('/api/state',{'db':bad,'revision':1})[0])
        self.assertEqual(1,self.call('/api/state')[1]['revision'])
    def test_metadata_lookup_is_authenticated_and_does_not_modify_library(self):
        with patch('server.MetadataLookup.identify', return_value={'found':False,'candidates':[],'message':'No match'}) as identify:
            self.assertEqual(200,self.call('/api/metadata',{'body':'A pasted chart'})[0])
            identify.assert_called_once_with({'body':'A pasted chart'})
            with patch.object(self.server.RequestHandlerClass,'local',return_value=False):
                self.assertEqual(403,self.call('/api/metadata',{'body':'Private draft'})[0])
            self.assertEqual(1,self.call('/api/state')[1]['revision'])
    def test_conflict_confirmation_must_match_reviewed_revision(self):
        base=copy.deepcopy(EMPTY)
        base['bands'][0]['songs']=[{'id':'s1','title':'Base','body':'[Am]x'}]
        self.call('/api/state',{'db':base,'revision':1})
        pc=copy.deepcopy(base);pc['bands'][0]['songs'][0]['title']='PC'
        self.call('/api/state',{'db':pc,'revision':2})
        phone=copy.deepcopy(base);phone['bands'][0]['songs'][0]['title']='Phone'
        status,conflict=self.call('/api/sync',{'db':phone,'base':base})
        self.assertEqual(409,status)
        self.assertEqual(409,self.call('/api/sync',{'db':phone,'base':base,'policy':'device','revision':2})[0])
        self.assertEqual(200,self.call('/api/sync',{'db':phone,'base':base,'policy':'device','revision':conflict['revision']})[0])
        self.assertEqual('Phone',self.call('/api/state')[1]['db']['bands'][0]['songs'][0]['title'])

if __name__=='__main__':unittest.main()
