# Chronicle Caravans — consignes pour Claude

- **Règle d'or : toujours pousser directement sur `main`** (pas de branche `dev`, pas de PR), à la demande de Loup0007A.
- Avant de pousser : le code doit compiler. Ensuite, vérifier le résultat de la CI : sur la branche `ci-logs`, `main/status.txt` puis `main/neoforge-1.21.1/selftest.log` (serveur), `clienttest.log` (client) et `screenshots/`.
- Après chaque push sur `main` qui passe les tests, la CI ajoute un commit « Jars compilés … » : faire `git fetch` + rebase avant le push suivant.
- Même organisation que le dépôt `fate-flip` (CI, release, envoi CurseForge).

## Le mod
- NeoForge **1.21.1**, Java 21, noms Mojang. Mod id `chroniclecaravans`, paquet `com.chroniclecaravans`. Le mod s'installe **des deux côtés** (client et serveur) : toute la logique est côté serveur, le client n'a que le rendu (`client/ClientSetup`).
- `block/MilestoneBlock` : la borne. La poser enregistre un arrêt dans `world/CaravanData` (SavedData de l'Overworld : arrêts + réputation des joueurs).
- `world/CaravanSpawner` : envoie les caravanes vers les bornes (tick du monde), crée le maître et ses lamas, suit les morts de bandits et les coups portés aux lamas.
- `entity/CaravanMaster` (étend `WanderingTrader`, rendu vanilla) : états `TRAVELING` / `HALTED` / `LEAVING`, trajet par étapes de 24 blocs (`nextHop`), stock par rang de réputation (`getOffers` ne renvoie que les offres visibles par le joueur qui commerce), embuscades.
- Le convoi : `CaravanMaster` garde la trace de ses pas (`trail`) et place à chaque tick, sur cette trace, le lama de trait (mené en laisse par le maître), le chariot `entity/CaravanWagon` puis les lamas de bât (attachés au chariot, puis l'un à l'autre). Les lamas du convoi sont en `NoAi` : seul le maître les déplace.
- Le chariot : modèle **généré** par `python3 tools/wagon.py` (`client/WagonModel.java` + `textures/entity/wagon.png` ; `--preview` en fait un rendu logiciel dans `/tmp` pour vérifier l'aspect). Ne pas modifier `WagonModel.java` à la main. La bâche est teintée par région (`WagonRenderer.tint`).
- Le guide (façon Ponder de Create) : `client/guide/` — `GuideScene` (scène = fonction du temps : blocs, acteurs, objets, bulles), `GuideScenes` (les 5 scènes), `GuideScreen` (rendu et commandes) ; `client/GuideKeys` (maintenir W sur la borne, `/caravanguide`). Textes dans les fichiers de langue (`guide.chroniclecaravans.<scène>.<id>`).
- `entity/RoadNavigation` : pathfinding qui pénalise les blocs hors route (tag de blocs `chroniclecaravans:roads`).
- `Region` : les 8 régions d'origine, leurs marchandises (rang 0 à 4) et la détection par biome. `Tier` : les 5 rangs de réputation.
- `CCConfig` : chaque fonctionnalité a son réglage (`config/chroniclecaravans-common.toml`).
- Tout texte affiché passe par les fichiers de langue (`en_us.json` + `fr_fr.json`, mêmes clés dans les deux).
- La vitesse réelle d'un mob varie comme le **carré** de son attribut de vitesse × le modificateur du but : en tenir compte pour tout réglage de vitesse.

## Compiler et tester
- Les dépôts Maven ne sont pas accessibles depuis la session, GitHub oui. Les bibliothèques de compilation NeoForge 1.21.1 viennent de la branche `mc-libs-neoforge-1.21.1` du dépôt `fate-flip` (clonée dans `/home/claude/mc/lib-neoforge-1.21.1`) ; compiler avec `javac --release 21 -proc:none -cp "/home/claude/mc/lib-neoforge-1.21.1/*"` depuis un dossier neutre (`cd /tmp`). Lire une signature : `javap -cp …/neoforge-21.1.265.jar -p <classe>`.
- Test en jeu (CI, `tools/ci_test.sh`) : un vrai serveur lancé avec `-Dchroniclecaravans.selftest=true` (`debug/SelfTest` : construit une route et une borne au spawn, fait venir une caravane, vérifie l'arrivée, le stock, l'embuscade, puis rejoue la scène) et un vrai client avec `-Dchroniclecaravans.clienttest=true` (`debug/ClientTest` : rejoint le serveur, prend les captures, quitte). Toute nouvelle fonctionnalité doit avoir sa vérification dans `SelfTest`.
- Nouvelle version = augmenter `mod_version` dans `gradle.properties` + ajouter une section `## x.y.z` en haut de `CHANGELOG.md` (elle sert de notes pour la release GitHub et pour CurseForge, envoi automatique). Les versions `0.x` partent en « beta » sur CurseForge.
- Toute nouvelle marchandise, région ou fonctionnalité visible : mettre à jour le README et `curseforge/description.md`.
- Le logo est dessiné par `python3 tools/logo.py`.
