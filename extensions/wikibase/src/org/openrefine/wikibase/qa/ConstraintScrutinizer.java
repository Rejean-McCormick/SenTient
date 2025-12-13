package org.openrefine.wikibase.qa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.openrefine.wikibase.updates.ItemUpdate;
import org.openrefine.wikibase.updates.StatementEdit;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.TimeValue;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;

/**
 * The Constraint Scrutinizer (QA Layer).
 * * Role: Enforces logical consistency and schema constraints on the data.
 * Config Source: config/qa/scrutinizer_rules.yaml
 * * Implements checks for:
 * 1. Date Validity: ISO 8601 compliance.
 * 2. Chronology Conflicts: Birth Date (P569) > Death Date (P570).
 * 3. Cardinality Violations: Single-value constraints (e.g., Capital P36).
 */
public class ConstraintScrutinizer extends EditScrutinizer {

    [cite_start]// Properties configured for Single Value checks [cite: 52]
    private static final Set<String> SINGLE_VALUE_PROPERTIES = Set.of("P36", "P1376");
    
    [cite_start]// Regex for ISO 8601 strict validation [cite: 50]
    private static final Pattern ISO8601_PATTERN = Pattern.compile("^\\+?[0-9]{4}-[0-9]{2}-[0-9]{2}T00:00:00Z$");

    // QA Warning Types
    public static final String DATE_VALIDITY_TYPE = "constraint-date-validity";
    public static final String CHRONOLOGY_TYPE = "constraint-chronology";
    public static final String SINGLE_VALUE_TYPE = "constraint-single-value";

    @Override
    public void scrutinize(ItemUpdate update) {
        
        // Map to hold property counts for Cardinality check
        Map<String, Integer> propertyCounts = new HashMap<>();
        
        // Variables for Chronology Check
        TimeValue birthDate = null;
        TimeValue deathDate = null;

        for (StatementEdit statementEdit : update.getStatementEdits()) {
            Statement statement = statementEdit.getStatement();
            String pid = statement.getClaim().getMainSnak().getPropertyId().getId();
            Value value = statement.getValue();

            // 1. Track Cardinality
            propertyCounts.put(pid, propertyCounts.getOrDefault(pid, 0) + 1);

            // 2. Date Validity & Extraction
            if (value instanceof TimeValue) {
                TimeValue timeVal = (TimeValue) value;
                
                [cite_start]// Rule: Date Validity [cite: 50]
                // We validate the string representation against the ISO regex
                if (!ISO8601_PATTERN.matcher(timeVal.toString()).matches()) {
                    QAWarning warning = new QAWarning(
                        DATE_VALIDITY_TYPE,
                        "constraint.date_validity.iso8601_mismatch",
                        QAWarning.Severity.ERROR,
                        1
                    );
                    warning.setSender("SenTient Constraint Gate");
                    addWarning(warning);
                }

                // Capture dates for Chronology logic
                if ("P569".equals(pid)) {
                    birthDate = timeVal;
                } else if ("P570".equals(pid)) {
                    deathDate = timeVal;
                }
            }
        }

        [cite_start]// 3. Rule: Chronology Conflict [cite: 51]
        // Condition: Date of death precedes date of birth
        if (birthDate != null && deathDate != null) {
            if (compareDates(deathDate, birthDate) < 0) {
                QAWarning warning = new QAWarning(
                    CHRONOLOGY_TYPE,
                    "constraint.chronology.death_before_birth",
                    QAWarning.Severity.ERROR,
                    1
                );
                warning.setSender("SenTient Constraint Gate");
                addWarning(warning);
            }
        }

        [cite_start]// 4. Rule: Single Value Violation [cite: 52]
        for (Map.Entry<String, Integer> entry : propertyCounts.entrySet()) {
            String pid = entry.getKey();
            int count = entry.getValue();

            if (SINGLE_VALUE_PROPERTIES.contains(pid) && count > 1) {
                QAWarning warning = new QAWarning(
                    SINGLE_VALUE_TYPE,
                    "constraint.single_value.violation: " + pid,
                    QAWarning.Severity.WARNING,
                    count
                );
                warning.setSender("SenTient Constraint Gate");
                addWarning(warning);
            }
        }
    }

    /**
     * Helper to compare two TimeValue objects.
     * Returns negative if t1 < t2, positive if t1 > t2.
     */
    private int compareDates(TimeValue t1, TimeValue t2) {
        // Simplified comparison based on Year -> Month -> Day
        if (t1.getYear() != t2.getYear()) {
            return Long.compare(t1.getYear(), t2.getYear());
        }
        if (t1.getMonth() != t2.getMonth()) {
            return Byte.compare(t1.getMonth(), t2.getMonth());
        }
        return Byte.compare(t1.getDay(), t2.getDay());
    }
}