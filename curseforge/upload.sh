#!/usr/bin/env bash
# Envoie un jar sur CurseForge via l'API d'upload officielle.
# Usage : CF_TOKEN=... CF_PROJECT=123456 bash curseforge/upload.sh <jar> <version> <nom affiché> <versions MC> <loaders> <java>
#   ex.   bash curseforge/upload.sh out/chroniclecaravans-0.1.0-neoforge-1.21.1.jar 0.1.0 "Chronicle Caravans 0.1.0 (NeoForge 1.21.1)" "1.21.1" "NeoForge" "Java 21"
# Les versions 0.x sont envoyées en « beta », les suivantes en « release ».
set -euo pipefail

JAR="$1"
VERSION="$2"
NAME="${3:-Chronicle Caravans $VERSION}"
MC_VERSIONS="${4:-1.21.1}"
LOADERS="${5:-NeoForge}"
JAVA="${6:-Java 21}"
API="https://minecraft.curseforge.com/api"

[ -f "$JAR" ] || { echo "Jar introuvable : $JAR"; exit 1; }

# Les versions de jeu sont identifiées par des ID numériques : on les résout par leur nom.
curl -sSf -H "X-Api-Token: ${CF_TOKEN}" "$API/game/version-types" > /tmp/cf-types.json
curl -sSf -H "X-Api-Token: ${CF_TOKEN}" "$API/game/versions" > /tmp/cf-versions.json

# Types dont le slug commence par un préfixe (minecraft-1-21, minecraft-26-3…), ou slug exact
types_like() { jq -r --arg p "$1" '[.[] | select(.slug | startswith($p)) | .id]' /tmp/cf-types.json; }
version_in() { # $1 = tableau JSON d'ID de types, $2 = nom exact
  jq -r --argjson t "$1" --arg n "$2" '.[] | select((.gameVersionTypeID as $g | $t | index($g)) and .name == $n) | .id' /tmp/cf-versions.json | head -n1
}

IDS=()
add() { # $1 = types, $2 = nom
  local id; id=$(version_in "$1" "$2")
  if [ -n "$id" ]; then IDS+=("$id"); echo "Version « $2 » -> $id"; else echo "::warning::Version CurseForge « $2 » introuvable"; fi
}
MC_T=$(types_like "minecraft-")
IFS=',' read -ra L <<< "$MC_VERSIONS"; for v in "${L[@]}"; do add "$MC_T" "$v"; done
IFS=',' read -ra L <<< "$LOADERS"; for v in "${L[@]}"; do add "$(types_like modloader)" "$v"; done
# Le mod s'installe des deux côtés
add "$(types_like environment)" "Client"
add "$(types_like environment)" "Server"
add "$(types_like java)" "$JAVA"
[ "${#IDS[@]}" -gt 0 ] || { echo "Aucune version de jeu résolue"; exit 1; }
GV=$(printf '%s\n' "${IDS[@]}" | jq -s '.')

# Notes de version : la section du CHANGELOG correspondant à cette version.
NOTES=$(awk -v v="## ${VERSION}" 'index($0,v)==1{f=1;next} /^## /{if(f)exit} f' CHANGELOG.md)
[ -n "$NOTES" ] || NOTES="Chronicle Caravans ${VERSION}"
case "$VERSION" in 0.*) TYPE=beta ;; *) TYPE=release ;; esac

META=$(jq -n --arg cl "$NOTES" --arg name "$NAME" --arg type "$TYPE" --argjson gv "$GV" \
  '{changelog: $cl, changelogType: "markdown", displayName: $name, gameVersions: $gv, releaseType: $type}')

HTTP=$(curl -sS -o /tmp/cf-response.json -w "%{http_code}" \
  -H "X-Api-Token: ${CF_TOKEN}" \
  --form-string "metadata=${META}" \
  -F "file=@${JAR}" \
  "$API/projects/${CF_PROJECT}/upload-file")
cat /tmp/cf-response.json; echo
if [ "$HTTP" != "200" ]; then echo "::error::Envoi CurseForge refusé (HTTP $HTTP) pour $NAME"; exit 1; fi
echo "::notice::$NAME envoyé sur CurseForge (projet ${CF_PROJECT})."
