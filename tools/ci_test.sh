#!/bin/bash
# In-game test of a jar: a real dedicated server (which stages the scene and checks the caravan logic)
# and a real client (portablemc, Xvfb, software rendering) that joins it and takes screenshots.
# Usage: tools/ci_test.sh <jar> <loader: forge|neoforge> <loader version> <test name>
#   e.g. tools/ci_test.sh out/chroniclecaravans-1.0.0-forge-1.20.1.jar forge 1.20.1-47.4.23 forge-1.20.1
set -u
JAR="$(realpath "$1")"; LOADER="$2"; LV="$3"; NAME="$4"
OUT="$PWD/ci-out/$NAME"; mkdir -p "$OUT/screenshots"
W="$PWD/ci-work/$NAME"; rm -rf "$W"; mkdir -p "$W"; cd "$W"
case "$LOADER" in
  forge)    INSTALLER="https://maven.minecraftforge.net/net/minecraftforge/forge/${LV}/forge-${LV}-installer.jar" ;;
  neoforge) INSTALLER="https://maven.neoforged.net/releases/net/neoforged/neoforge/${LV}/neoforge-${LV}-installer.jar" ;;
  *) echo "unknown loader: $LOADER"; exit 2 ;;
esac

# ---- Server ----
# (26.x pauses an empty server after 60 s: the self-test runs before anyone joins, so never pause)
mkdir -p srv && cd srv
curl -sSfL -o installer.jar "$INSTALLER" || { echo "installer not found: $INSTALLER"; exit 1; }
java -jar installer.jar --installServer . > installer.log 2>&1 || (tail -30 installer.log; exit 1)
echo "eula=true" > eula.txt
printf 'online-mode=false\nwhite-list=false\nenforce-whitelist=false\nspawn-protection=0\nlevel-seed=%s\ngamemode=creative\ndifficulty=normal\nview-distance=8\npause-when-empty-seconds=0\n' "${SEED:-caravans}" > server.properties
mkdir -p mods && cp "$JAR" mods/
printf -- '\n-Dchroniclecaravans.selftest=true\n' >> user_jvm_args.txt
chmod +x run.sh
(timeout 1700 ./run.sh nogui < /dev/null > "$OUT/selftest.log" 2>&1; echo "server exit $?" >> "$OUT/selftest.log") &
cd ..
for i in $(seq 1 150); do grep -q "SELFTEST\] READY" "$OUT/selftest.log" && break; sleep 4; done
grep -E "\[SELFTEST\]|Exception|ERROR" "$OUT/selftest.log" | head -60

# ---- Client ----
mkdir -p client/mods && cp "$JAR" client/mods/
printf 'onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\nsoundCategory_master:0.0\nnarrator:0\nguiScale:2\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:8\nfov:0.0\nrenderClouds:"true"\n' > client/options.txt
# Minecraft 26.x: rendering backend; under Xvfb, SDL must go through EGL for the sRGB visual 26.x asks for
[ -n "${BACKEND:-}" ] && echo "preferredGraphicsBackend:${BACKEND}" >> client/options.txt
[ "${BACKEND:-}" = "opengl" ] && export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
VERSION="${LOADER}::${LV}"; LAUNCH=()
if [[ "$LV" =~ ^2[6-9]\. ]]; then
  # Minecraft 26.x: the loader's own installer sets up the client, then portablemc starts that version
  mkdir -p mcmain && echo '{"profiles":{}}' > mcmain/launcher_profiles.json
  java -jar srv/installer.jar --installClient mcmain > "$OUT/client-installer.log" 2>&1 || tail -30 "$OUT/client-installer.log"
  VERSION=$(ls mcmain/versions | grep -i "$LOADER" | head -1)
  echo "installed version: $VERSION"
  LAUNCH=(--jvm "$(command -v java)")
fi
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" timeout 1500 \
  portablemc --main-dir "$PWD/mcmain" start --mc-dir "$PWD/client" "${LAUNCH[@]}" "$VERSION" \
  -u Tester --join-server 127.0.0.1 --resolution 1280x720 \
  --jvm-arg=-Xmx3G,-Dchroniclecaravans.clienttest=true > "$OUT/clienttest.log" 2>&1
echo "client exit $?"
grep -E "CLIENTTEST|ChronicleCaravans|Exception|ERROR" "$OUT/clienttest.log" | head -100
grep -q "CLIENTTEST\] RESULT" "$OUT/clienttest.log" || tail -80 "$OUT/clienttest.log"
cp client/screenshots/*.png "$OUT/screenshots/" 2>/dev/null || true
cp client/crash-reports/*.txt "$OUT/" 2>/dev/null || true
cp srv/crash-reports/*.txt "$OUT/" 2>/dev/null || true
echo "screenshots: $(ls "$OUT/screenshots" | wc -l)"
pkill -f "nogui" || true
wait
grep -q "\[SELFTEST\] RESULT: OK" "$OUT/selftest.log" && grep -q "\[CLIENTTEST\] RESULT: OK" "$OUT/clienttest.log"
