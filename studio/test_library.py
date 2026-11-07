import copy
import tempfile
import unittest
from pathlib import Path
from library import Library, merge_libraries
from server import EMPTY

def sample():
    db=copy.deepcopy(EMPTY)
    db['bands'][0]['songs']=[{'id':'s1','title':'Uno','body':'[Am]ciao','bpm':120,'tr':0,'scroll':1,'notes':{'Tutti':'attacco'}}]
    db['bands'][0]['sets'][0]['items']=[{'type':'song','id':'s1'}]
    return db

class LibraryTests(unittest.TestCase):
    def test_dossier_merges_independent_checklist_and_contact_changes(self):
        base=sample();base['bands'][0]['events']=[{'id':'g1','title':'Serata'}]
        phone=copy.deepcopy(base);pc=copy.deepcopy(base)
        phone['bands'][0]['events'][0]['checklist']=[{'id':'c1','text':'Cavi','owner':'Basso','done':True}]
        pc['bands'][0]['events'][0].update(address='Via esempio',contacts=[{'id':'p1','name':'Fonico','role':'Audio','phone':''}],stagePlot=[{'id':'s1','label':'Basso','instrument':'Basso','x':20,'y':80}],inputs=[{'id':'i1','channel':'1','label':'Basso','mic':'DI','notes':''}])
        merged,_,conflicts=merge_libraries(base,phone,pc)
        self.assertEqual([],conflicts)
        gig=merged['bands'][0]['events'][0]
        self.assertTrue(gig['checklist'][0]['done']);self.assertEqual('Fonico',gig['contacts'][0]['name'])
        with tempfile.TemporaryDirectory() as tmp:
            lib=Library(Path(tmp)/'dossier.sqlite',merged)
            self.assertEqual(gig,lib.get()['db']['bands'][0]['events'][0])
    def test_invalid_stage_positions_cannot_be_saved(self):
        data=sample();data['bands'][0]['events']=[{'id':'g1','stagePlot':[{'id':'p1','x':120,'y':40}]}]
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError): Library(Path(tmp)/'invalid.sqlite',data)
    def test_web_delivery_requires_confirmation_before_tablet_deletes_pc_song(self):
        base=sample(); phone=copy.deepcopy(base)
        phone['bands'][0]['songs']=[];phone['bands'][0]['sets'][0]['items']=[]
        _,_,conflicts=merge_libraries(base,phone,base,delivery=True)
        self.assertTrue(any('/songs/s1' in p for p in conflicts))
        merged,_,_=merge_libraries(base,phone,base,'pc',delivery=True)
        self.assertEqual('s1',merged['bands'][0]['songs'][0]['id'])
        self.assertEqual(1,len(merged['bands'][0]['sets'][0]['items']))
    def test_web_delivery_cannot_silently_remove_a_pc_set_entry(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=sample(); device=copy.deepcopy(base)
            device['bands'][0]['sets'][0]['items']=[]
            lib=Library(Path(tmp)/'library.sqlite',base)
            result,status=lib.sync(base,device,delivery=True)
            self.assertEqual(409,status)
            self.assertTrue(any(p.endswith('/items') for p in result['conflicts']))
            self.assertEqual(1,len(lib.get()['db']['bands'][0]['sets'][0]['items']))
            result,status=lib.sync(base,device,'pc',result['revision'],delivery=True)
            self.assertEqual(200,status)
            self.assertEqual(1,len(result['db']['bands'][0]['sets'][0]['items']))
    def test_web_delivery_accepts_pc_only_append_without_a_conflict(self):
        base=sample(); device=copy.deepcopy(base); pc=copy.deepcopy(base)
        pc['bands'][0]['sets'][0]['items'].append({'type':'mark','label':'Encore','dur':'0:30'})
        result,_,conflicts=merge_libraries(base,device,pc,delivery=True)
        self.assertEqual([],conflicts)
        self.assertEqual(2,len(result['bands'][0]['sets'][0]['items']))
    def test_web_song_and_set_append_survive_repeated_android_round_trips(self):
        base=sample(); device=copy.deepcopy(base); pc=copy.deepcopy(base)
        for item in base['bands'][0]['sets'][0]['items']:
            item.update(label=None,dur=None)
        device=copy.deepcopy(base); pc=copy.deepcopy(base)
        pc['bands'][0]['songs'].append({'id':'s_new-uuid','title':'Nuovo dal web','body':'[C]prova','bpm':129})
        pc['bands'][0]['sets'][0]['items'].append({'type':'song','id':'s_new-uuid'})
        for _ in range(3):
            merged,pc,conflicts=merge_libraries(base,device,pc)
            self.assertEqual([],conflicts)
            self.assertEqual(['s1','s_new-uuid'],[i['id'] for i in merged['bands'][0]['sets'][0]['items']])
            for item in merged['bands'][0]['sets'][0]['items']: item.update(label=None,dur=None)
            base=copy.deepcopy(merged); device=copy.deepcopy(merged)
    def test_independent_edits_merge_and_personal_transposition_stays(self):
        base=sample(); device=copy.deepcopy(base); pc=copy.deepcopy(base)
        device['bands'][0]['songs'][0]['title']='Dal tablet'
        device['bands'][0]['songs'][0]['tr']=3
        pc['bands'][0]['songs'][0]['bpm']=96
        result,canonical,conflicts=merge_libraries(base,device,pc)
        self.assertEqual([],conflicts)
        self.assertEqual(('Dal tablet',96,3), tuple(result['bands'][0]['songs'][0][k] for k in ('title','bpm','tr')))
        self.assertEqual(0,canonical['bands'][0]['songs'][0]['tr'])
    def test_same_field_needs_an_explicit_choice(self):
        base=sample(); device=copy.deepcopy(base); pc=copy.deepcopy(base)
        device['bands'][0]['songs'][0]['bpm']=100; pc['bands'][0]['songs'][0]['bpm']=110
        result,_,conflicts=merge_libraries(base,device,pc,'device')
        self.assertEqual(1,len(conflicts)); self.assertEqual(100,result['bands'][0]['songs'][0]['bpm'])
    def test_delete_also_cleans_set_and_edit_delete_conflicts(self):
        base=sample(); device=copy.deepcopy(base); pc=copy.deepcopy(base)
        pc['bands'][0]['songs']=[]; pc['bands'][0]['sets'][0]['items']=[]
        result,_,conflicts=merge_libraries(base,device,pc)
        self.assertEqual([],conflicts); self.assertEqual([],result['bands'][0]['sets'][0]['items'])
        device['bands'][0]['songs'][0]['title']='New title'
        self.assertTrue(merge_libraries(base,device,pc)[2])
    def test_first_sync_adds_library_to_empty_pc(self):
        with tempfile.TemporaryDirectory() as tmp:
            lib=Library(Path(tmp)/'lib.sqlite',EMPTY)
            result,status=lib.sync(None,sample())
            self.assertEqual(200,status)
            self.assertEqual([],result['conflicts'])
            self.assertEqual('s1',result['db']['bands'][0]['songs'][0]['id'])
    def test_sqlite_is_durable_revision_checked_and_undoable(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'library.sqlite'; lib=Library(path,sample())
            changed=lib.get()['db']; changed['bands'][0]['songs'][0]['title']='Modificato'
            self.assertEqual(2,lib.save(changed,1)['revision'])
            with self.assertRaises(ValueError): lib.save(changed,1)
            self.assertEqual('Modificato',Library(path,EMPTY).get()['db']['bands'][0]['songs'][0]['title'])
            self.assertEqual('Uno',lib.undo(2)['db']['bands'][0]['songs'][0]['title'])
    def test_conflict_does_not_change_the_pc_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            lib=Library(Path(tmp)/'lib.sqlite',sample())
            base=sample(); pc=sample(); pc['bands'][0]['songs'][0]['title']='PC'
            lib.save(pc,1); phone=sample(); phone['bands'][0]['songs'][0]['title']='Phone'
            reply,status=lib.sync(base,phone)
            self.assertEqual(409,status); self.assertTrue(reply['conflicts'])
            self.assertEqual('PC',lib.get()['db']['bands'][0]['songs'][0]['title'])
    def test_independent_rehearsal_passages_merge_across_schema_upgrade(self):
        base=sample();phone=copy.deepcopy(base);pc=copy.deepcopy(base)
        phone['bands'][0]['practiceTasks']=[{'id':'t1','songId':'s1','goal':'Attacco','done':False}]
        pc['bands'][0]['practiceTasks']=[{'id':'t2','songId':'s1','goal':'Finale','done':True}]
        result,_,conflicts=merge_libraries(base,phone,pc)
        self.assertEqual([],conflicts)
        self.assertEqual({'t1','t2'},{t['id'] for t in result['bands'][0]['practiceTasks']})

if __name__=='__main__': unittest.main()
