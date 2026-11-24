<!-- kOA Doc Server · Docs IA · lot 02 · rédaction dérivée d’une capture, à valider par le propriétaire -->
# Navigation IA — SenTient

**Rôle observé :** Prototype hybride de découverte et de rapprochement d'entités avec couches Solr, Falcon, OpenRefine et DuckDB.

**Autorité :** SenTient produit des candidats contextualisés; les consommateurs décident leur admission.

**Provenance :** `SmartDump_20261120_221507.zip` → `SenTient_Code_snapshot.zip` (20 novembre 2026). Cette capture ne prouve ni la présence actuelle du dossier Windows, ni un déploiement, ni une qualification.

## Objet et état de cette fiche

La description ci-dessous est rattachée au `README.md` du snapshot; vérifier le code actif sur le poste. Cette fiche est un **guide de navigation**, non une spécification normative ni une validation de maturité.

## Processus conseillé

1. **Identifier la source et le profil.** Consulter le blueprint, lancer uniquement des fixtures locales, relire les sorties et la sécurité de chaque couche.
2. **Contrôler l’autorité.** SenTient produit des candidats contextualisés; les consommateurs décident leur admission.
3. **Préparer un environnement de test.** Utiliser les fichiers réels de la révision étudiée et des données fictives ou autorisées.
4. **Vérifier sans promouvoir.** Documenter commandes, périmètre, résultat, chemins et empreintes; ne jamais transformer une observation locale en preuve historique validée.
5. **Revue propriétaire.** Une publication, activation, import ou effet externe nécessite l’accord du propriétaire et les gates correspondants.

## Garde-fous particuliers

- Aucune libération automatique de données externes; intégration de production de bout en bout non attestée.
- N’inclure aucun secret, donnée personnelle, sauvegarde privée ou sortie de diagnostic non autorisée dans les exports Doc Server.
- Préserver l’intégrité des sources et des états historiques; éviter toute réécriture automatique des références.

## Sources d’orientation vérifiables dans le snapshot

1. [`README.md`](../../README.md) — source propriétaire présente dans le snapshot.
2. [`Docs/00_ARCHITECTURE_BLUEPRINT.md`](../../Docs/00_ARCHITECTURE_BLUEPRINT.md) — source propriétaire présente dans le snapshot.
3. [`Docs/07_QA_AND_VALIDATION.md`](../../Docs/07_QA_AND_VALIDATION.md) — source propriétaire présente dans le snapshot.
4. [`Docs/01_INGESTION_LAYER.md`](../../Docs/01_INGESTION_LAYER.md) — source propriétaire présente dans le snapshot.
5. [`Docs/02_SEMANTIC_LAYER.md`](../../Docs/02_SEMANTIC_LAYER.md) — source propriétaire présente dans le snapshot.
6. [`Docs/03_CORE_LAYER.md`](../../Docs/03_CORE_LAYER.md) — source propriétaire présente dans le snapshot.
7. [`Docs/04_DATA_DICTIONARY.md`](../../Docs/04_DATA_DICTIONARY.md) — source propriétaire présente dans le snapshot.
8. [`Docs/05_WIRING_AND_CONFIG.md`](../../Docs/05_WIRING_AND_CONFIG.md) — source propriétaire présente dans le snapshot.

## Compatibilité Framework

S’il y a une dépendance Kristal, lire la révision du Framework et le profil **effectivement adoptés** par ce dépôt. Les nouvelles conventions v11 (identités sémantiques, dictionnaires séparés, provenance) servent de référence lorsque compatibles; **ne pas bloquer** la navigation IA ni imposer la migration de données historiques pour cette seule raison.
