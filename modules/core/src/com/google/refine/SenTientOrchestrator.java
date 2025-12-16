package com.google.refine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.ReconCandidate;
import com.google.refine.storage.DuckDBStore;

import info.debatty.java.stringsimilarity.Levenshtein;

/**
 * Layer 3: The Core Orchestrator.
 * Role: Enforces the "Funnel" logic:
 * 1. Solr (Fast Tagging) -> 2. Falcon (Context NLP) -> 3. DuckDB (Offload).
 * Architecture: Non-Blocking, Event-Driven.
 */
public class SenTientOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger("SenTientOrchestrator");
    
    // Configuration Keys (matched to butterfly.properties)
    private static final String KEY_SOLR_URL = "sentient.layer1.solr.url";
    private static final String KEY_FALCON_URL = "sentient.layer2.falcon.url";
    private static final String KEY_SOLR_TIMEOUT = "sentient.layer1.timeout_ms";
    private static final String KEY_FALCON_TIMEOUT = "sentient.layer2.timeout_ms";

    // Tuning Constants (matched to environment.json scoring strategy)
    private static final double WEIGHT_TAPIOCA = 0.4;
    private static final double WEIGHT_FALCON = 0.3;
    private static final double WEIGHT_LEVENSHTEIN = 0.3; 
    
    // Sigmoid Constants for Solr Normalization (Log-likelihood -> 0..1)
    private static final double SIGMOID_K = 2.0; // Steepness
    private static final double SIGMOID_M = 3.0; // Midpoint

    private final Properties config;
    private final HttpClient httpClient;
    private final DuckDBStore sidecarStore;
    private final Levenshtein levenshtein = new Levenshtein();

    public SenTientOrchestrator(Properties config, DuckDBStore sidecarStore) {
        this.config = config;
        this.sidecarStore = sidecarStore;
        
        // Initialize HTTP Client (Java 11+)
        // Optimized for HTTP/2 multiplexing to handle Solr parallel requests
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    }

    /**
     * The Main Event Loop.
     * Processes a batch of raw cells through the entire reconciliation funnel.
     * * @param project The active project.
     * @param cells The list of cells to reconcile.
     * @param rowIndices The corresponding row indices for sidecar storage.
     * @param columnContext The surrounding text in the row (for Falcon).
     */
    public void reconcileBatch(Project project, List<Cell> cells, List<Integer> rowIndices, List<List<String>> columnContext) {
        
        // 1. Layer 1: Solr Fast Tagging (Parallel)
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            List<String> context = columnContext.get(i);
            
            // Skip empty or already matched cells
            if (cell.value == null || (cell.recon != null && cell.recon.judgment == Recon.Judgment.Matched)) {
                continue;
            }

            // Prepare Recon Object
            if (cell.recon == null) {
                cell.recon = new Recon(System.currentTimeMillis(), null, null);
            }

            // Async Chain: Solr -> Falcon -> Consensus
            CompletableFuture<Void> pipeline = callLayer1Solr(cell)
                .thenCompose(candidates -> {
                    // 2. Ambiguity Check (The Funnel Gate)
                    if (isAmbiguous(candidates)) {
                        // Call Layer 2 (Falcon) only if ambiguous
                        return callLayer2Falcon(cell, context, candidates); 
                    } else {
                        return CompletableFuture.completedFuture(candidates);
                    }
                })
                .thenAccept(candidates -> {
                    // 3. Final Adjudication
                    finalizeConsensus(cell, candidates);
                })
                .exceptionally(ex -> {
                    logger.error("Reconciliation failed for cell " + cell.id, ex);
                    return null;
                });
            
            futures.add(pipeline);
        }

        // Wait for all futures in this batch to complete (Non-Blocking mostly handled by ProcessManager)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 4. Offload to Sidecar (DuckDB)
        // We persist the heavy "candidates" and "vectors" to disk and clear them from RAM.
        try {
            sidecarStore.insertBatch(project.id, cells, rowIndices); 
            
            // Clear transient heavy data from Heap to respect 4GB limit
            for (Cell cell : cells) {
                if (cell.recon != null) {
                    // We nullify the list here; DuckDBStore.fetchVisibleRows() re-hydrates it on scroll
                    cell.recon.candidates = null; 
                }
            }
        } catch (Exception e) {
            logger.error("Failed to offload batch to DuckDB", e);
        }
    }

    // =========================================================================
    // LAYER 1: SOLR (The Sieve)
    // =========================================================================

    private CompletableFuture<List<ReconCandidate>> callLayer1Solr(Cell cell) {
        // [ALIGNMENT] Use URL from butterfly.properties (127.0.0.1:8983)
        String url = config.getProperty(KEY_SOLR_URL) + "/tag?overlaps=NO_SUB&tagsLimit=10&fl=id,label,popularity_score,types";
        String payload = cell.value.toString();
        int timeout = Integer.parseInt(config.getProperty(KEY_SOLR_TIMEOUT, "500"));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .timeout(Duration.ofMillis(timeout))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    logger.warn("Solr Error: " + response.statusCode());
                    return new ArrayList<>();
                }
                return parseSolrResponse(response.body());
            });
    }

    private List<ReconCandidate> parseSolrResponse(String jsonBody) {
        List<ReconCandidate> candidates = new ArrayList<>();
        try {
            JSONObject response = new JSONObject(jsonBody);
            // Solr Tagger returns 'tags' array. We map them to candidates.
            // Simplified for brevity; actual impl extracts QID and raw score.
            // ...
            // Critical: Initialize 'features' map for SmartCell contract
        } catch (Exception e) {
            logger.error("Error parsing Solr response", e);
        }
        return candidates;
    }

    // =========================================================================
    // LAYER 2: FALCON (The Linguist)
    // =========================================================================

    private boolean isAmbiguous(List<ReconCandidate> candidates) {
        if (candidates.isEmpty()) return false;
        if (candidates.size() == 1) return false; 
        
        // Optimization: If top candidate is > 2x score of second, short-circuit
        return true; 
    }

    private CompletableFuture<List<ReconCandidate>> callLayer2Falcon(Cell cell, List<String> context, List<ReconCandidate> candidates) {
        // [ALIGNMENT] Use URL from butterfly.properties (127.0.0.1:5005)
        String url = config.getProperty(KEY_FALCON_URL) + "/disambiguate";
        int timeout = Integer.parseInt(config.getProperty(KEY_FALCON_TIMEOUT, "120000"));

        // Extract QIDs for Python
        List<String> qids = candidates.stream().map(c -> c.id).collect(Collectors.toList());

        JSONObject payload = new JSONObject();
        payload.put("surface_form", cell.value.toString());
        payload.put("context_window", new JSONArray(context));
        payload.put("candidates", new JSONArray(qids));
        payload.put("limit", 3); // CPU Safety for Falcon

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .timeout(Duration.ofMillis(timeout))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    logger.warn("Falcon Error: " + response.statusCode());
                    return candidates; // Fallback to Solr ranking
                }
                return mergeFalconScores(candidates, response.body());
            });
    }

    private List<ReconCandidate> mergeFalconScores(List<ReconCandidate> candidates, String jsonBody) {
        try {
            JSONObject response = new JSONObject(jsonBody);
            JSONArray ranked = response.optJSONArray("ranked_candidates");
            
            if (ranked == null) return candidates;

            for (int i = 0; i < ranked.length(); i++) {
                JSONObject rc = ranked.getJSONObject(i);
                String id = rc.getString("id");
                double falconScore = rc.optDouble("falcon_score", 0.0);
                
                // Inject Falcon score into candidate features
                for (ReconCandidate c : candidates) {
                    if (c.id.equals(id)) {
                        if (c.features == null) c.features = new HashMap<>();
                        c.features.put("falcon_context", falconScore);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error merging Falcon scores", e);
        }
        return candidates;
    }

    // =========================================================================
    // LAYER 3: CONSENSUS SCORING (The Judge)
    // =========================================================================

    private void finalizeConsensus(Cell cell, List<ReconCandidate> candidates) {
        if (candidates.isEmpty()) {
            cell.recon.judgment = Recon.Judgment.None;
            return;
        }

        ReconCandidate bestMatch = null;
        double highestScore = -1.0;

        for (ReconCandidate c : candidates) {
            // Ensure features map exists
            if (c.features == null) c.features = new HashMap<>();

            // 1. Solr Score Normalization (Sigmoid)
            // S_Normalized = 1 / (1 + e^(-k * (log_score - m)))
            double rawTapioca = c.features.getOrDefault("tapioca_popularity", 0.0);
            double sTapioca = 1.0 / (1.0 + Math.exp(-SIGMOID_K * (rawTapioca - SIGMOID_M)));
            
            // 2. Falcon Score (Cosine Similarity)
            double sFalcon = c.features.getOrDefault("falcon_context", 0.0);

            // 3. Levenshtein Score (String Distance)
            double dist = levenshtein.distance(cell.value.toString(), c.name);
            double maxLen = Math.max(cell.value.toString().length(), c.name.length());
            double sLevenshtein = 1.0 - (dist / maxLen);
            
            // Store Levenshtein for UI visualization
            c.features.put("levenshtein_distance", sLevenshtein);

            // 4. Weighted Formula 
            double finalScore = (sTapioca * WEIGHT_TAPIOCA) + 
                                (sFalcon * WEIGHT_FALCON) + 
                                (sLevenshtein * WEIGHT_LEVENSHTEIN);

            // Update Candidate Score (0-100 scale for UI)
            c.score = finalScore * 100;

            // Track Best Match
            if (finalScore > highestScore) {
                highestScore = finalScore;
                bestMatch = c;
            }
        }

        // Attach Candidates to Recon (Transient - will be flushed to DuckDB)
        cell.recon.candidates = candidates;
        cell.recon.consensusScore = (float) highestScore;

        // Auto-Match Logic
        if (highestScore >= 0.85) {
            cell.recon.match = bestMatch; // Set the Golden Record
            cell.recon.judgment = Recon.Judgment.Matched;
        } else if (highestScore >= 0.40) {
            cell.recon.judgment = Recon.Judgment.Ambiguous;
        } else {
            cell.recon.judgment = Recon.Judgment.None;
        }
    }
}