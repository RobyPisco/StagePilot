"""Prove senza rete delle parti pure di identifica.py. Si lanciano con: python -m unittest test_identifica"""
import unittest

import identifica as I


class IdentificaTest(unittest.TestCase):
    def test_titles_are_normalised(self):
        self.assertEqual(I.norm_title("Comfortably Numb (2011 Remaster)"), "comfortably numb")
        self.assertEqual(I.norm_title("Who Knew - Remastered 2011"), "who knew")
        self.assertEqual(I.norm_title("Hotel California [Live]"), "hotel california")

    def test_overlap_tells_the_right_song(self):
        pasted = I.trigrams(I.words("You took my hand you showed me how you promised me you'd be around"))
        same = "You took my hand, You showed me how\nYou promised me you'd be around, uh huh"
        other = "Hello is it me you're looking for\nI can see it in your eyes"
        self.assertGreater(I.overlap(pasted, same), 0.8)
        self.assertLess(I.overlap(pasted, other), 0.05)

    def test_apostrophes_do_not_break_matching(self):
        self.assertEqual(I.words("You’d be gone"), I.words("You'd be gone"))

    def test_shorter_variants_find_the_tail_of_a_phrase(self):
        v = I.with_shorter_variants(["have become comfortably numb", "pain you are receding"])
        self.assertIn("comfortably numb", v)
        self.assertIn("become comfortably numb", v)
        self.assertEqual(v[0], "have become comfortably numb")          # le originali restano in testa

    def test_duration_format(self):
        self.assertEqual(I.mmss(208), "3:28")
        self.assertEqual(I.mmss(382.4), "6:22")
        self.assertEqual(I.mmss(None), "")

    def test_empty_input_finds_nothing(self):
        self.assertEqual(I.overlap(set(), "qualcosa"), 0.0)


if __name__ == "__main__":
    unittest.main()
