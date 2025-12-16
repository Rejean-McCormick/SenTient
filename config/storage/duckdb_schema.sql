-- =============================================================================
-- SenTient Sidecar Storage Schema (DuckDB)
-- =============================================================================
-- Role: Off-Heap storage for heavy AI reconciliation results.
-- Strategy: Hybrid Memory Architecture (Hot data in RAM, Cold data on Disk).
-- Data Contract: Aligned with schemas/data/smart_cell.json
-- =============================================================================

-- 1. RECONCILIATION TABLE
-- Stores the full "EnhancedRecon" object for every processed cell.
-- We use a columnar store to allow OLAP-style faceting on 'judgment' and 'score'.
CREATE TABLE IF NOT EXISTS sentient_recon (
    -- Composite Key: Uniquely identifies a cell within a specific project history state
    project_id BIGINT NOT NULL,
    row_index INTEGER NOT NULL,
    
    -- Lightweight Metadata (Indexed for fast filtering/faceting)
    -- Must match 'status' in schemas/data/smart_cell.json
    judgment VARCHAR,        -- Enum: 'NEW', 'PENDING', 'AMBIGUOUS', 'MATCHED', 'REVIEW_REQUIRED'
    consensus_score FLOAT,   -- Range: 0.0 to 1.0
    
    -- Heavy Payloads (Serialized JSON to save RAM)
    -- Contains: { "vector": [...], "candidates": [{...}, {...}], "features": {...} }
    -- Strategy: Only loaded into RAM when the user scrolls this row into view.
    heavy_payload JSON,
    
    -- Timestamp for debugging and "Resume" capability
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (project_id, row_index)
);

-- 2. CORRECTION LOG (COMMUNITY FEEDBACK)
-- Stores manual user rejections to build training datasets.
-- Data Contract: Aligned with schemas/data/feedback_correction.json
CREATE TABLE IF NOT EXISTS sentient_feedback (
    id UUID PRIMARY KEY,
    project_id BIGINT,
    
    -- The "Input"
    surface_form VARCHAR,
    context_window VARCHAR[], -- Array of surrounding words
    
    -- The "Correction"
    rejected_id VARCHAR,      -- The QID the AI chose (e.g., Q90)
    accepted_id VARCHAR,      -- The QID the User chose (e.g., Q167646)
    
    -- Telemetry
    ai_score FLOAT,
    user_comment VARCHAR,     -- Optional explanation
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. INDEXES
-- Optimizes the "Instant Faceting" queries (e.g., "Show all Ambiguous matches")
CREATE INDEX IF NOT EXISTS idx_recon_judgment ON sentient_recon (project_id, judgment);
CREATE INDEX IF NOT EXISTS idx_recon_score ON sentient_recon (project_id, consensus_score);