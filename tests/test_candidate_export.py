"""Pure offline candidate adapter tests: no services, model or network required."""
import copy
import unittest
from src.candidate_export import observation, CandidateExportError, FORMAT

GOOD={'inferred_property':'P31','ranked_candidates':[{'id':'Q42','falcon_score':0.72,'semantic_reason':'Context overlap'}]}
SOURCE='sha256:'+('f'*64)

class CandidateExportTests(unittest.TestCase):
    def test_candidate_remains_unapproved_and_does_not_become_canonical(self):
        a=observation(GOOD,source_ref='test:source-1',evidence_sha256='f'*64)
        self.assertEqual(a['format'],FORMAT)
        self.assertEqual(a['candidates'][0]['external_id'],'Q42')
        self.assertEqual(a['candidates'][0]['review_status'],'unreviewed_candidate')
        self.assertFalse(a['crosswalk_approved'])
        self.assertFalse(a['canonical_graph_modified'])
        self.assertNotIn('canonical_id',a['candidates'][0])
        self.assertNotIn('exactMatch',a['candidates'][0])
        self.assertEqual(a,observation(GOOD,source_ref='test:source-1',evidence_sha256='f'*64))

    def test_external_qid_is_never_a_canonical_kq(self):
        for invalid in ('KQ42','Q0','Q-1','word','P31'):
            with self.subTest(invalid=invalid):
                response=copy.deepcopy(GOOD);response['ranked_candidates'][0]['id']=invalid
                with self.assertRaises(CandidateExportError):
                    observation(response,source_ref='test:x',evidence_sha256='a'*64)

    def test_duplicates_bad_score_and_falcon_error_denied(self):
        dup=copy.deepcopy(GOOD);dup['ranked_candidates'].append(dup['ranked_candidates'][0])
        with self.assertRaisesRegex(CandidateExportError,'Non-unique'):
            observation(dup,source_ref='test:x',evidence_sha256='a'*64)
        for bad in (float('nan'),float('inf'),True,-0.1,1.1):
            o=copy.deepcopy(GOOD);o['ranked_candidates'][0]['falcon_score']=bad
            with self.assertRaisesRegex(CandidateExportError,'score'):
                observation(o,source_ref='test:x',evidence_sha256='a'*64)
        with self.assertRaisesRegex(CandidateExportError,'error'):
            observation({'error':'failed','ranked_candidates':[]},source_ref='test:x',evidence_sha256='a'*64)

    def test_source_integrity_not_automatically_established(self):
        with self.assertRaisesRegex(CandidateExportError,'SHA-256'):
            observation(GOOD,source_ref='test:x',evidence_sha256='not-a-hash')
        with self.assertRaisesRegex(CandidateExportError,'source reference'):
            observation(GOOD,source_ref='',evidence_sha256='a'*64)

if __name__=='__main__': unittest.main()
