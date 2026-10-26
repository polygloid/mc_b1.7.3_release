# Beta 1.7.3 quality-of-life client

This patch adds:

- **Options → FOV**: 30–110 degrees, default 70; saved in `options.txt`. Water and death effects still apply.
- **Options → Controls**: paged bindings for movement, attack/use/pick block, inventory, chat, drop, fog, all nine hotbar slots, perspective, zoom, HUD, debug, smooth camera, fullscreen, screenshots, and texture reload. Supports keyboard and mouse buttons, marks duplicate bindings red, and includes Reset All. Select a binding and press Escape to unbind it. Escape remains the pause/back key; mouse-wheel hotbar scrolling remains available.
- **F5**: first person → rear view → front view. Rebindable; camera collision checks remain enabled.
- **Hold C**: 4× zoom with reduced mouse sensitivity. Releasing it, opening a menu, or losing game focus restores the normal view without changing the saved FOV or sensitivity.
- **macOS colour workaround**: uses float vertex colours instead of packed bytes on Apple's legacy OpenGL driver. It activates automatically on macOS, including an Intel JVM under Rosetta. `-Dminecraft.macosFloatColors=false` disables it; `true` forces it on other platforms for testing.
- **Multiplayer session validation** through Modern Beta's Authfix-compatible HTTP proxy. The requested `-Dhttp.proxyHost=proxy.modernbeta.org -Dhttp.proxyPort=8080` arguments are supported. These are also the defaults specifically for the legacy join request, so installing the patch doesn't require globally proxying unrelated HTTP traffic. Requests have timeouts and URL-encode session parameters. An authenticated launcher session is still required for online-mode servers.
- **Public player skins**: resolves usernames via Mojang's profile/session APIs and downloads textures over HTTPS. This is independent of the auth proxy and sends no login credentials. Missing profiles or network failures keep the default skin. Modern 64×64 skins are adapted to Beta's 64×32 classic model; independent left limbs, body overlays, and true three-pixel slim arms are not implemented. Use a classic-model skin for accurate arm textures.

## Build and install

Use a **Java 8 JDK** (not just a JRE):

```sh
export JAVA_HOME=/path/to/jdk8
./tools/build-client.sh
```

The output is `build/beta-1.7.3-qol-patch.jar`, a **jar-mod overlay** for a vanilla Beta 1.7.3 instance. In a launcher with jar-mod support (such as Prism Launcher), create a Beta 1.7.3 instance and add this file using the instance's Version → Add to Minecraft.jar action. It contains rebuilt classes, not game assets or native libraries; it is not a standalone executable jar. Other jar mods that replace the same classes may conflict.

For a MacBook Air with Apple Silicon, use **Intel/x86_64 Java 8 under Rosetta 2** and matching macOS x86_64 LWJGL 2 libraries/natives supplied by the launcher. The repository bundles Linux `.so` natives, which cannot run on macOS. This patch doesn't port LWJGL 2 to ARM or switch the game to LWJGL 3. Use the launcher's authenticated account login for multiplayer; a standalone development launch with the default `-` session is offline only.

If entering the proxy settings in your launcher, place these in **JVM arguments**, before the main class, not game arguments:

```text
-Dhttp.proxyHost=proxy.modernbeta.org -Dhttp.proxyPort=8080
```

Skins need HTTPS access to `api.mojang.com`, `sessionserver.mojang.com`, and `textures.minecraft.net`. Online validation needs access to `proxy.modernbeta.org:8080`. Legacy multiplayer servers must also have working server-side authentication support. The client patch cannot repair a server's dead session-check endpoint.

## Validation

```sh
./tools/test-client.sh
```

This rebuilds the client and runs regression checks for FOV persistence/clamping, old options compatibility, complete Controls pagination, key/mouse bindings and reset, perspective cycling, RGBA byte order, modern/legacy skin parsing, and authentication through a local mock HTTP proxy. It does not need an account or external service.

Optional OpenGL integration checks require a display (or Xvfb) and native libraries matching the host/JVM:

```sh
export LWJGL_NATIVE_DIR=/path/to/lwjgl2/natives
./tools/test-render.sh
```

These draw and read back RGB test quads through both colour paths, render the Controls and Options screens, check zoom/menu/focus behavior, and verify front/rear camera transforms at 18 yaw/pitch combinations. A Controls screenshot is written under `build/render-check/screenshots/`.

Validated in Linux with Java 8 and a virtual OpenGL display. The Mac M5 driver and Rosetta runtime still require an on-device check. Live online authentication and skin downloads could not be exercised in the cloud environment because its network policy blocks those destinations; request handling and parsing were tested locally with dummy data. No Microsoft/Mojang session credentials were available for a live login.

Generated classes, jars, and integration-test artifacts stay under ignored `build/`. The tracked MCP `bin/`, `temp/`, source assets, and dependency jars remain untouched by these scripts.
