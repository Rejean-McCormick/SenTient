# ==============================================================================
# SenTient v1.0.0-RC3 | Initialization & Screening Script (Safe Mode)
# ==============================================================================
# Usage: ./init_sentient.ps1
# Role: Scaffolds the Hybrid Architecture without overwriting existing work.
# ==============================================================================

$ErrorActionPreference = "Stop"
# FIX: Use the current directory as the project root (No nesting)
$ProjectRoot = Get-Location

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
        Write-Host "  + Created Directory: $Dir" -ForegroundColor Green
    }
}

# --- 2. Manifest Artifacts (Safe Touch) ---
# Only creates files if they are missing. Does NOT overwrite.
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

Write-Host "`n[2/4] CHECKING ARTIFACTS..." -ForegroundColor Cyan
ForEach ($File in $Artifacts) {
    $Path = Join-Path $ProjectRoot $File
    
    if (Test-Path $Path) {
        $Item = Get-Item $Path
        if ($Item.Length -gt 0) {
            # File exists and has content - Skip
            Write-Host "  [SKIP] Exists & Populated: $File" -ForegroundColor DarkGray
        } else {
            # File exists but is empty - Warn user
            Write-Host "  [WARN] Exists but EMPTY: $File" -ForegroundColor Yellow
        }
    } else {
        # File is missing - Create placeholder
        New-Item -ItemType File -Path $Path -Force | Out-Null
        Write-Host "  + Created Placeholder: $File" -ForegroundColor Green
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
        Write-Host "  [WARN] Java 17+ is required for 'core_java'. Found: $JavaVer" -ForegroundColor Yellow
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
Write-Host "`n[4/4] STATUS REPORT" -ForegroundColor Cyan
Write-Host "------------------------------------------------------------"
Write-Host "1. Root Directory: $ProjectRoot"
Write-Host "2. Any file marked [SKIP] is safe and was not touched."
Write-Host "3. Any file marked [WARN] is empty - PASTE YOUR CODE THERE."
Write-Host "4. If everything looks good, run: docker-compose up -d"
Write-Host "------------------------------------------------------------"
Write-Host "SenTient Initialization Complete." -ForegroundColor Green