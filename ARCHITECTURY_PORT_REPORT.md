# CatchRateDisplay — Architectury Multiloader Port Report

> Comprehensive analysis, corrections, and complete task breakdown for converting from Fabric-only to Architectury multiloader (Fabric + NeoForge).

---

## 1. Executive Summary

The CatchRateDisplay mod (2,625 lines of Kotlin across 15 files) is a **good candidate** for multiloader porting. Only **7 of 15 source files** reference Fabric APIs. The core catch-rate math, formula engine, ball multiplier logic, and constants are already platform-agnostic. The main work involves:

1. Restructuring the Gradle build into 3 subprojects
2. Renaming Yarn mappings → Mojang official mappings
3. Extracting ~50 lines of Fabric API calls into platform interfaces
4. Creating NeoForge platform implementations (~4 new files)
5. Creating Fabric platform wrappers (~4 new files)

**Estimated total effort:** ~25 new/modified files, ~800 lines of new platform code.

---

## 2. Corrections & Improvements to the Original Plan

### 2.1 Critical Corrections

| # | Original Plan Statement | Issue | Correction |
|---|---|---|---|
| 1 | "Mojang official mappings across all modules (requires renaming Yarn class names)" | Partially correct — Cobblemon itself uses `officialMojangMappings()` + Parchment, so the Cobblemon API classes already use Mojmap names in the dev environment. The renaming is only needed for **Minecraft vanilla** class references. | Use `loom.layered { officialMojangMappings(); parchment(...) }` to match Cobblemon's exact setup. Cobblemon API classes need **zero renames** — only vanilla MC imports change. |
| 2 | "ServiceLoader pattern for platform abstraction" | The plan describes `lateinit var` singletons, NOT Java `ServiceLoader`. Architectury's native pattern is `@ExpectPlatform` static methods, which is simpler and requires no runtime initialization. | **Use `@ExpectPlatform`** instead of manual `Services.kt` singletons. This eliminates initialization-order bugs and is the standard Architectury pattern. Cobblemon itself uses Architectury Loom — matching their approach is safest. |
| 3 | "`CatchRateResult.kt` — extracted from CatchRateCalculator" | `CatchRateResult` is a `data class` **already defined inside** `CatchRateCalculator.kt`. The plan suggests extracting it but doesn't explain why. | Not strictly necessary, but recommended. Extract it so `CatchRateHudRenderer` can import it without pulling in the calculator's client-side MC dependencies. Mark as optional refactor. |
| 4 | "CatchRatePackets.kt — CustomPayload→CustomPacketPayload" | The plan says to put this in `common/` but `CustomPayload` (Yarn) = `CustomPacketPayload` (Mojmap). However, in NeoForge 1.21.1, `CustomPacketPayload` lives at `net.minecraft.network.protocol.common.custom.CustomPacketPayload` with a different `Type` inner class. The Fabric and NeoForge payload registration APIs are **completely different**. | The payload **data classes** go in common. Payload **registration** (codecs, channel setup) must be per-platform. The `CODEC` objects can stay in common (they use vanilla `StreamCodec` which is identical on both platforms), but the `TYPE` / `ID` declaration needs care — see §4.5. |
| 5 | "CatchRateHudRenderer.kt — Remove HudRenderCallback interface, expose render(GuiGraphics)" | Correct direction, but the plan doesn't address that `onHudRender(DrawContext, RenderTickCounter)` becomes `render(GuiGraphics, DeltaTracker)` in Mojmap, and NeoForge uses `RegisterGuiLayersEvent` or `RenderGuiEvent.Post`, which passes `GuiGraphics` and `float partialTick` (not `DeltaTracker`). | The common `CatchRateHudRenderer` should expose `fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker)`. The NeoForge module must bridge from `RenderGuiLayerEvent.Post` (which gives `getGuiGraphics()` + `getPartialTick()`) — a DeltaTracker can be obtained from `Minecraft.getInstance().timer` or the event's float can be used directly. **See §4.3 for the recommended approach.** |
| 6 | "NeoForge: CatchRateHelloPayload.kt — empty payload, server sends on join" | Unnecessary complexity. NeoForge has `ICommonPacketListener.hasChannel()` which is the direct equivalent of Fabric's `ClientPlayNetworking.canSend()`. NeoForge also negotiates channels automatically during the configuration phase. | **Drop the HelloPayload entirely.** Use NeoForge's built-in channel negotiation. On Fabric, keep using `ClientPlayNetworking.canSend()`. Abstract this behind a `PlatformHelper.isServerChannelAvailable(channelId)` method. |
| 7 | "Build files: 3 `build.gradle.kts`" — descriptions are vague | The plan lists bullet points for each build file but doesn't specify which Cobblemon artifacts to use per subproject. | **common:** `modCompileOnly("com.cobblemon:mod:VERSION")` — the common/intermediary artifact. **fabric:** `modImplementation("com.cobblemon:fabric:VERSION")`. **neoforge:** `modImplementation("com.cobblemon:neoforge:VERSION")`. See §5 for complete build files. |
| 8 | "CatchRateKeybinds.kt — Create KeyMapping objects but do NOT register them" | The plan correctly identifies that registration is platform-specific, but `KeyBinding` (Yarn) constructor uses `InputUtil.Type` (Yarn) = `InputConstants.Type` (Mojmap). The keybind category string and translation keys are identical across platforms. | KeyMapping objects should be created in common as `val` properties (not `lateinit`). Platform modules call their respective registration APIs with these pre-created `KeyMapping` instances. |
| 9 | Plan says "Mixin JSON — dropped (no active mixins)" | The mixin JSON references `ItemStackMixin` in the client array, but `fabric.mod.json` has `"mixins": []` so it's already unused. | Correct to drop. Confirm no runtime dependency on any mixin behavior. |

