"""Offline, noncanonical proposal export from an actual Falcon response shape.

Never produces a Framework KQ/KP/KA/KS identifier, an accepted Wikidata
crosswalk or a diK import decision. This utility is an opt-in observation
adapter for human/owner review; no network or canonical workspace I/O.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
from typing import Any

QID = re.compile(r'Q[1-9][0-9]*\Z')
PID = re.compile(r'P[1-9][0-9]*\Z')
FORMAT='sentient.falcon-candidate-observation/0.1-noncanonical'


class CandidateExportError(ValueError):
    pass


def observation(response:dict[str,Any], *, source_ref:str, evidence_sha256:str)->dict:
    """Convert Falcon ranked candidates to a review-only, untrusted handoff.

    The caller supplies the digest of independently preserved evidence. This
    function *does not authenticate it* or verify the upstream Wikidata origin.
    """
    if not isinstance(response,dict):
        raise CandidateExportError('Expected Falcon response object')
    if not isinstance(source_ref,str) or not 0 < len(source_ref) <= 512:
        raise CandidateExportError('Explicit bounded source reference required')
    if not isinstance(evidence_sha256,str) or not re.fullmatch(r'[0-9a-f]{64}',evidence_sha256):
        raise CandidateExportError('Explicit source SHA-256 required; not proof of external authenticity')
    items=response.get('ranked_candidates')
    if not isinstance(items,list) or len(items)>100:
        raise CandidateExportError('Falcon ranked_candidates must be bounded list')
    if 'error' in response:
        raise CandidateExportError('Falcon error cannot be exported as candidates')
    pid=response.get('inferred_property')
    if pid is not None and (not isinstance(pid,str) or not PID.fullmatch(pid)):
        raise CandidateExportError('Unrecognized external Wikidata property')
    candidates=[]; seen=set()
    for row in items:
        if not isinstance(row,dict) or set(row)!= {'id','falcon_score','semantic_reason'}:
            raise CandidateExportError('Unexpected Falcon candidate shape')
        qid=row['id'];score=row['falcon_score'];reason=row['semantic_reason']
        if not isinstance(qid,str) or not QID.fullmatch(qid) or qid in seen:
            raise CandidateExportError('Non-unique or invalid external QID; no implicit KQ minting')
        if isinstance(score,bool) or not isinstance(score,(int,float)) or not math.isfinite(score) or not 0<=score<=1:
            raise CandidateExportError('Falcon score must be finite numeric probability-like feature')
        if not isinstance(reason,str) or not reason or len(reason)>512:
            raise CandidateExportError('Missing/beyond-bounds explanation')
        candidates.append({'external_id':qid,'falcon_score':score,
                           'semantic_reason':reason,'review_status':'unreviewed_candidate'})
        seen.add(qid)
    # Fingerprint the observed response, not a newly approved mapping.
    stable=json.dumps(response,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode('utf-8')
    return {'format':FORMAT,'source_ref':source_ref,'source_evidence_sha256':evidence_sha256,
            'falcon_response_sha256':hashlib.sha256(stable).hexdigest(),
            'inferred_external_property':pid,'candidates':candidates,
            'external_source_authenticity':'not_verified',
            'canonical_graph_modified':False,'crosswalk_approved':False,
            'authority':'noncanonical_review_only',
            'note':'No KQ/KP/KA/KS identity, accepted exactMatch or diK import authorization is implied.'}
