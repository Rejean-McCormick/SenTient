<!-- kOA Doc Server · Docs IA · lot 02 · rédaction dérivée d’une capture, à valider par le propriétaire -->
# Navigation IA — SenTient

**Rôle observé :** Prototype hybride de découverte et de rapprochement d'entités avec couches Solr, Falcon, OpenRefine et DuckDB.

**Autorité :** SenTient produit des candidats contextualisés; les consommateurs décident leur admission.

**Provenance :** `SmartDump_20261120_221507.zip` → `SenTient_Code_snapshot.zip` (20 novembre 2026). Cette capture ne prouve ni la présence actuelle du dossier Windows, ni un déploiement, ni une qualification.

## Ordre de lecture fondé sur les sources

1. [`README.md`](../../README.md) — source propriétaire présente dans le snapshot.
2. [`Docs/00_ARCHITECTURE_BLUEPRINT.md`](../../Docs/00_ARCHITECTURE_BLUEPRINT.md) — source propriétaire présente dans le snapshot.
3. [`Docs/07_QA_AND_VALIDATION.md`](../../Docs/07_QA_AND_VALIDATION.md) — source propriétaire présente dans le snapshot.
4. [`Docs/01_INGESTION_LAYER.md`](../../Docs/01_INGESTION_LAYER.md) — source propriétaire présente dans le snapshot.
5. [`Docs/02_SEMANTIC_LAYER.md`](../../Docs/02_SEMANTIC_LAYER.md) — source propriétaire présente dans le snapshot.
6. [`Docs/03_CORE_LAYER.md`](../../Docs/03_CORE_LAYER.md) — source propriétaire présente dans le snapshot.
7. [`Docs/04_DATA_DICTIONARY.md`](../../Docs/04_DATA_DICTIONARY.md) — source propriétaire présente dans le snapshot.
8. [`Docs/05_WIRING_AND_CONFIG.md`](../../Docs/05_WIRING_AND_CONFIG.md) — source propriétaire présente dans le snapshot.

## Pour une intervention IA

1. Définir le changement envisagé et relire les documents propriétaires ci-dessus.
2. Consulter le blueprint, lancer uniquement des fixtures locales, relire les sorties et la sécurité de chaque couche.
3. Identifier les tests de la révision locale et préserver les traces avant de proposer une modification.
4. Présenter les limites de couverture et demander l’accord du propriétaire avant une opération à effet.

## Limites à ne pas perdre

- Aucune libération automatique de données externes; intégration de production de bout en bout non attestée.
- L’overlay ne certifie pas le produit et n’autorise pas une écriture distante.
- Les contrats et `AGENTS.md` propriétaires priment sur cet index dérivé.
- Le Framework Kristal v11 est une cible documentaire facultative; toute adoption exige un profil explicitement épinglé.

## Aides complémentaires

- [`AI_CONTEXT.md`](AI_CONTEXT.md) — workflow et frontières de confiance propres au dépôt.
- `PROFIL_INGENIERIE.md` — facultatif, mesuré par RepoSurveyor sur le checkout local avec confirmation.
