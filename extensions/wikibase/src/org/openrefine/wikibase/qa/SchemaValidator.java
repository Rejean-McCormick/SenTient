package org.openrefine.wikibase.qa;

import java.util.ArrayList;
import java.util.List;

import org.openrefine.wikibase.schema.WikibaseSchema;
import org.openrefine.wikibase.updates.ItemUpdate;
import org.openrefine.wikibase.updates.scheduler.ImpossibleSchedulingException;
import org.openrefine.wikibase.updates.scheduler.WikibaseAPIUpdateScheduler;

import com.google.refine.browsing.Engine;
import com.google.refine.model.Project;

/**
 * The QA Orchestrator (The Scrutinizer Gate).
 * Role: Drives the Quality Assurance process by executing all registered Scrutinizers.
 * Architecture:
 * 1. Simulates the export (generates ItemUpdates).
 * 2. Passes updates to IntegrityScrutinizer and ConstraintScrutinizer.
 * 3. Aggregates QAWarning objects for the UI.
 * Triggered by: SenTientOrchestrator before commit or export.
 */
public class SchemaValidator {

    private final List<EditScrutinizer> scrutinizers;

    public SchemaValidator() {
        this.scrutinizers = new ArrayList<>();
        
        // 1. Register Core SenTient Scrutinizers
        // These enforce the rules defined in 'config/qa/scrutinizer_rules.yaml'
        
        // Checks for Missing Identity, Invalid Formats, P-Tag Confusion
        this.scrutinizers.add(new IntegrityScrutinizer());
        
        // Checks for Date Validity, Chronology, Cardinality
        this.scrutinizers.add(new ConstraintScrutinizer());
        
        // Future: Add ConsensusScrutinizer here for "Popularity vs Context" checks
    }

    /**
     * Main Entry Point: Validates the current schema mapping against the project data.
     * @param project The OpenRefine project containing the data rows.
     * @param schema The WikibaseSchema defining the mapping graph.
     * @param engine The filtering engine (to validate only active rows).
     * @return A list of aggregated QAWarning objects.
     */
    public List<QAWarning> validate(Project project, WikibaseSchema schema, Engine engine) {
        // 1. Reset Scrutinizers for a clean run
        for (EditScrutinizer scrutinizer : scrutinizers) {
            scrutinizer.prepareDependencies(project);
        }

        // 2. Simulate Export (Generate ItemUpdates)
        // This evaluates the schema graph against the raw cell values.
        List<ItemUpdate> updates;
        try {
            // Evaluates the schema to produce the stream of intended changes
            updates = schema.evaluate(project, engine);
            
            // Schedule updates to resolve any merge conflicts (Semantic Diffing)
            WikibaseAPIUpdateScheduler scheduler = new WikibaseAPIUpdateScheduler();
            updates = scheduler.schedule(updates);
            
        } catch (ImpossibleSchedulingException e) {
            // If the schedule is impossible (e.g. cyclic dependencies), return a critical warning
            List<QAWarning> fatal = new ArrayList<>();
            fatal.add(new QAWarning("scheduler-error", "QA.scheduler_error", QAWarning.Severity.CRITICAL, 0));
            return fatal;
        }

        // 3. Execute Scrutinizers (The Gate)
        for (ItemUpdate update : updates) {
            for (EditScrutinizer scrutinizer : scrutinizers) {
                // Each scrutinizer inspects the update and adds warnings to its internal list
                scrutinizer.scrutinize(update);
            }
        }

        // 4. Aggregate Results
        List<QAWarning> allWarnings = new ArrayList<>();
        for (EditScrutinizer scrutinizer : scrutinizers) {
            // Collect warnings (deduplicated by the Scrutinizer base class)
            allWarnings.addAll(scrutinizer.getWarnings());
        }

        return allWarnings;
    }
    
    /**
     * Validates if a specific action (Export/Commit) should be blocked.
     * Based on 'strict_mode' config in scrutinizer_rules.yaml.
     */
    public boolean containsFatalErrors(List<QAWarning> warnings) {
        return warnings.stream()
                .anyMatch(w -> w.getSeverity() == QAWarning.Severity.CRITICAL);
    }
}