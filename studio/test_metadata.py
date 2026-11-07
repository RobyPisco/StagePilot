import copy
import io
import unittest
import urllib.error
import urllib.parse
from metadata import MetadataLookup, artist_key, title_key, lyric_lines, title_queries, mmss

LYRICS = '\n'.join(['Carry home the northern light', 'Let the morning guide our way', 'Keep the fire through the rain', 'Carry home the northern light'] * 2)


class Catalog:
    def __init__(self, tempo=123.4, wrong_detail=False):
        self.calls = []
        self.tempo = tempo
        self.wrong_detail = wrong_detail
        self.rows = [
            {'id': 1, 'trackName': 'Northern Light', 'artistName': 'Cover Group', 'albumName': 'Covers', 'duration': 240, 'plainLyrics': LYRICS},
            {'id': 2, 'trackName': 'Northern Light', 'artistName': 'Original Group', 'albumName': 'Original Album', 'duration': 210, 'plainLyrics': LYRICS},
            {'id': 3, 'trackName': 'Other Song', 'artistName': 'Wrong Band', 'duration': 200, 'plainLyrics': 'Nothing here resembles the pasted words at all'},
        ]
        self.tracks = [
            {'id': 10, 'title': 'Northern Light', 'artist': {'name': 'Original Group'}, 'duration': 210, 'album': {'title': 'Original Album'}},
            {'id': 11, 'title': 'Northern Light (Live)', 'artist': {'name': 'Original Group'}, 'duration': 280, 'album': {'title': 'Concert'}},
            {'id': 12, 'title': 'Northern Light', 'artist': {'name': 'Cover Group'}, 'duration': 240, 'album': {'title': 'Covers'}},
        ]
    def __call__(self, url, timeout):
        self.calls.append(url)
        if 'lrclib' in url: return copy.deepcopy(self.rows)
        if '/search?' in url: return {'data': copy.deepcopy(self.tracks)}
        track_id = int(urllib.parse.urlsplit(url).path.rsplit('/', 1)[1])
        track = copy.deepcopy(next(t for t in self.tracks if t['id'] == track_id))
        track['bpm'] = self.tempo
        if self.wrong_detail: track['title'] = 'Other Song'
        return track


class MetadataTests(unittest.TestCase):
    def lookup(self, catalog):
        return MetadataLookup(fetch=catalog, pause=lambda _: None)
    def test_recognises_text_and_proposes_catalog_original_with_sources(self):
        result = self.lookup(Catalog()).identify({'body': '# Verse\n' + LYRICS.replace('Carry', '[Am]Carry')})
        selected = result['selected']
        self.assertTrue(result['found'])
        self.assertEqual(('Northern Light', 'Original Group', '3:30', 123), (selected['title'], selected['artist'], selected['dur'], selected['bpm']))
        self.assertEqual('Cover Group', result['candidates'][0]['artist'])
        self.assertTrue(any('LRCLIB' in s['label'] for s in selected['sources']))
        self.assertTrue(any('BPM' in s['label'] for s in selected['sources']))
        self.assertNotIn('plainLyrics', str(result))
    def test_unrelated_lyrics_never_count_as_a_recognition(self):
        result = self.lookup(Catalog()).identify({'body': 'We build another melody tonight\nThe mountains call us somewhere new'})
        self.assertFalse(result['found'])
        self.assertNotIn('selected', result)
    def test_missing_zero_or_invalid_bpm_never_gets_invented(self):
        for tempo in (0, None, 999, '123', float('nan')):
            with self.subTest(tempo=tempo):
                result = self.lookup(Catalog(tempo)).identify({'body': LYRICS})
                self.assertIsNone(result['selected']['bpm'])
                self.assertEqual('3:30', result['selected']['dur'])
    def test_other_recording_detail_does_not_supply_bpm(self):
        result = self.lookup(Catalog(wrong_detail=True)).identify({'body': LYRICS})
        self.assertIsNone(result['selected']['bpm'])
    def test_explicit_cover_selection_is_respected(self):
        result = self.lookup(Catalog()).identify({'body': LYRICS, 'title': 'Northern Light', 'artist': 'Cover Group'})
        self.assertEqual('Cover Group', result['selected']['artist'])
        self.assertEqual('4:00', result['selected']['dur'])
        self.assertTrue(result['selected']['recordingUrl'].endswith('/12'))
    def test_live_and_studio_versions_are_not_conflated(self):
        result = self.lookup(Catalog()).identify({'body': '[Am] [F]', 'title': 'Northern Light (Live)', 'artist': 'Original Group'})
        self.assertEqual('4:40', result['selected']['dur'])
        self.assertTrue(result['selected']['recordingUrl'].endswith('/11'))
        self.assertNotEqual(title_key('Northern Light'), title_key('Northern Light (Live)'))
    def test_cache_is_bounded_in_memory_and_results_cannot_mutate_it(self):
        catalog = Catalog(); service = self.lookup(catalog)
        first = service.identify({'body': LYRICS}); calls = len(catalog.calls)
        first['selected']['title'] = 'Changed in client'
        second = service.identify({'body': LYRICS})
        self.assertEqual(calls, len(catalog.calls))
        self.assertEqual('Northern Light', second['selected']['title'])
    def test_offline_retains_an_editable_failure_without_made_up_metadata(self):
        def offline(*_): raise OSError('Network unavailable')
        result = self.lookup(offline).identify({'body': LYRICS})
        self.assertFalse(result['found']); self.assertIn('cataloghi', result['message'])
    def test_rate_limit_honors_retry_after_without_retrying_the_provider(self):
        calls = []
        def limited(url, _):
            calls.append(url)
            raise urllib.error.HTTPError(url, 429, 'Limited', {'Retry-After': '120'}, io.BytesIO(b''))
        service = self.lookup(limited)
        service.identify({'body': LYRICS})
        service.identify({'body': LYRICS + '\nAnother line for another attempt'})
        self.assertEqual(1, len(calls))
    def test_input_validation_and_empty_input(self):
        service = self.lookup(Catalog())
        for data in ({'body': []}, {'body': 'x' * 80001}, {'title': 'x' * 201}):
            with self.assertRaises(ValueError): service.identify(data)
        self.assertFalse(service.identify({})['found'])
    def test_queries_remove_chords_and_normalise_accents_and_artist_punctuation(self):
        lines = lyric_lines('# Chorus\n[Am]Carry home the northern light\n[F]Carry home the northern light\ne|---0---|')
        self.assertIn('home the northern light', title_queries(lines))
        self.assertEqual(artist_key('AC/DC'), artist_key('AC⁄DC'))
        self.assertEqual('3:28', mmss(208.2))
        self.assertEqual('', mmss(float('nan')))


if __name__ == '__main__': unittest.main()
