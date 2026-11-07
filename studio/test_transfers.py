import unittest
from transfers import TransferHub


class TransferTests(unittest.TestCase):
    def setUp(self):
        self.now = 100
        self.hub = TransferHub(clock=lambda: self.now)
    def poll(self, ready=True): return self.hub.poll({'device':'tablet-1','ready':ready})['sequence']
    def finish(self, seq, state='done'): return self.hub.finish({'device':'tablet-1','sequence':seq,'state':state})
    def test_web_request_is_delivered_once_and_requires_tablet_confirmation(self):
        self.assertEqual(0, self.poll())
        self.assertEqual('sending', self.hub.request(12)['status'])
        seq = self.poll(); self.assertGreater(seq, 0)
        self.assertEqual(0, self.poll())
        self.assertNotEqual('done', self.hub.status()['status'])
        self.assertEqual('done', self.finish(seq)['status'])
        self.assertEqual(0, self.poll())
    def test_performance_and_editing_hold_delivery_until_tablet_is_ready(self):
        self.poll(False); self.hub.request(12)
        self.assertEqual(0, self.poll(False))
        self.assertEqual('held', self.hub.status()['status'])
        self.assertGreater(self.poll(True), 0)
    def test_request_waits_for_new_tablet_and_expires_without_an_ack(self):
        self.assertEqual('waiting', self.hub.request(12)['status'])
        self.assertGreater(self.poll(), 0)
        self.now += 121
        self.assertEqual(0, self.poll())
        self.assertEqual('expired', self.hub.status()['status'])
    def test_old_confirmation_cannot_complete_a_new_request(self):
        self.hub.request(12); first=self.poll()
        self.hub.request(13); second=self.poll()
        self.assertNotEqual('done', self.finish(first)['status'])
        self.assertEqual('done', self.finish(second)['status'])
    def test_conflict_waits_for_user_resolution_without_repeated_sync(self):
        self.hub.request(12); seq=self.poll()
        self.assertEqual('conflict', self.finish(seq,'conflict')['status'])
        self.now += 17; self.assertEqual(0,self.poll())
        self.assertEqual('done', self.finish(seq)['status'])
    def test_interrupted_delivery_can_be_retried_after_lease_expires(self):
        self.hub.request(12); seq=self.poll()
        self.now += 16
        self.assertEqual(seq,self.poll())
    def test_invalid_device_and_ack_are_rejected(self):
        for data in ({'device':'../path','ready':True},{'device':'tablet','ready':'yes'}):
            with self.assertRaises(ValueError): self.hub.poll(data)
        with self.assertRaises(ValueError): self.finish(1)


if __name__=='__main__': unittest.main()
