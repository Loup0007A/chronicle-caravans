# Chronicle Caravans — consignes pour Claude

- **Règle d'or : toujours pousser directement sur `main`** (pas de branche `dev`, pas de PR), à la demande de Loup0007A.
- Avant de pousser : le code doit compiler pour **chaque cible**. Ensuite, vérifier le résultat de la CI : sur la branche `ci-logs`, `main/status.txt` puis, par cible, `main/<cible>/selftest.log` (serveur), `clienttest.log` (client) et `screenshots/`.
- Après chaque push sur `main` qui passe les tests, la CI ajoute un commit « Jars compilés … » : faire `git fetch` + rebase avant le push suivant.
- Même organisation que le dépôt `fate-flip` (CI, release, envoi CurseForge).

## Multi-versions (comme fate-flip)
- `src/main` = la variante de référence **NeoForge 1.21.1** (compile telle quelle avec le `build.gradle` racine).
- Les autres cibles sont dans `targets/<cible>/` : `forge-1.21.1`, `forge-1.20.1` (Java 17), `neoforge-26.3` (Java 25). Chacune a son build Gradle, son `target.json` et ses `overrides/`. Leurs sources sont fabriquées par `python3 tools/port.py <cible>` : blocs `//#if` / `//#elif` / `//#else` / `//#endif` (étiquettes `neoforge`, `forge`, `mc120`, `mc121`, `mc26`…, lignes inactives préfixées `//$$ `), remplacements de `target.json`, puis fichiers de `overrides/`. `python3 tools/port.py --check` vérifie que `src/main` est cohérent.
- Ce qui change d'une version de Minecraft à l'autre passe par `platform/Compat.java` et `platform/Nbt.java` (une copie par cible dans `overrides/` quand il le faut).
- Compiler une cible localement : `bash /home/claude/tools/cc_target.sh <cible>` (bibliothèques dans `/home/claude/mc/lib-<cible>`, branches `mc-libs-<cible>` de ce dépôt ou de fate-flip ; le build CI les exporte automatiquement la première fois ; JDK 25 dans `/home/claude/jdk25` pour 26.3).
- 26.x : rendu par états (`EntityRenderer.submit`, `SubmitNodeCollector`) ; le rendu du chariot est entièrement dans `targets/neoforge-26.3/overrides/.../WagonRenderer.java`, la scène 3D du guide est un « picture in picture » (`overrides/.../guide/GuideRender.java`, enregistré dans `ClientSetup`). Sauvegardes des entités par `ValueOutput`/`ValueInput` (pont vers `writeX/readX(CompoundTag)`).
- CI : un job de build par cible, un test en jeu par cible, puis release (tous les jars) et CurseForge (un fichier par cible).

