#!/usr/bin/env bash
# In-game test of the auction house on a local Paper or Folia server, played by three bots.
#
#   e2e/run.sh <paper|folia> <Artisan jar> [minecraft version, default 1.21.8]
#
# Builds the plugin and the test harness, downloads the server from PaperMC, starts it in
# e2e/work/<server>/ (offline mode, peaceful, port 25601), runs e2e/bot/run.js, stops it.
# Exit code 0 when every check passed. The server log is e2e/work/<server>/server.log.
set -euo pipefail
SERVER="$1"; ARTISAN_JAR="$(realpath "$2")"; MC="${3:-1.21.8}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; E2E="$ROOT/e2e"; WORK="$E2E/work/$SERVER"
mkdir -p "$WORK/plugins/ArtisanAuctions/artisan_module/data"

(cd "$ROOT" && ./gradlew -q build)
JAR="$(ls "$ROOT"/plugin/build/libs/ArtisanAuctions-*.jar | grep -v -- '-sources' | sort -V | tail -1)"

# Harness: compiled against the jars the build already resolved.
CP="$(find ~/.gradle/caches/modules-2 -name 'paper-api-*.jar' ! -name '*sources*' | head -1):$(find ~/.gradle/caches/modules-2 -name 'artisan-core-api-*.jar' ! -name '*sources*' | sort -V | tail -1):$(ls "$ROOT"/api/build/libs/api-*.jar | grep -v sources | head -1)"
for lib in adventure-api adventure-key bungeecord-chat annotations-26; do CP="$CP:$(find ~/.gradle/caches/modules-2 -name "$lib*.jar" ! -name '*sources*' | head -1)"; done
rm -rf "$E2E/work/harness" && mkdir -p "$E2E/work/harness"
javac -nowarn -d "$E2E/work/harness" -cp "$CP" "$E2E/harness/src/e2e/Harness.java"
cp "$E2E/harness/plugin.yml" "$E2E/work/harness/"
(cd "$E2E/work/harness" && jar cf ../E2EHarness.jar .)

[ -f "$WORK/server.jar" ] || curl -fsS -o "$WORK/server.jar" "$(curl -fsS "https://fill.papermc.io/v3/projects/$SERVER/versions/$MC/builds/latest" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['downloads']['server:default']['url'])")"
# A jar left by an earlier run of another version would be loaded too.
rm -f "$WORK"/plugins/ArtisanAuctions-*.jar "$WORK"/plugins/.paper-remapped/ArtisanAuctions-*.jar
cp "$ARTISAN_JAR" "$JAR" "$E2E/work/E2EHarness.jar" "$WORK/plugins/"
echo "eula=true" > "$WORK/eula.txt"
printf 'online-mode=false\nserver-port=25601\nlevel-type=minecraft\\:flat\nspawn-protection=0\ndifficulty=peaceful\nview-distance=4\nsimulation-distance=4\n' > "$WORK/server.properties"
# The harness's "coins" currency, owner's settings over the shipped ones.
cat > "$WORK/plugins/ArtisanAuctions/artisan_module/data/config.yml" <<'YAML'
currencies:
  allowed: [coins]
  default: coins
  display:
    coins: "{amount} coins"
YAML

cd "$WORK" && : > console.in
tail -f console.in | java -Xmx2G -jar server.jar --nogui > server.log 2>&1 &
trap 'echo stop >> "$WORK/console.in"; sleep 10; pkill -f "tail -f console.in" || true' EXIT
for _ in $(seq 1 100); do grep -q "Done (" server.log && break; sleep 3; done
sleep 8
(cd "$E2E/bot" && npm install --silent && node run.js 25601 "$WORK/console.in")
! grep -n -E "Exception|SEVERE" server.log
