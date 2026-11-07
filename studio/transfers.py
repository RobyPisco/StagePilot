"""Short-lived delivery requests. The tablet performs the normal safe merge."""
import re
import threading
import time


class TransferHub:
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.lock = threading.Lock()
        self.sequence = 0
        self.revision = 0
        self.requested = 0
        self.devices = {}

    def request(self, revision):
        with self.lock:
            self.sequence += 1
            self.revision = revision
            self.requested = self.clock()
            for device in self.devices.values():
                device.update(sequence=self.sequence, state='pending', issued=0)
            return self._status()

    def poll(self, data):
        device_id = data.get('device', '')
        ready = data.get('ready', False)
        if not isinstance(device_id, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', device_id) or type(ready) is not bool:
            raise ValueError('Tablet non valido.')
        with self.lock:
            now = self.clock()
            self.devices = {k: v for k, v in self.devices.items() if now - v['seen'] < 300}
            device = self.devices.setdefault(device_id, {'sequence': 0, 'state': 'idle', 'issued': 0})
            device.update(seen=now, ready=ready)
            active = self.sequence > 0 and now - self.requested < 120
            if active and device['sequence'] != self.sequence:
                device.update(sequence=self.sequence, state='pending', issued=0)
            sequence = 0
            if active and device['state'] in ('pending', 'held', 'in_progress'):
                if not ready:
                    if device['state'] != 'in_progress': device['state'] = 'held'
                elif device['state'] != 'in_progress' or now - device['issued'] >= 15:
                    sequence = self.sequence
                    device.update(state='in_progress', issued=now)
            return {'sequence': sequence}

    def finish(self, data):
        with self.lock:
            device = self.devices.get(data.get('device'))
            state = data.get('state')
            if not device or type(data.get('sequence')) is not int or state not in ('done', 'conflict', 'error', 'cancelled'):
                raise ValueError('Conferma del tablet non valida.')
            if data['sequence'] != self.sequence or device['sequence'] != self.sequence:
                return self._status()  # An old acknowledgment cannot finish a newer request.
            device.update(state=state, seen=self.clock())
            return self._status()

    def status(self):
        with self.lock: return self._status()

    def _status(self):
        now = self.clock()
        online = [d for d in self.devices.values() if now - d['seen'] < 20]
        current = [d for d in online if d['sequence'] == self.sequence]
        states = [d['state'] for d in current]
        if not self.sequence:
            state, message = 'idle', 'Tablet collegato e pronto.' if online else 'Apri StagePilot sul tablet e collega lo studio PC.'
        elif states and all(s == 'done' for s in states):
            state, message = 'done', 'Trasferimento completato: brani e scalette sono aggiornati sul tablet.'
        elif self.sequence and now - self.requested >= 120:
            state, message = 'expired', 'Il tablet non ha confermato. Apri StagePilot sul tablet e premi di nuovo Invia al tablet.'
        elif 'conflict' in states:
            state, message = 'conflict', 'Sul tablet ci sono modifiche da confrontare: scegli quale versione tenere.'
        elif 'error' in states or 'cancelled' in states:
            state, message = 'error', 'Trasferimento non completato. Controlla il messaggio sul tablet e riprova.'
        elif 'held' in states:
            state, message = 'held', 'Tablet sul palco o in modifica: torna a Oggi sul tablet per completare il trasferimento.'
        elif not online:
            state, message = 'waiting', 'In attesa del tablet. Apri StagePilot, con PC e tablet sulla stessa Wi-Fi.'
        else:
            state, message = 'sending', 'Trasferimento al tablet in corso…'
        return {'sequence': self.sequence, 'revision': self.revision, 'online': len(online), 'status': state, 'message': message}
