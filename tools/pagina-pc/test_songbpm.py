"""Prove senza rete di songbpm.py. Si lanciano con: python -m unittest test_songbpm"""
import unittest

import songbpm as S


def card(url, artist, title, key, dur, bpm):
    return (f'<a class="hover:bg-foreground/5 flex" href="{url}"><div><p class="text-sm uppercase"> {artist} </p>'
            f'<p class="text-lg"> {title} </p></div>'
            f'<div><span class="text-xs uppercase">Key</span> <span class="x">{key}</span></div>'
            f'<div><span class="text-xs uppercase">Duration</span> <span class="x"> {dur} </span></div>'
            f'<div><span class="text-xs uppercase">BPM</span> <span class="x"> {bpm} </span></div></a>')


PAGE = "<html>" + "".join([
    card("/@pnk/who-knew-live", "P!nk", "Who Knew - Live", "A", "4:10", 141),
    card("/@pnk/who-knew-edit", "P!nk", "Who Knew - Edit", "A", "3:28", 140),
    card("/@pnk/who-knew", "P!nk", "Who Knew", "A", "3:29", 140),
    card("/@other/who-knew", "Altro Gruppo", "Who Knew", "C", "3:00", 99),
    card("/@outfield/your-love", "The Outfield", "Your Love", "C&#9839;/D&#9837;", "3:43", 130),
]) + "</html>"


class SongBpmTest(unittest.TestCase):
    def test_results_are_read(self):
        r = S.parse_results(PAGE)
        self.assertEqual(len(r), 5)
        self.assertEqual((r[1]["artist"], r[1]["title"], r[1]["dur"], r[1]["bpm"]), ("P!nk", "Who Knew - Edit", "3:28", 140))
        self.assertEqual(r[4]["key"], "C♯/D♭")

    def test_picks_same_song_and_artist(self):
        r = S.parse_results(PAGE)
        best = S.pick(r, "Who Knew", "P!nk")
        self.assertEqual(best["artist"], "P!nk")
        self.assertNotIn("Live", best["title"])

    def test_closest_duration_decides_between_versions(self):
        r = S.parse_results(PAGE)
        self.assertEqual(S.pick(r, "Who Knew", "Pink", 209)["dur"], "3:29")

    def test_other_artist_is_not_taken(self):
        self.assertIsNone(S.pick(S.parse_results(PAGE), "Who Knew", "Gruppo Sconosciuto"))

    def test_page_without_results_or_changed(self):
        self.assertEqual(S.parse_results("<html>niente</html>"), [])
        self.assertEqual(S.parse_results(None), [])

    def test_duration_to_seconds(self):
        self.assertEqual(S.secs("3:28"), 208)
        self.assertEqual(S.secs(""), 0)


if __name__ == "__main__":
    unittest.main()
