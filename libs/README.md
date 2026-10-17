# libs/ — compile-only RFB api (NEVER committed, NEVER shipped)

`rfb-api.jar` is the `com/gtnewhorizons/retrofuturabootstrap/api` package
extracted from lwjgl3ify's `forgePatches` artifact. The RFB plugin
(`dev.taxmachine.anarchycompat.rfb`) needs it to compile; at runtime RFB
itself provides these classes (the dependency is `compileOnly`).

Extract it locally (CI does the same — see `.github/workflows/release.yml`):

```sh
curl -sSL -o /tmp/lwjgl3ify-forgePatches.jar \
  "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/lwjgl3ify/3.0.38/lwjgl3ify-3.0.38-forgePatches.jar"
mkdir -p /tmp/rfb-api libs
unzip -q /tmp/lwjgl3ify-forgePatches.jar 'com/gtnewhorizons/retrofuturabootstrap/api/*' -d /tmp/rfb-api
(cd libs && "$JAVA_HOME/bin/jar" cf rfb-api.jar -C /tmp/rfb-api .)
```
