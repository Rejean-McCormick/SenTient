package org.openrefine.wikibase.qa;

import org.openrefine.wikibase.updates.ItemUpdate;
import org.openrefine.wikibase.updates.StatementEdit;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.ItemIdValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Value;

import java.util.regex.Pattern;

/**
 * The Integrity Scrutinizer (QA Layer).
 * * Role: Validates the structural health of data before export.
 * Config Source: config/qa/scrutinizer_rules.yaml
 * * Implements strict checks for:
 * 1. Missing Identity: Matched items with null IDs.
 * 2. Invalid Format: IDs that do not match ^Q[0-9]+$.
 * 3. P-Tag Confusion: Usage of Properties (P-items) in Item (Q-item) slots.
 */
public class IntegrityScrutinizer extends EditScrutinizer {

    // Regex compiled from config/qa/scrutinizer_rules.yaml
    private static final Pattern VALID_QID_PATTERN = Pattern.compile("^Q[0-9]+$");
    private static final Pattern PROPERTY_PID_PATTERN = Pattern.compile("^P[0-9]+$");

    // QA Warning Types (Keys used for aggregation in UI)
    public static final String MISSING_IDENTITY_TYPE = "integrity-missing-identity";
    public static final String INVALID_FORMAT_TYPE = "integrity-invalid-format";
    public static final String P_TAG_CONFUSION_TYPE = "integrity-p-tag-confusion";

    @Override
    public void scrutinize(ItemUpdate update) {
        ItemIdValue subject = update.getEntityId();

        // Rule 1: Missing Identity [cite: 648]
        // Condition: "status == 'MATCHED' && (id == null || id == '')"
        // In the context of an ItemUpdate, if the subject is null but the update exists, it's a critical failure.
        if (subject == null) {
            // Note: New items might have null IDs initially, but 'isNew()' handles that check.
            if (!update.isNew()) {
                QAWarning warning = new QAWarning(
                    MISSING_IDENTITY_TYPE, 
                    "integrity.missing_identity.title", 
                    QAWarning.Severity.CRITICAL, 
                    1
                );
                warning.setSender("SenTient Integrity Gate");
                addWarning(warning);
            }
            return; // Cannot proceed with further ID checks if null
        }

        String qid = subject.getId();

        // Rule 2: Invalid Format [cite: 649]
        // Condition: "!regex(id, '^Q[0-9]+$')"
        if (!VALID_QID_PATTERN.matcher(qid).matches()) {
            QAWarning warning = new QAWarning(
                INVALID_FORMAT_TYPE,
                "integrity.invalid_format.title",
                QAWarning.Severity.CRITICAL,
                1
            );
            warning.setSender("SenTient Integrity Gate");
            addWarning(warning);
        }

        // Rule 3: P-Tag Confusion [cite: 650]
        // Condition: "regex(id, '^P[0-9]+$')"
        if (PROPERTY_PID_PATTERN.matcher(qid).matches()) {
            QAWarning warning = new QAWarning(
                P_TAG_CONFUSION_TYPE,
                "integrity.p_tag_confusion.title",
                QAWarning.Severity.WARNING,
                1
            );
            warning.setSender("SenTient Integrity Gate");
            addWarning(warning);
        }

        // Rule 4: Deep Scan of Statements (Checking Values)
        for (StatementEdit statement : update.getStatementEdits()) {
            Value value = statement.getStatement().getValue();
            if (value instanceof EntityIdValue) {
                validateValueEntity((EntityIdValue) value);
            }
        }
    }

    /**
     * Recursive check for nested entities in statements (e.g., "Linked To" fields).
     */
    private void validateValueEntity(EntityIdValue entity) {
        String id = entity.getId();

        // Check if a Property is used where an Item is expected
        if (entity instanceof ItemIdValue) {
            if (PROPERTY_PID_PATTERN.matcher(id).matches()) {
                addWarning(new QAWarning(
                    P_TAG_CONFUSION_TYPE,
                    "integrity.p_tag_confusion_in_value.title", 
                    QAWarning.Severity.WARNING, 
                    1
                ));
            }
        }
    }
}