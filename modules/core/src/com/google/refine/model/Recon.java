package com.google.refine.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Reconciliation State Object (SenTient Edition).
 * Role: Extends the OpenRefine 'Recon' model to support the Hybrid Memory Architecture.
 * Key Change: Heavy data (vectors, candidates) is marked 'transient' and is
 * NOT stored in the Project History JSON. It is hydrated on-demand from DuckDB.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Recon implements Serializable {
    
    private static final long serialVersionUID = 20251212L;

    // =========================================================================
    // 1. Persistent State (Stored in History / RAM)
    // =========================================================================

    public enum Judgment {
        @JsonProperty("none") None,
        @JsonProperty("matched") Matched,
        @JsonProperty("new") New,
        @JsonProperty("ambiguous") Ambiguous // Added for SenTient "Review Needed" flow
    }

    /**
     * Internal ID for the reconciliation attempt.
     */
    @JsonProperty("id")
    public long id;

    /**
     * The ID of the space/schema (e.g., Wikidata Entity URI).
     */
    @JsonProperty("identifierSpace")
    public String identifierSpace = "http://www.wikidata.org/entity/";

    /**
     * The ID of the schema space (e.g., Wikidata Property URI).
     */
    @JsonProperty("schemaSpace")
    public String schemaSpace = "http://www.wikidata.org/prop/direct/";

    /**
     * The User's Judgment (or Auto-Match status).
     */
    @JsonProperty("j")
    public Judgment judgment = Judgment.None;

    /**
     * The "Golden Record". The single candidate chosen as the correct match.
     * Persisted because it defines the cell's final identity.
     */
    @JsonProperty("m")
    public ReconCandidate match;

    /**
     * Tracking field for QA.
     * Stores the consensus score of the *match* if judgment is Matched.
     */
    @JsonProperty("score")
    public double matchRank = 0.0;

    // =========================================================================
    // 2. Transient State (Hydrated from Sidecar / DuckDB)
    // =========================================================================
    // These fields are loaded ONLY when the cell is visible in the UI Grid.
    // They are @JsonIgnore-d to prevent bloating the Project History file.

    /**
     * The list of potential matches returned by Solr/Falcon.
     * Heavy Object: Contains descriptions, types, and labels.
     */
    @JsonIgnore 
    public transient List<ReconCandidate> candidates;

    /**
     * The final calculated confidence (0.0 - 1.0).
     * Used for the "Confidence Bar" visualization in the UI.
     */
    @JsonIgnore
    public transient float consensusScore;

    /**
     * Debugging telemetry / Feature Vector.
     * Contains { "tapioca_popularity": 0.9, "falcon_context": 0.2, ... }
     * [ALIGNMENT] Named 'features' to match SenTientOrchestrator and DuckDBStore.
     */
    @JsonIgnore
    public transient Map<String, Double> features;

    // =========================================================================
    // 3. Constructors & Logic
    // =========================================================================

    public Recon(long id, String identifierSpace, String schemaSpace) {
        this.id = id;
        this.identifierSpace = identifierSpace;
        this.schemaSpace = schemaSpace;
        this.candidates = Collections.emptyList();
    }

    /**
     * Sets the Judgment to Matched and updates the matchRank.
     */
    public void match(ReconCandidate candidate) {
        this.judgment = Judgment.Matched;
        this.match = candidate;
        this.matchRank = candidate.score;
    }

    /**
     * Helper to retrieve the highest scoring candidate (usually index 0).
     */
    public ReconCandidate getBestCandidate() {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0);
        }
        return null;
    }
}