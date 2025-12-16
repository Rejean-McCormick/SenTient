package com.google.refine.storage;

import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONObject;
import org.json.JSONArray;
import com.google.refine.model.Cell;
import com.google.refine.model.Recon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Sidecar Database Adapter.
 * Role: Implements the "Hybrid Memory Architecture" pattern:
 * - Offloads heavy AI reconciliation data (Vectors, Candidates) to a local Columnar Store (DuckDB).
 * - Prevents Java Heap crashes ("OutOfMemoryError") when processing large datasets (e.g., 5GB CSV).
 * - Provides sub-10ms OLAP aggregation for UI faceting.
 */
public class DuckDBStore {
    
    private static final Logger logger = LoggerFactory.getLogger("DuckDBStore");
    
    private Connection conn;
    private final String dbPath;
    private static final String TABLE_RECON = "sentient_recon";
    private static final String TABLE_FEEDBACK = "sentient_feedback";

    public DuckDBStore(String storagePath) {
        // Ensure the path ends with the correct extension
        this.dbPath = storagePath.endsWith(".duckdb") ? storagePath : storagePath + "/sentient_cache.duckdb";
    }

    /**
     * Initializes the DB connection and ensures the schema exists.
     * Uses the Write-Ahead Log (WAL) for concurrency.
     */
    public void init() throws SQLException {
        try {
            // Load the DuckDB JDBC driver
            Class.forName("org.duckdb.DuckDBDriver");
            this.conn = DriverManager.getConnection("jdbc:duckdb:" + this.dbPath);
            
            // Enable WAL mode for better concurrency (Write-Aware)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
            }

            // Create Tables (Idempotent)
            createSchema();
            
            logger.info("DuckDB Sidecar initialized at: " + this.dbPath);

        } catch (ClassNotFoundException e) {
            throw new SQLException("DuckDB Driver not found. Ensure the JAR is on the classpath.", e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 1. Reconciliation Table (Heavy Storage)
            // Aligned with config/storage/duckdb_schema.sql
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_RECON + " (" +
                         "project_id BIGINT NOT NULL, " +
                         "row_index INTEGER NOT NULL, " +
                         "judgment VARCHAR, " +
                         "consensus_score FLOAT, " +
                         "heavy_payload JSON, " + // Stores vectors & candidates
                         "processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                         "PRIMARY KEY (project_id, row_index))");

            // 2. Feedback Table (Community Training Data)
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_FEEDBACK + " (" +
                         "id UUID PRIMARY KEY, " +
                         "project_id BIGINT, " +
                         "surface_form VARCHAR, " +
                         "context_window VARCHAR[], " +
                         "rejected_id VARCHAR, " +
                         "accepted_id VARCHAR, " +
                         "ai_score FLOAT, " +
                         "user_comment VARCHAR, " +
                         "logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            // 3. Performance Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_recon_judgment ON " + TABLE_RECON + " (project_id, judgment)");
        }
    }

    /**
     * Phase A: The Fast Write (Background Process)
     * Offloads a batch of processed cells to disk immediately to free up RAM.
     */
    public void insertBatch(long projectId, List<Cell> cells, List<Integer> rowIndices) throws SQLException {
        String sql = "INSERT INTO " + TABLE_RECON + 
                     " (project_id, row_index, judgment, consensus_score, heavy_payload) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON CONFLICT (project_id, row_index) DO UPDATE SET " +
                     "judgment = EXCLUDED.judgment, " +
                     "consensus_score = EXCLUDED.consensus_score, " +
                     "heavy_payload = EXCLUDED.heavy_payload, " +
                     "processed_at = CURRENT_TIMESTAMP";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                int rowIndex = rowIndices.get(i);
                
                if (cell.recon == null) continue;

                pstmt.setLong(1, projectId);
                pstmt.setInt(2, rowIndex);
                
                // Store lightweight metadata for faceting
                String judgmentStr = (cell.recon.judgment == null) ? "None" : cell.recon.judgment.name();
                pstmt.setString(3, judgmentStr);
                
                // [ALIGNMENT] Access consensusScore directly from Recon (Standardized with SenTientOrchestrator)
                // Default to 0.0 if not yet calculated
                float score = (cell.recon.consensusScore > 0) ? cell.recon.consensusScore : 0.0f;
                pstmt.setFloat(4, score);

                // Serialize the heavy AI data (Candidates, Vectors) to JSON
                JSONObject payload = new JSONObject();
                if (cell.recon.candidates != null) {
                    payload.put("candidates", new JSONArray(cell.recon.candidates));
                }
                // [ALIGNMENT] Check featureVector availability
                if (cell.recon.features != null && !cell.recon.features.isEmpty()) {
                    payload.put("features", new JSONObject(cell.recon.features));
                }
                
                pstmt.setString(5, payload.toString());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    /**
     * Phase B: The View Read (Virtual Grid)
     * Fetches only the necessary heavy data for the currently visible rows (Pagination).
     * @return A ResultSet containing the JSON payloads for the requested range.
     */
    public ResultSet fetchVisibleRows(long projectId, int start, int limit) throws SQLException {
        String sql = "SELECT row_index, heavy_payload FROM " + TABLE_RECON + 
                     " WHERE project_id = ? " +
                     " AND row_index >= ? AND row_index < ? " +
                     " ORDER BY row_index ASC";
        
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setLong(1, projectId);
        pstmt.setInt(2, start);
        pstmt.setInt(3, start + limit);
        
        // Caller is responsible for closing the ResultSet
        return pstmt.executeQuery();
    }
    
    /**
     * Phase C: Instant Faceting (OLAP Speed)
     * Counts matches by Judgment status without loading any objects into Java Heap.
     */
    public int countByJudgment(long projectId, String judgment) throws SQLException {
        String sql = "SELECT count(*) FROM " + TABLE_RECON + 
                     " WHERE project_id = ? AND judgment = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, projectId);
            pstmt.setString(2, judgment);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Community Feature: Log User Feedback
     * Stores manual corrections to help retrain the SBERT/Solr models.
     */
    public void logCorrection(long projectId, String surfaceForm, String rejectedId, String acceptedId, String comment) throws SQLException {
        String sql = "INSERT INTO " + TABLE_FEEDBACK + 
                     " (id, project_id, surface_form, rejected_id, accepted_id, user_comment) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
                      
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, UUID.randomUUID());
            pstmt.setLong(2, projectId);
            pstmt.setString(3, surfaceForm);
            pstmt.setString(4, rejectedId);
            pstmt.setString(5, acceptedId);
            pstmt.setString(6, comment);
            pstmt.executeUpdate();
        }
    }

    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.warn("Error closing DuckDB connection", e);
        }
    }
}