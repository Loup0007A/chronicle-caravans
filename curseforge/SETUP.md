# Mettre Chronicle Caravans sur CurseForge

La création du projet est **manuelle** (une seule fois) : CurseForge fait relire chaque nouveau projet par un modérateur. Ensuite, chaque nouvelle version est envoyée **automatiquement** par GitHub Actions.

## 1. Créer le projet (une fois)

1. Connecte-toi sur CurseForge, puis va sur <https://authors.curseforge.com/#/projects/create/choose-game>.
2. Jeu : **Minecraft** → classe : **Mods**.
3. Remplis la fiche avec les textes prêts à copier de [`description.md`](description.md) :
   - **Nom** : `Chronicle Caravans`
   - **Résumé** (summary) : la ligne « Summary » du fichier.
   - **Description** : le bloc « Description » (en mode Markdown).
   - **Avatar** : [`avatar.png`](avatar.png) (400×400).
   - **Catégories** conseillées : *Adventure and RPG* (principale), *Mobs*, *World Gen* ou *Miscellaneous*.
   - **Licence** : MIT. **Code source** : `https://github.com/Loup0007A/chronicle-caravans`. **Issues** : `https://github.com/Loup0007A/chronicle-caravans/issues`.
4. Premier fichier : envoie `releases/chroniclecaravans-latest-neoforge-1.21.1.jar` (ou celui de la Release GitHub) avec :
   - Version de jeu **1.21.1**, chargeur **NeoForge**, environnements **Client** et **Server**, **Java 21**.
   - Type : **Beta** tant que la version est en 0.x. Notes : la section correspondante de `CHANGELOG.md`.
5. Ajoute les captures du dossier [`screenshots/`](screenshots/) dans l'onglet **Images** (galerie).
6. Envoie en modération. L'**ID du projet** (un nombre) s'affiche dans l'encadré « About Project » de la page du projet.

## 2. Brancher l'envoi automatique (une fois)

1. Crée un jeton API CurseForge : <https://www.curseforge.com/account/api-tokens> (nom : `github-chronicle-caravans`).
2. Sur GitHub, dans le dépôt : **Settings → Secrets and variables → Actions**
   - onglet **Secrets** → *New repository secret* : `CURSEFORGE_TOKEN` = le jeton ;
   - onglet **Variables** → *New repository variable* : `CURSEFORGE_PROJECT_ID` = l'ID du projet.

## 3. Publier une nouvelle version

1. Augmente `mod_version` dans `gradle.properties` et ajoute une section `## x.y.z` en haut de `CHANGELOG.md`.
2. Pousse sur `main`. Le workflow compile, teste en jeu, crée la Release GitHub, copie le jar dans `releases/`… et l'envoie sur CurseForge (1.21.1 · NeoForge · Client + Server · Java 21) avec la section du changelog comme notes.

Pour renvoyer la version actuelle sans la changer : onglet **Actions → Build Chronicle Caravans → Run workflow** sur `main`, case *publish_curseforge* cochée.

Sans le secret ou la variable, l'étape CurseForge est simplement ignorée (avec un message dans le résumé du workflow).
