# ==============================================================================
# SenTient v1.0.0-RC3 | Initialization & Screening Script
# ==============================================================================
# Usage: ./init_sentient.ps1
# Role: Scaffolds the Hybrid Architecture and validates the runtime environment.
# ==============================================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Join-Path (Get-Location) "SenTient"

Write-Host "`n[1/4] INITIALIZING SENTIENT ARCHITECTURE..." -ForegroundColor Cyan
Write-Host "      Target: $ProjectRoot" -ForegroundColor Gray

# --- 1. Create Directory Hierarchy (The "Funnel" Layout) ---
$Directories = @(
    "config/core",
    "config/nlp",
    "config/solr",
    "config/elastic",
    "config/orchestration",
    "config/qa",
    "config/storage",
    "modules/core/src/com/google/refine/i18n",
    "modules/core/src/com/google/refine/model",
    "modules/core/src/com/google/refine/process",
    "modules/core/src/com/google/refine/storage",
    "extensions/wikibase/src/org/openrefine/wikibase/qa",
    "src/components/Grid",
    "src/components/Reconcile",
    "src/falcon",
    "src/locales",
    "server/solr/sentient-tapioca/conf",
    "data/workspace",
    "data/models",
    "data/stopwords",
    "logs",
    "evaluation"
)

ForEach ($Dir in $Directories) {
    $Path = Join-Path $ProjectRoot $Dir
    If (-Not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
        Write-Host "  + Created: $Dir" -ForegroundColor Green
    }
}

# --- 2. Manifest Artifacts (Placeholders) ---
# Creates empty files where you will paste the code generated in the notebooks.
$Artifacts = @(
    # Config Layer
    "config/orchestration/environment.json",
    "config/core/butterfly.properties",
    "config/nlp/falcon_settings.yaml",
    "config/qa/scrutinizer_rules.yaml",
    "config/storage/duckdb_schema.sql",
    "config/elastic/falcon_mapping.json",
    "config/solr/tapioca_schema.xml",
    
    # Java Core (Layer 3)
    "modules/core/src/com/google/refine/RefineServlet.java",
    "modules/core/src/com/google/refine/SenTientOrchestrator.java",
    "modules/core/src/com/google/refine/model/Cell.java",
    "modules/core/src/com/google/refine/model/Recon.java",
    "modules/core/src/com/google/refine/storage/DuckDBStore.java",
    "modules/core/src/com/google/refine/process/ProcessManager.java",
    "modules/core/src/com/google/refine/i18n/Messages.properties",
    
    # Extensions (QA)
    "extensions/wikibase/src/org/openrefine/wikibase/qa/ConstraintScrutinizer.java",
    "extensions/wikibase/src/org/openrefine/wikibase/qa/IntegrityScrutinizer.java",
    "extensions/wikibase/src/org/openrefine/wikibase/qa/SchemaValidator.java",
    
    # Python NLP (Layer 2)
    "src/main.py",
    "src/requirements.txt",
    "src/falcon/pipeline.py",
    "src/falcon/preprocessing.py",
    "data/stopwords/falcon_extended_en.txt",
    "evaluation/evaluate_falcon_api.py",
    
    # Frontend
    "src/components/Grid/VirtualGrid.jsx",
    "src/components/Reconcile/ConfidenceBar.jsx",
    "src/locales/en.json",
    "package.json",
    
    # Infrastructure
    "docker-compose.yml",
    "refine.ini",
    "refine",  # Shell script
    "server/solr/sentient-tapioca/core.properties",
    "server/solr/sentient-tapioca/conf/solrconfig.xml",
    "server/solr/sentient-tapioca/conf/managed-schema"
)

Write-Host "`n[2/4] MANIFESTING ARTIFACTS..." -ForegroundColor Cyan
ForEach ($File in $Artifacts) {
    $Path = Join-Path $ProjectRoot $File
    If (-Not (Test-Path $Path)) {
        New-Item -ItemType File -Path $Path -Force | Out-Null
        Write-Host "  + Touch: $File" -ForegroundColor DarkGray
    }
}

# --- 3. Environmental Screening (Validation) ---
Write-Host "`n[3/4] SYSTEM SCREENING (Prerequisites)..." -ForegroundColor Cyan

# Check Java
Try {
    $JavaVer = java -version 2>&1 | Select-String "version"
    If ($JavaVer -match "17|18|19|20|21") {
        Write-Host "  [OK] Java Runtime: $JavaVer" -ForegroundColor Green
    } Else {
        Write-Host "  [WARN] Java 17+ is required for 'core_java'." -ForegroundColor Yellow
    }
} Catch { Write-Host "  [FAIL] Java not found in PATH." -ForegroundColor Red }

# Check Docker
Try {
    $DockerVer = docker --version
    Write-Host "  [OK] Container Engine: $DockerVer" -ForegroundColor Green
} Catch { Write-Host "  [WARN] Docker not found. Required for 'solr' and 'elastic'." -ForegroundColor Yellow }

# Check Python
Try {
    $PyVer = python --version 2>&1
    Write-Host "  [OK] NLP Runtime: $PyVer" -ForegroundColor Green
} Catch { Write-Host "  [WARN] Python 3.9+ not found." -ForegroundColor Yellow }

# --- 4. Final Instructions ---
Write-Host "`n[4/4] DEPLOYMENT READY" -ForegroundColor Cyan
Write-Host "------------------------------------------------------------"
Write-Host "1. Codebase scaffolded at: $ProjectRoot"
Write-Host "2. NEXT STEP: Paste the code blocks from the previous chat "
Write-Host "   into the empty files created above."
Write-Host "3. THEN: Run 'docker-compose up -d' to boot the backend."
Write-Host "------------------------------------------------------------"
Write-Host "SenTient v1.0.0-RC3 initialized." -ForegroundColor Green