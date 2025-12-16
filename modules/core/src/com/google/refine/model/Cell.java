// modules\core\src\com\google\refine\model\Cell.java
package com.google.refine.model;

import java.io.Serializable;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Atomic Unit of Data in SenTient (Layer 3).
 * Role: Represents the "SmartCell" artifact. Unlike standard OpenRefine cells,
 * SenTient cells carry a unique UUID and a structural fingerprint to enable
 * distributed reconciliation across the Hybrid Architecture.
 * Schema: schemas/data/smart_cell.json
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Cell implements Serializable {
    
    private static final long serialVersionUID = 20251212L;

    // =========================================================================
    // 1. Standard OpenRefine Fields
    // =========================================================================
    
    /**
     * The raw value of the cell.
     * Can be String, Number, Boolean, or OffsetDateTime.
     * Maps to 'raw_value' in the logical Data Dictionary, but serialized as 'v' 
     * to maintain compatibility with legacy OpenRefine GREL expressions.
     */
    @JsonProperty("v")
    public Serializable value;

    /**
     * The reconciliation state container.
     * If null, the cell is in the 'NEW' state.
     * Serialized as 'r'.
     */
    @JsonProperty("r")
    public Recon recon;

    // =========================================================================
    // 2. SenTient Extensions (SmartCell Protocol)
    // =========================================================================

    /**
     * Unique tracking ID (UUID v4).
     * Essential for mapping Async NLP responses (Layer 2) back to the specific
     * cell in the UI Grid, especially during virtualization/scroll.
     */
    @JsonProperty("id")
    public String id;

    /**
     * The Key Collision Fingerprint.
     * Generated during Ingestion (Layer 0) via: lowercase(sort(tokenize(value))).
     * Used for local clustering and cache lookups (Redis).
     */
    @JsonProperty("fp")
    public String fingerprint;

    // =========================================================================
    // 3. Constructors
    // =========================================================================

    public Cell(Serializable value, Recon recon) {
        this.value = value;
        this.recon = recon;
        // Auto-generate UUID on creation to ensure traceability
        this.id = UUID.randomUUID().toString();
    }

    /**
     * JSON Creator for deserialization from the Frontend or History storage.
     * Allows restoring the exact UUID and Fingerprint from disk/network.
     */
    @JsonCreator
    public Cell(
        @JsonProperty("v") Serializable value,
        @JsonProperty("r") Recon recon,
        @JsonProperty("id") String id,
        @JsonProperty("fp") String fingerprint
    ) {
        this.value = value;
        this.recon = recon;
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.fingerprint = fingerprint;
    }

    // =========================================================================
    // 4. Utility Methods
    // =========================================================================

    /**
     * @return True if this cell has been matched to a specific identity (Status: MATCHED).
     */
    public boolean isReconciled() {
        return recon != null && recon.judgment == Recon.Judgment.Matched;
    }

    /**
     * @return True if the cell contains data (non-null and non-empty string).
     */
    public boolean hasValue() {
        return value != null && !value.toString().isEmpty();
    }

    @Override
    public String toString() {
        return String.format("SmartCell[id=%s, val='%s', status=%s]", 
            id, value, (recon == null ? "NEW" : recon.judgment));
    }
}