## Le mod
- Mod id `chroniclecaravans`, paquet `com.chroniclecaravans`, noms Mojang. Le mod s'installe **des deux côtés** (client et serveur) : toute la logique est côté serveur, le client n'a que le rendu (`client/ClientSetup`).
- `block/MilestoneBlock` : la borne. La poser enregistre un arrêt dans `world/CaravanData` (SavedData de l'Overworld : arrêts, auberges, routes nommées/bâties, registre des caravanes, profils de guilde). Cassée : une lanterne ; au toucher de soie : la borne avec son horaire (`getDrops` + données d'objet, jamais empilable).
- `block/InnSignBlock` : l'enseigne d'auberge (lit à moins de 8 blocs) ; la nuit les caravanes y dorment (`CaravanMaster.checkNight`), paient le matin dans le conteneur voisin.
- `world/CaravanSpawner` : quand une étape attend, `dispatch` envoie d'abord une caravane libre chargée, sinon déplace une caravane endormie (registre `CaravanData.caravans`), sinon (rarement : `newCaravanCooldownSeconds`, max `maxActiveCaravans`) en crée une hors de vue. Suit aussi les morts de bandits et les coups portés aux lamas.
- `entity/CaravanMaster` (étend `WanderingTrader`, rendu vanilla) : états `TRAVELING` / `HALTED` / `LODGING` / `LEAVING`, trajet par étapes de 24 blocs (`nextHop`), choix de l'étape suivante par score (`chooseNext` : en attente, village, proche, pas déjà servie), embuscades, escorte (`CaravanGuard`). Les offres restent des `MerchantOffer` vanilla (sauvegarde gratuite) ; le genre et le niveau requis sont codés dans leur xp (`Region.meta`). Pas d'écran vanilla : `openFor` ouvre `menu/CaravanMenu`.
- `menu/CaravanMenu` / `client/screen/CaravanScreen` (onglets Marché, Demandes, Services, Guilde) et `menu/AtlasMenu` / `client/screen/AtlasScreen` : tout passe par des emplacements d'affichage + DataSlots (valeurs < 32767) et les actions par `clickMenuButton` (ids < 128 : octet en 1.20.1). Aucun paquet réseau perso. Renommer depuis l'atlas = commande `/caravans rename …`. `client/screen/CCScreen` regroupe les différences de versions des écrans.
- `Guild` (niveaux, 12 talents en 3 branches, titres) remplace les rangs ; `Trading` (prix après talents, paiement) ; `world/RoadWorks` + `entity/RoadBuilder` (bâtisseurs) ; `item/AtlasItem`.
- Le convoi : `CaravanMaster` garde la trace de ses pas (`trail`) et place à chaque tick, sur cette trace, le lama de trait (mené en laisse par le maître), le chariot `entity/CaravanWagon` puis les lamas de bât (attachés au chariot, puis l'un à l'autre). Les lamas du convoi sont en `NoAi` : seul le maître les déplace.
- Le chariot : modèle **généré** par `python3 tools/wagon.py` (variantes 1.20.1 / 1.21 / 26.x en blocs `//#if`) (`client/WagonModel.java` + `textures/entity/wagon.png` ; `--preview` en fait un rendu logiciel dans `/tmp` pour vérifier l'aspect). Ne pas modifier `WagonModel.java` à la main. La bâche est teintée par région (`WagonRenderer.tint`).
- Le guide (façon Ponder de Create) : `client/guide/` — `GuideScene` (scène = fonction du temps : blocs, acteurs, objets, bulles), `GuideScenes` (les 5 scènes), `GuideScreen` (rendu et commandes) ; `client/GuideKeys` (maintenir W sur la borne, `/caravanguide`). Textes dans les fichiers de langue (`guide.chroniclecaravans.<scène>.<id>`).
- `entity/RoadNavigation` : pathfinding qui pénalise les blocs hors route (tag de blocs `chroniclecaravans:roads`).
- `Region` : les 8 régions d'origine, leurs marchandises (niveau de guilde requis), demandes, trésors, et la détection par biome.
- Textures/modèles de l'enseigne, de l'atlas et peaux des gardes/bâtisseurs : `python3 tools/assets.py`. Rendu des aides : `client/HelperRenderer` (+ copie 26.3 dans `overrides/`).
- `CCConfig` : chaque fonctionnalité a son réglage (`config/chroniclecaravans-common.toml`).
- Tout texte affiché passe par les fichiers de langue (`en_us.json` + `fr_fr.json`, mêmes clés dans les deux).
- La vitesse réelle d'un mob varie comme le **carré** de son attribut de vitesse × le modificateur du but : en tenir compte pour tout réglage de vitesse.

## Compiler et tester
- Les dépôts Maven ne sont pas accessibles depuis la session, GitHub oui. Les bibliothèques de compilation NeoForge 1.21.1 viennent de la branche `mc-libs-neoforge-1.21.1` du dépôt `fate-flip` (clonée dans `/home/claude/mc/lib-neoforge-1.21.1`) ; compiler avec `javac --release 21 -proc:none -cp "/home/claude/mc/lib-neoforge-1.21.1/*"` depuis un dossier neutre (`cd /tmp`). Lire une signature : `javap -cp …/neoforge-21.1.265.jar -p <classe>`.
- Test en jeu (CI, `tools/ci_test.sh`) : un vrai serveur lancé avec `-Dchroniclecaravans.selftest=true` (`debug/SelfTest` : construit une route et une borne au spawn, fait venir une caravane, vérifie l'arrivée, le stock, l'embuscade, puis rejoue la scène) et un vrai client avec `-Dchroniclecaravans.clienttest=true` (`debug/ClientTest` : rejoint le serveur, prend les captures, quitte). Toute nouvelle fonctionnalité doit avoir sa vérification dans `SelfTest`.
- Nouvelle version = augmenter `mod_version` dans `gradle.properties` + ajouter une section `## x.y.z` en haut de `CHANGELOG.md` (elle sert de notes pour la release GitHub et pour CurseForge, envoi automatique). Les versions `0.x` partent en « beta » sur CurseForge.
- Toute nouvelle marchandise, région ou fonctionnalité visible : mettre à jour le README et `curseforge/description.md`.
- Le logo est dessiné par `python3 tools/logo.py`.