### 2.2 Missing Items in the Original Plan

| # | Missing Item | Impact | Details |
|---|---|---|---|
| 1 | **`@ExpectPlatform` vs ServiceLoader** | Architecture decision | The plan uses a manual singleton pattern. `@ExpectPlatform` is the Architectury-native equivalent, requires no initialization code, works at compile time. Recommended. |
| 2 | **Cobblemon uses Mojmap + Parchment** | Build configuration | Cobblemon uses `parchment-1.21:2024.07.28`. Matching this gives identical parameter names and avoids mapping mismatches. The plan doesn't mention Parchment. |
| 3 | **Access wideners** | Build configuration | Cobblemon uses an access widener. If the mod needs to access any protected/private MC fields, an access widener must be configured per-platform. Currently no AW needed but should be documented. |
| 4 | **`CatchRateDisplayMod.MOD_ID` usage in packet IDs** | Common module | `CatchRatePackets` references `CatchRateDisplayMod.MOD_ID` for `Identifier.of()`. Since `CatchRateDisplayMod` is the Fabric entrypoint, this constant must be extracted to a common `ModConstants` object. |
| 5 | **`CatchRateDisplayMod.LOGGER` and `.debug()` usage** | Common module | 10+ files reference `CatchRateDisplayMod.LOGGER` and `.debug()`. These must be available in common. Extract to a shared object. |
| 6 | **`showServerWarning()` in CatchRateClientNetworking** | Client-side Minecraft API | Uses `MinecraftClient.getInstance()` / `Text` / `Formatting` — these are vanilla MC classes that rename to Mojmap but are otherwise fine in common. **No abstraction needed**, just Mojmap renames. |
| 7 | **NeoForge `@Mod` annotation** | NeoForge entrypoint | NeoForge uses annotation-based mod discovery, not entrypoint lists. The `@Mod("catchrate-display")` class needs proper Kotlin structure with `@EventBusSubscriber`. |
| 8 | **NeoForge packet handler threading** | Networking | NeoForge's `IPayloadContext` handlers run on the network thread by default. Must use `context.enqueueWork {}` for main-thread operations. Fabric's `ClientPlayNetworking.registerGlobalReceiver` already runs on the main thread. |
| 9 | **`pack.mcmeta`** | NeoForge resources | Required for NeoForge but not for Fabric. Must specify `pack_format: 15` for MC 1.21.1. |
| 10 | **Gradle wrapper version** | Build tooling | Architectury Loom 1.7+ requires Gradle 8.4+. Current project's Gradle wrapper version should be verified/updated. |
| 11 | **Kotlin compiler configuration** | Build tooling | Fabric uses `fabric-language-kotlin`, NeoForge uses `kotlinforforge-neoforge`. Both must be configured as dependencies in their respective subprojects. JVM target 21 must be set in all modules. |
| 12 | **`ServerPlayerEntity.world` → `ServerPlayer.level()`** | Mojmap rename | In Mojmap, `player.world` becomes `player.level()` (it's a method call, not a field). The plan's rename table lists `World→Level` but misses that access syntax changes. |
| 13 | **`client.options.hudHidden`** | Mojmap rename | `GameOptions.hudHidden` in Yarn → `Options.hideGui` in Mojmap. Not in the rename table. |
| 14 | **Identifier.of() dual-arg** | Mojmap rename | `Identifier.of(ns, path)` → `ResourceLocation.fromNamespaceAndPath(ns, path)`. Single-arg `Identifier.of(string)` → `ResourceLocation.parse(string)`. Plan only lists the first form. |
| 15 | **`KeyBinding.wasPressed()`** | Mojmap rename | `KeyBinding.wasPressed()` → `KeyMapping.consumeClick()`. Not in the rename table. |
| 16 | **`KeyBinding.isPressed`** | Mojmap rename | `KeyBinding.isPressed` → `KeyMapping.isDown`. Not in the rename table. |
| 17 | **`KeyBinding.boundKeyLocalizedText`** | Mojmap rename | → `KeyMapping.getTranslatedKeyMessage()`. Not in the rename table. |
| 18 | **Cobblemon `resourceIdentifier`** | Cobblemon API | `pokemon.species.resourceIdentifier` — verify this is the same across Cobblemon's Fabric and NeoForge builds. It should be, as Cobblemon abstracts this internally. |
| 19 | **`player.sendMessage()`** | Mojmap rename | `PlayerEntity.sendMessage(Text, boolean)` → `Player.sendSystemMessage(Component)` or `Player.displayClientMessage(Component, boolean)`. The plan doesn't address this. |
| 20 | **Test plan** | Quality | No testing strategy mentioned. Need a verification checklist for both platforms. |

---

## 3. Complete Mojmap Rename Reference

### 3.1 Class Renames (Yarn → Mojmap)

| Yarn | Mojmap | Full Mojmap Package |
|---|---|---|
| `MinecraftClient` | `Minecraft` | `net.minecraft.client.Minecraft` |
| `DrawContext` | `GuiGraphics` | `net.minecraft.client.gui.GuiGraphics` |
| `RenderTickCounter` | `DeltaTracker` | `net.minecraft.client.DeltaTracker` |
| `PlayerEntity` | `Player` | `net.minecraft.world.entity.player.Player` |
| `ServerPlayerEntity` | `ServerPlayer` | `net.minecraft.server.level.ServerPlayer` |
| `ClientPlayerEntity` | `LocalPlayer` | `net.minecraft.client.player.LocalPlayer` |
| `World` | `Level` | `net.minecraft.world.level.Level` |
| `Identifier` | `ResourceLocation` | `net.minecraft.resources.ResourceLocation` |
| `Text` | `Component` | `net.minecraft.network.chat.Component` |
| `Formatting` | `ChatFormatting` | `net.minecraft.ChatFormatting` |
| `KeyBinding` | `KeyMapping` | `net.minecraft.client.KeyMapping` |
| `InputUtil` | `InputConstants` | `com.mojang.blaze3d.platform.InputConstants` |
| `InputUtil.Type` | `InputConstants.Type` | `com.mojang.blaze3d.platform.InputConstants.Type` |
| `ItemStack` | `ItemStack` | `net.minecraft.world.item.ItemStack` |
| `RegistryByteBuf` | `RegistryFriendlyByteBuf` | `net.minecraft.network.RegistryFriendlyByteBuf` |
| `PacketCodec` | `StreamCodec` | `net.minecraft.network.codec.StreamCodec` |
| `CustomPayload` | `CustomPacketPayload` | `net.minecraft.network.protocol.common.custom.CustomPacketPayload` |
| `CustomPayload.Id` | `CustomPacketPayload.Type` | (inner class) |
| `HitResult` | `HitResult` | `net.minecraft.world.phys.HitResult` |
| `EntityHitResult` | `EntityHitResult` | `net.minecraft.world.phys.EntityHitResult` |

### 3.2 Method/Field Renames

| Yarn | Mojmap | Context |
|---|---|---|
| `.drawTextWithShadow()` | `.drawString()` | `GuiGraphics` |
| `.drawHorizontalLine()` | `.hLine()` | `GuiGraphics` |
| `.drawVerticalLine()` | `.vLine()` | `GuiGraphics` |
| `client.player` | `minecraft.player` | `Minecraft` instance |
| `client.world` | `minecraft.level` | `Minecraft` instance |
| `client.textRenderer` | `minecraft.font` | `Minecraft` instance |
| `client.options.hudHidden` | `minecraft.options.hideGui` | `Options` |
| `Identifier.of(ns, path)` | `ResourceLocation.fromNamespaceAndPath(ns, path)` | Factory method |
| `Identifier.of(string)` | `ResourceLocation.parse(string)` | Factory method |
| `.writeString()` | `.writeUtf()` | `FriendlyByteBuf` |
| `.readString()` | `.readUtf()` | `FriendlyByteBuf` |
| `KeyBinding.wasPressed()` | `KeyMapping.consumeClick()` | Keybind polling |
| `KeyBinding.isPressed` | `KeyMapping.isDown` | Keybind state |
| `KeyBinding.boundKeyLocalizedText` | `KeyMapping.getTranslatedKeyMessage()` | Display name |
| `player.world` | `player.level()` | Entity method (note: **method call**, not field) |
| `player.sendMessage(text, overlay)` | `player.displayClientMessage(component, overlay)` | Chat/overlay message |
| `Text.literal(s)` | `Component.literal(s)` | Text creation |
| `Text.translatable(key)` | `Component.translatable(key)` | Translation |
| `.formatted(Formatting.X)` | `.withStyle(ChatFormatting.X)` | Text formatting |
| `.string` (on Text) | `.getString()` (on Component) | Get plain string |
| `DrawContext.fill()` | `GuiGraphics.fill()` | Same name, same behavior |

### 3.3 Renames Affecting Cobblemon API References

Cobblemon's own API classes (`com.cobblemon.mod.common.*`) use **Mojmap names internally**. These do NOT need renaming:
- `CobblemonClient.battle` — same
- `PokeBalls.getPokeBall()` — same  
- `BattleRegistry.getBattleByParticipatingPlayer()` — same
- `Pokemon`, `PokemonEntity`, `Gender`, `Species` — same
- `Statuses.SLEEP`, etc. — same
- `PokeBallItem`, `PokeBall` — same

However, Cobblemon's API methods that return/accept MC types will use Mojmap types:
- `status.name` returns a `ResourceLocation` (not `Identifier`)
- `pokemon.species.resourceIdentifier` returns a `ResourceLocation`

Since both your code and Cobblemon will be in Mojmap in the dev environment, this is seamless.

---

## 4. Detailed Architecture Design

### 4.1 Project Structure

```
CatchRateDisplay/
├── build.gradle.kts              (root — applies architectury-plugin)
├── settings.gradle.kts           (root — includes common, fabric, neoforge)
├── gradle.properties             (shared versions)
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
│
├── common/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/catchrate/
│       │   ├── CatchRateMod.kt                  (shared constants, logger, debug)
│       │   ├── CatchRateConstants.kt             (zero changes)
│       │   ├── CatchRateFormula.kt               (zero changes)
│       │   ├── CatchRateResult.kt                (extracted data class)
│       │   ├── BallMultiplierCalculator.kt       (Identifier→ResourceLocation)
│       │   ├── BallContextFactory.kt             (Mojmap renames)
│       │   ├── CatchRateCalculator.kt            (Mojmap renames)
│       │   ├── CatchRateKeybinds.kt              (Mojmap renames, no registration)
│       │   ├── client/
│       │   │   ├── BallComparisonCalculator.kt   (Mojmap renames)
│       │   │   ├── CatchRateHudRenderer.kt       (remove HudRenderCallback, Mojmap)
│       │   │   └── HudDrawing.kt                 (Mojmap renames)
│       │   ├── config/
│       │   │   └── CatchRateConfig.kt            (use @ExpectPlatform for configDir)
│       │   ├── network/
│       │   │   ├── CatchRatePackets.kt           (Mojmap renames)
│       │   │   ├── CatchRateClientNetworking.kt  (abstract platform calls)
│       │   │   └── CatchRateServerNetworking.kt  (abstract platform calls)
│       │   └── platform/
│       │       └── PlatformHelper.kt             (@ExpectPlatform static methods)
│       └── resources/
│           └── assets/catchrate-display/
│               ├── icon.png
│               └── lang/
│                   └── (27 .json files)
│
├── fabric/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/catchrate/
│       │   ├── fabric/
│       │   │   ├── CatchRateDisplayFabric.kt         (ModInitializer — server entry)
│       │   │   ├── CatchRateDisplayFabricClient.kt   (ClientModInitializer — client entry)
│       │   │   └── platform/
│       │   │       └── PlatformHelperImpl.kt         (@ExpectPlatform implementations)
│       └── resources/
│           └── fabric.mod.json
│
├── neoforge/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/catchrate/
│       │   └── neoforge/
│       │       ├── CatchRateDisplayNeoForge.kt       (@Mod class — common init)
│       │       ├── CatchRateDisplayNeoForgeClient.kt (client event subscribers)
│       │       └── platform/
│       │           └── PlatformHelperImpl.kt         (@ExpectPlatform implementations)
│       └── resources/
│           ├── META-INF/
│           │   └── neoforge.mods.toml
│           └── pack.mcmeta
```

### 4.2 Platform Abstraction via `@ExpectPlatform`

Instead of the ServiceLoader/singleton pattern in the original plan, use Architectury's `@ExpectPlatform`:

**common/src/.../platform/PlatformHelper.kt:**
```kotlin
package com.catchrate.platform

import dev.architectury.injectables.annotations.ExpectPlatform
import java.nio.file.Path

object PlatformHelper {
    @JvmStatic @ExpectPlatform
    fun getConfigDir(): Path = throw AssertionError()

    @JvmStatic @ExpectPlatform
    fun isServerModPresent(): Boolean = throw AssertionError()

    @JvmStatic @ExpectPlatform
    fun sendToServer(payload: Any): Unit = throw AssertionError()

    @JvmStatic @ExpectPlatform
    fun sendToPlayer(player: Any, payload: Any): Unit = throw AssertionError()
}
```

**fabric/src/.../fabric/platform/PlatformHelperImpl.kt:**
```kotlin
package com.catchrate.fabric.platform

// Must be in {original_package}.fabric.{ClassName}Impl for @ExpectPlatform
object PlatformHelperImpl {
    @JvmStatic
    fun getConfigDir(): Path = FabricLoader.getInstance().configDir

    @JvmStatic
    fun isServerModPresent(): Boolean = 
        ClientPlayNetworking.canSend(CatchRatePackets.REQUEST_TYPE)

    @JvmStatic
    fun sendToServer(payload: Any) = 
        ClientPlayNetworking.send(payload as CustomPacketPayload)

    @JvmStatic
    fun sendToPlayer(player: Any, payload: Any) = 
        ServerPlayNetworking.send(player as ServerPlayer, payload as CustomPacketPayload)
}
```

**neoforge/src/.../neoforge/platform/PlatformHelperImpl.kt:**
```kotlin
package com.catchrate.neoforge.platform

object PlatformHelperImpl {
    @JvmStatic
    fun getConfigDir(): Path = FMLPaths.CONFIGDIR.get()

    @JvmStatic
    fun isServerModPresent(): Boolean = 
        NeoForgeNetworkHelper.isServerModPresent()

    @JvmStatic
    fun sendToServer(payload: Any) = 
        PacketDistributor.sendToServer(payload as CustomPacketPayload)

    @JvmStatic
    fun sendToPlayer(player: Any, payload: Any) = 
        PacketDistributor.sendToPlayer(player as ServerPlayer, payload as CustomPacketPayload)
}
```

> **Note on `@ExpectPlatform` with Kotlin:** The Architectury plugin transforms calls to `@ExpectPlatform` methods by rewriting the call target to `{package}.{platform}.{Class}Impl.{method}`. The `Impl` class MUST be in the correct sub-package (`fabric` or `neoforge` under the original package).

### 4.3 HUD Rendering Bridge

The `CatchRateHudRenderer` in common should be a plain class (not implementing any interface):

```kotlin
// common — no platform imports
class CatchRateHudRenderer {
    fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        // ... all existing render logic with Mojmap names ...
    }
}
```

**Fabric bridge:**
```kotlin
// In CatchRateDisplayFabricClient.onInitializeClient()
val renderer = CatchRateHudRenderer()
HudRenderCallback.EVENT.register { drawContext, tickCounter ->
    renderer.render(drawContext, tickCounter)
}
```

**NeoForge bridge:**
```kotlin
// In CatchRateDisplayNeoForgeClient — registered on NeoForge.EVENT_BUS
@SubscribeEvent
fun onRenderGui(event: RenderGuiLayerEvent.Post) {
    // RenderGuiLayerEvent.Post fires after each vanilla GUI layer.
    // Only render after the last layer to avoid drawing multiple times.
    // Alternatively, use RegisterGuiLayersEvent to register as a named layer.
    renderer.render(event.guiGraphics, Minecraft.getInstance().deltaTracker)
}
```

**Recommended approach for NeoForge:** Register via `RegisterGuiLayersEvent` (mod bus) to add a named GUI layer:
```kotlin
@SubscribeEvent
fun onRegisterGuiLayers(event: RegisterGuiLayersEvent) {
    event.registerAboveAll(
        ResourceLocation.fromNamespaceAndPath("catchrate-display", "hud")
    ) { guiGraphics, deltaTracker ->
        renderer.render(guiGraphics, deltaTracker)
    }
}
```

### 4.4 Networking Architecture

#### Common module

`CatchRatePackets.kt` stays in common with Mojmap renames:
- `CustomPayload` → `CustomPacketPayload`
- `PacketCodec` → `StreamCodec`  
- `RegistryByteBuf` → `RegistryFriendlyByteBuf`
- `Identifier.of()` → `ResourceLocation.fromNamespaceAndPath()`
- `CustomPayload.Id` → `CustomPacketPayload.Type`
- `.writeString()` → `.writeUtf()`
- `.readString()` → `.readUtf()`

`CatchRateClientNetworking.kt` keeps all caching/throttling/state logic. Replace:
- `PayloadTypeRegistry` calls → removed (platform-specific)
- `ClientPlayNetworking.registerGlobalReceiver` → removed (platform registers, calls `onResponseReceived()`)
- `ClientPlayNetworking.canSend` → `PlatformHelper.isServerModPresent()`
- `ClientPlayNetworking.send` → `PlatformHelper.sendToServer()`
- `ClientPlayConnectionEvents` → removed (platform calls `onConnect()` / `onDisconnect()`)
- `MinecraftClient` → `Minecraft` (plain Mojmap rename)

New public methods to expose for platform modules:
```kotlin
fun onResponseReceived(payload: CatchRateResponsePayload)  // called by platform receiver
fun onConnect()   // called by platform on join
fun onDisconnect() // called by platform on disconnect
```

`CatchRateServerNetworking.kt` keeps all calculation logic. Replace:
- `PayloadTypeRegistry` calls → removed
- `ServerPlayNetworking.registerGlobalReceiver` → removed (platform registers, calls `handleCatchRateRequest()`)
- `ServerPlayNetworking.send()` → `PlatformHelper.sendToPlayer()`

Make `handleCatchRateRequest(player, request)` **public** for platform modules to call.

#### Fabric module

```kotlin
// In CatchRateDisplayFabric.onInitialize()
PayloadTypeRegistry.playC2S().register(CatchRateRequestPayload.TYPE, CatchRateRequestPayload.CODEC)
PayloadTypeRegistry.playS2C().register(CatchRateResponsePayload.TYPE, CatchRateResponsePayload.CODEC)

ServerPlayNetworking.registerGlobalReceiver(CatchRateRequestPayload.TYPE) { payload, context ->
    CatchRateServerNetworking.handleCatchRateRequest(context.player(), payload)
}
```

```kotlin
// In CatchRateDisplayFabricClient.onInitializeClient()
ClientPlayNetworking.registerGlobalReceiver(CatchRateResponsePayload.TYPE) { payload, _ ->
    CatchRateClientNetworking.onResponseReceived(payload)
}

ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
    CatchRateClientNetworking.onDisconnect()
}

ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
    CatchRateClientNetworking.onConnect()
}
```

#### NeoForge module

```kotlin
// In CatchRateDisplayNeoForge — on mod bus
@SubscribeEvent
fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
    val registrar = event.registrar("catchrate-display").versioned("1")
    registrar.playToServer(
        CatchRateRequestPayload.TYPE,
        CatchRateRequestPayload.CODEC
    ) { payload, context ->
        context.enqueueWork {
            val player = context.player() as? ServerPlayer ?: return@enqueueWork
            CatchRateServerNetworking.handleCatchRateRequest(player, payload)
        }
    }
    registrar.playToClient(
        CatchRateResponsePayload.TYPE,
        CatchRateResponsePayload.CODEC
    ) { payload, context ->
        context.enqueueWork {
            CatchRateClientNetworking.onResponseReceived(payload)
        }
    }
}
```

```kotlin
// Client events on NeoForge.EVENT_BUS
@SubscribeEvent
fun onClientDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
    CatchRateClientNetworking.onDisconnect()
}

@SubscribeEvent
fun onClientConnect(event: ClientPlayerNetworkEvent.LoggingIn) {
    CatchRateClientNetworking.onConnect()
}
```

**Server mod detection on NeoForge:**  
NeoForge automatically negotiates channels during the configuration phase. A channel registered as **non-optional** will cause a disconnect if the other side doesn't have it. Register the channels as **optional** via `registrar.optional()`, then check `hasChannel()`:

```kotlin
// In NeoForge PlatformHelperImpl
fun isServerModPresent(): Boolean {
    val connection = Minecraft.getInstance().connection ?: return false
    return connection.hasChannel(CatchRateRequestPayload.TYPE)
}
```

This eliminates the need for a custom "hello" payload.

### 4.5 Payload Type/ID Declaration (Critical Detail)

In Yarn/Fabric, payload IDs use `CustomPayload.Id<T>`. In Mojmap, this is `CustomPacketPayload.Type<T>`. The constructor is the same — it wraps a `ResourceLocation`.

```kotlin
// Common module — Mojmap names
data class CatchRateRequestPayload(...) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<CatchRateRequestPayload>(
            ResourceLocation.fromNamespaceAndPath("catchrate-display", "catch_rate_request")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CatchRateRequestPayload> = object : StreamCodec<...> {
            override fun encode(buf: RegistryFriendlyByteBuf, payload: CatchRateRequestPayload) {
                buf.writeLong(payload.pokemonUuid.mostSignificantBits)
                buf.writeLong(payload.pokemonUuid.leastSignificantBits)
                buf.writeUtf(payload.ballItemId)
                buf.writeInt(payload.turnCount)
            }
            override fun decode(buf: RegistryFriendlyByteBuf): CatchRateRequestPayload {
                return CatchRateRequestPayload(
                    pokemonUuid = UUID(buf.readLong(), buf.readLong()),
                    ballItemId = buf.readUtf(),
                    turnCount = buf.readInt()
                )
            }
        }
    }
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
```

This is identical on both platforms — `CustomPacketPayload` and `StreamCodec` are vanilla Minecraft classes.

### 4.6 Keybind Architecture

**Common (create but don't register):**
```kotlin
object CatchRateKeybinds {
    val toggleHudKey = KeyMapping("key.catchrate.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.catchrate")
    val showComparisonKey = KeyMapping("key.catchrate.show_comparison", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.catchrate")
    val resetPositionKey = KeyMapping("key.catchrate.reset_position", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.catchrate")
    // ... etc

    var isComparisonHeld = false; private set
    val comparisonKeyName: String get() = showComparisonKey.translatedKeyMessage.string

    fun tick(minecraft: Minecraft) { /* unchanged logic with Mojmap names */ }
}
```

**Fabric (register):**
```kotlin
CatchRateKeybinds::class.java.declaredFields
    .filter { KeyMapping::class.java.isAssignableFrom(it.type) }
    .forEach { field ->
        field.isAccessible = true
        KeyBindingHelper.registerKeyBinding(field.get(CatchRateKeybinds) as KeyMapping)
    }
// Or explicitly:
KeyBindingHelper.registerKeyBinding(CatchRateKeybinds.toggleHudKey)
KeyBindingHelper.registerKeyBinding(CatchRateKeybinds.showComparisonKey)
// ... etc
```

**NeoForge (register via event):**
```kotlin
@SubscribeEvent
fun onRegisterKeys(event: RegisterKeyMappingsEvent) {
    event.register(CatchRateKeybinds.toggleHudKey)
    event.register(CatchRateKeybinds.showComparisonKey)
    // ... etc
}
```

---

## 5. Build Configuration

### 5.1 Version Matrix

| Component | Version | Notes |
|---|---|---|
| Minecraft | 1.21.1 | Target |
| Architectury Loom | 1.9+ (use `1.9.+` or match Cobblemon's `1.11-SNAPSHOT`) | Build tool plugin |
| Architectury Plugin | 3.4-SNAPSHOT | Class transformation |
| Architectury API | **Not needed** | Only needed if using Architectury runtime APIs (events, networking). We abstract manually. |
| Parchment | 2024.07.28 (for MC 1.21) | Matches Cobblemon's setup |
| NeoForge | 21.1.77+ (recommend latest stable ~21.1.219) | NeoForge loader |
| Fabric Loader | 0.16.7 | Same as current |
| Fabric API | 0.116.7+1.21.1 | Same as current |
| Fabric Language Kotlin | 1.13.4+kotlin.2.2.0 | Same as current |
| Kotlin for Forge | 5.11.0 | NeoForge Kotlin support |
| Cobblemon | 1.7.1+1.21.1 | Same as current target |
| Kotlin | 2.2.0 | Same as current |
| Java | 21 | Same as current |

### 5.2 Alternative: Use Architectury API Runtime

The plan currently uses manual platform abstraction. An alternative is to use the **Architectury API** runtime library, which provides cross-platform wrappers for:
- Networking (`NetworkManager`)
- Key bindings (`KeyMappingHelper`)
- Events (`ClientTickEvent`, `GuiEvent.RenderHud`)  
- Platform queries (`Platform.getConfigFolder()`)

**Pros:** Less boilerplate, maintained abstractions, fewer platform-specific files.
**Cons:** Additional dependency (~500KB), another mod users must install (or shade it), API may not cover all edge cases.

**Recommendation:** Start WITHOUT Architectury API. The mod's platform surface area is small enough (~50 lines of platform code) that manual abstraction with `@ExpectPlatform` is cleaner and avoids an extra dependency. Architectury API can be added later if needed.

---

## 6. File-by-File Migration Guide

### 6.1 Files That Move to Common — No Logic Changes (Mojmap Renames Only)

| File | Changes Required |
|---|---|
| `CatchRateConstants.kt` | **Zero** — no MC imports |
| `CatchRateFormula.kt` | **Zero** — no MC imports |
| `CatchRateResult.kt` (new) | Extract `data class CatchRateResult` from `CatchRateCalculator.kt` |
| `BallMultiplierCalculator.kt` | 1 rename: `Identifier` → `ResourceLocation`, `Identifier.of()` → `ResourceLocation.fromNamespaceAndPath()` |
| `BallContextFactory.kt` | `PlayerEntity` → `Player`, `ServerPlayerEntity` → `ServerPlayer`, `World` → `Level`, `player.world` → `player.level()` |
| `CatchRateCalculator.kt` | `MinecraftClient` → `Minecraft`, `ItemStack` package change, `Identifier` → `ResourceLocation`, `CustomPayload.Id` → `CustomPacketPayload.Type` |
| `HudDrawing.kt` | `DrawContext` → `GuiGraphics`, `Formatting` → `ChatFormatting`, `.drawTextWithShadow()` → `.drawString()`, `.drawHorizontalLine()` → `.hLine()`, `.drawVerticalLine()` → `.vLine()`, `.formatted()` → `.withStyle()` |
| `BallComparisonCalculator.kt` | `MinecraftClient` → `Minecraft`, `EntityHitResult` / `HitResult` package changes |

### 6.2 Files That Move to Common — Logic Changes Required

| File | Fabric Code to Remove | Replacement |
|---|---|---|
| `CatchRateHudRenderer.kt` | `implements HudRenderCallback`, `onHudRender(DrawContext, RenderTickCounter)` | Plain class, `render(GuiGraphics, DeltaTracker)` method. All Mojmap renames. |
| `CatchRateKeybinds.kt` | `KeyBindingHelper.registerKeyBinding()` calls in `bind()` | Create `KeyMapping` objects as `val` properties. Remove `register()` method. Add `allKeys(): List<KeyMapping>` for platform use. |
| `CatchRateConfig.kt` | `FabricLoader.getInstance().configDir` | `PlatformHelper.getConfigDir()` via `@ExpectPlatform` |
| `CatchRateClientNetworking.kt` | `PayloadTypeRegistry`, `ClientPlayNetworking`, `ClientPlayConnectionEvents` | Expose `onResponseReceived()`, `onConnect()`, `onDisconnect()`. Use `PlatformHelper.sendToServer()`, `PlatformHelper.isServerModPresent()`. |
| `CatchRateServerNetworking.kt` | `PayloadTypeRegistry`, `ServerPlayNetworking` | Make `handleCatchRateRequest()` public. Use `PlatformHelper.sendToPlayer()`. Remove `initialize()` or make it no-op. |
| `CatchRatePackets.kt` | None (no Fabric imports) | Mojmap renames only: `CustomPayload` → `CustomPacketPayload`, `PacketCodec` → `StreamCodec`, etc. |

### 6.3 Files That Are Replaced (Fabric-Only → Platform-Specific)

| Old File | Replaced By |
|---|---|
| `CatchRateDisplayMod.kt` (ClientModInitializer) | `fabric/CatchRateDisplayFabricClient.kt` + `neoforge/CatchRateDisplayNeoForgeClient.kt` |
| `CatchRateDisplayModServer.kt` (ModInitializer) | `fabric/CatchRateDisplayFabric.kt` + `neoforge/CatchRateDisplayNeoForge.kt` |

### 6.4 New Shared File

| File | Purpose |
|---|---|
| `common/.../CatchRateMod.kt` | Contains `MOD_ID`, `LOGGER`, `debug()`, `DEBUG_ENABLED` — extracted from `CatchRateDisplayMod.kt` companion object. All files reference this instead. |

### 6.5 New Platform Files

| File | Purpose |
|---|---|
| **Fabric** | |
| `CatchRateDisplayFabric.kt` | `ModInitializer` — registers server networking |
| `CatchRateDisplayFabricClient.kt` | `ClientModInitializer` — registers client networking, HUD callback, tick events, keybinds |
| `platform/PlatformHelperImpl.kt` | `@ExpectPlatform` impls for config dir, networking, server detection |
| **NeoForge** | |
| `CatchRateDisplayNeoForge.kt` | `@Mod` class — registers payload handlers on mod bus |
| `CatchRateDisplayNeoForgeClient.kt` | Client event subscribers — GUI layers, tick events, keybinds, connect/disconnect |
| `platform/PlatformHelperImpl.kt` | `@ExpectPlatform` impls for config dir, networking, server detection |

---

## 7. Resource File Plan

### 7.1 Common Resources (`common/src/main/resources/`)

```
assets/catchrate-display/
├── icon.png                      (copied from existing)
└── lang/
    ├── de_de.json
    ├── en_us.json
    ├── es_es.json
    ├── ... (all 27 language files — copied as-is)
```

No mixin JSON needed (currently unused).

### 7.2 Fabric Resources (`fabric/src/main/resources/`)

**fabric.mod.json** — updated from current:
```json
{
  "schemaVersion": 1,
  "id": "catchrate-display",
  "version": "${version}",
  "name": "Cobblemon Catch Rate Display",
  "description": "...",
  "authors": ["Akkiruk"],
  "contact": { ... },
  "license": "MIT",
  "icon": "assets/catchrate-display/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": [{ "adapter": "kotlin", "value": "com.catchrate.fabric.CatchRateDisplayFabric" }],
    "client": [{ "adapter": "kotlin", "value": "com.catchrate.fabric.CatchRateDisplayFabricClient" }]
  },
  "mixins": [],
  "depends": {
    "fabricloader": ">=0.15.0",
    "minecraft": ">=1.21 <1.22",
    "cobblemon": ">=1.6.0",
    "fabric-language-kotlin": ">=1.10.0"
  },
  "recommends": { "fabric-api": "*" }
}
```

### 7.3 NeoForge Resources (`neoforge/src/main/resources/`)

**META-INF/neoforge.mods.toml:**
```toml
modLoader = "kotlinforforge"
loaderVersion = "[5.10,)"
license = "MIT"

[[mods]]
modId = "catchrate-display"
version = "${version}"
displayName = "Cobblemon Catch Rate Display"
description = "Shows real-time catch rate percentages during Cobblemon battles."
authors = "Akkiruk"
logoFile = "assets/catchrate-display/icon.png"

[[dependencies."catchrate-display"]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies."catchrate-display"]]
modId = "minecraft"
type = "required"
versionRange = "[1.21,1.22)"
ordering = "NONE"
side = "BOTH"

[[dependencies."catchrate-display"]]
modId = "cobblemon"
type = "required"
versionRange = "[1.6.0,)"
ordering = "AFTER"
side = "BOTH"

[[dependencies."catchrate-display"]]
modId = "kotlinforforge"
type = "required"
versionRange = "[5.10,)"
ordering = "NONE"
side = "BOTH"
```

**pack.mcmeta:**
```json
{
  "pack": {
    "description": "Cobblemon Catch Rate Display",
    "pack_format": 15
  }
}
```

---

## 8. Execution Order (Revised)

### Phase 1: Build System Setup
1. Update `gradle.properties` with new version properties
2. Replace `settings.gradle.kts` with multiloader version (include common, fabric, neoforge)
3. Replace root `build.gradle.kts` with Architectury plugin setup
4. Create `common/build.gradle.kts`
5. Create `fabric/build.gradle.kts`
6. Create `neoforge/build.gradle.kts`
7. Verify Gradle wrapper is 8.4+ (`gradle/wrapper/gradle-wrapper.properties`)

### Phase 2: Common Module
8. Create `common/src/main/kotlin/com/catchrate/CatchRateMod.kt` (shared constants/logger)
9. Create `common/src/main/kotlin/com/catchrate/platform/PlatformHelper.kt` (`@ExpectPlatform`)
10. Copy & rename: `CatchRateConstants.kt` (no changes)
11. Copy & rename: `CatchRateFormula.kt` (no changes)
12. Extract & create: `CatchRateResult.kt`
13. Copy & rename: `BallMultiplierCalculator.kt` (Mojmap renames)
14. Copy & rename: `BallContextFactory.kt` (Mojmap renames)
15. Copy & rename: `CatchRateCalculator.kt` (Mojmap renames)
16. Copy & rename: `CatchRateKeybinds.kt` (Mojmap renames, remove registration)
17. Copy & rename: `HudDrawing.kt` (Mojmap renames)
18. Copy & rename: `BallComparisonCalculator.kt` (Mojmap renames)
19. Refactor: `CatchRateHudRenderer.kt` (remove Fabric interface, Mojmap renames)
20. Refactor: `CatchRateConfig.kt` (use `@ExpectPlatform`, Mojmap renames)
21. Refactor: `CatchRatePackets.kt` (Mojmap renames)
22. Refactor: `CatchRateClientNetworking.kt` (extract platform calls, Mojmap renames)
23. Refactor: `CatchRateServerNetworking.kt` (extract platform calls, Mojmap renames)
24. Copy all resources: `assets/catchrate-display/lang/*.json`, `icon.png`

### Phase 3: Fabric Module
25. Create `fabric/platform/PlatformHelperImpl.kt`
26. Create `fabric/CatchRateDisplayFabric.kt` (server entrypoint)
27. Create `fabric/CatchRateDisplayFabricClient.kt` (client entrypoint)
28. Create `fabric/src/main/resources/fabric.mod.json`

### Phase 4: NeoForge Module
29. Create `neoforge/platform/PlatformHelperImpl.kt`
30. Create `neoforge/CatchRateDisplayNeoForge.kt` (common/server)
31. Create `neoforge/CatchRateDisplayNeoForgeClient.kt` (client events)
32. Create `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
33. Create `neoforge/src/main/resources/pack.mcmeta`

### Phase 5: Verification
34. Run `./gradlew common:build` — verify compilation with Mojmap
35. Run `./gradlew fabric:build` — verify Fabric jar produces
36. Run `./gradlew neoforge:build` — verify NeoForge jar produces
37. Test Fabric in-game: keybinds, HUD rendering, networking
38. Test NeoForge in-game: keybinds, HUD rendering, networking
39. Delete old `src/` directory
40. Update README.md with multiloader build instructions

---

## 9. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Cobblemon API differences between fabric/neoforge artifacts | Low | High | Cobblemon uses Architectury Loom internally — API surface is identical. Only networking/loader bootstrapping differs, which is handled by Cobblemon itself. |
| `@ExpectPlatform` not working with Kotlin object methods | Medium | Medium | Use `@JvmStatic` on all `@ExpectPlatform` methods in a companion object or top-level functions. Test early in Phase 2. Fallback: use manual interface + lateinit. |
| NeoForge packet handler threading issues | Medium | High | All NeoForge packet handlers run on the netty thread. Must wrap game-state mutations in `context.enqueueWork {}`. Fabric runs handlers on the game thread by default. |
| Gradle build cache issues during development | Medium | Low | Use `./gradlew clean` liberally. Architectury Loom's remapping cache can become stale. |
| ModMenu integration (Fabric-only) | Low | Low | `fabric.mod.json` custom block for ModMenu still works. NeoForge doesn't have ModMenu — skip. |
| Workspace path spaces breaking Architectury Plugin | Medium | Medium | Cobblemon's own README warns about this. If building in the current path (contains spaces), clone to a space-free path for compilation. |
| Minecraft version drift | Low | Medium | Pin all versions in `gradle.properties`. This port targets MC 1.21.1 specifically. |

---

## 10. Final File Count

| Location | Files | New/Modified |
|---|---|---|
| Root (build scripts) | 3 | 3 replaced |
| common/src/kotlin | 14 | 12 migrated + 2 new (`CatchRateMod.kt`, `PlatformHelper.kt`) |
| common/resources | 28 | 28 copied (27 lang + icon) |
| fabric/src/kotlin | 3 | 3 new |
| fabric/resources | 1 | 1 new (updated `fabric.mod.json`) |
| neoforge/src/kotlin | 3 | 3 new |
| neoforge/resources | 2 | 2 new (`neoforge.mods.toml`, `pack.mcmeta`) |
| **Total** | **54** | **8 new code files, 3 replaced build files, 12 migrated, 29 resources** |

Old files deleted: 15 source files + 2 resource files from `src/`.

---

## 11. Verification Checklist

### Fabric
- [ ] Mod loads without errors in log
- [ ] Config file created in `.minecraft/config/catchrate-display.json`
- [ ] Keybinds appear in Controls settings
- [ ] HUD renders during Cobblemon battle
- [ ] Ball comparison panel shows on key hold
- [ ] Toggle HUD keybind works
- [ ] Position adjustment keybinds work
- [ ] Client-only mode works (no server mod)
- [ ] Server mode works (server + client mod installed)
- [ ] "Server mod not installed" warning appears on vanilla server
- [ ] Packet throttling works (no spam in logs)
- [ ] Language files load correctly
- [ ] F1 hides HUD
- [ ] Out-of-combat display works when looking at wild Pokémon

### NeoForge
- [ ] All items from Fabric checklist above
- [ ] `neoforge.mods.toml` loads correctly
- [ ] Kotlin for Forge initializes without issues
- [ ] Server channel detection works without custom hello payload
- [ ] No threading issues (no CME, no wrong-thread access)

### Cross-Platform
- [ ] Fabric client can connect to NeoForge server (with mod)
- [ ] NeoForge client can connect to Fabric server (with mod)
- [ ] Packet format is identical across platforms (same `StreamCodec`)
- [ ] Both platforms produce same catch rate calculations
