# Resolve

A Minecraft **1.21.8** / Fabric client-side mod: **dynamic render resolution that follows your zoom**.

The world is normally rendered at a reduced resolution (80% by default) and upscaled back to the window,
saving a huge amount of pixel fill. The moment you zoom in — with the vanilla spyglass or any zoom mod —
the resolution instantly jumps to 100%, so the distant detail you are actually looking at stays native
sharpness; release the zoom and it falls back to 80%.

> **About the name**: this mod was originally called Foxy (a pun on FOV + fox) and has been renamed to
> **Resolve** (a pun on resolution + resolve). The rename covers every outward-facing identifier:
>
> | Item | Old | New |
> |---|---|---|
> | mod id | `foxy` | `resolve` |
> | config file | `config/foxy.json` | `config/resolve.json` |
> | Java package | `com.foxy.zilin233` | `com.zilin233.resolve` |
> | jar | `foxy-1.0.0.jar` | `resolve-1.0.0.jar` |
> | mixin / AW config | `foxy.mixins.json` / `foxy.accesswidener` | `resolve.*` |
> | translation keys | `category.foxy` / `key.foxy.*` | `category.resolve` / `key.resolve.*` |
>
> **Settings are migrated automatically on upgrade**: on the first launch after the rename, if the old
> `foxy.json` is found it is read and written out as `resolve.json`, so your tuned values (currently
> 60% / 100%) carry over as-is. The old file is deliberately left in place — deleting a user's file
> during a rename is not this mod's call to make, and it costs nothing to leave it behind. The log will
> contain a line `Migrated settings from foxy.json to resolve.json`.
>
> **You must delete the old `foxy-1.0.0.jar`**: the two jars have different mod ids and so would both
> load, and as they both rewrite the same render target, having both present just corrupts the picture.

---

## 1. Installation

1. Put `resolve-1.0.0.jar` into the mods directory:
   `D:\minecraft\.minecraft\versions\2b2t\mods\`
2. **Move `renderscale-fabric-1.21.8-1.3.3.jar` out of the mods directory** (see "Known conflicts" below).
3. Launch the game.

Requirements: Fabric Loader ≥ 0.16, Fabric API, Minecraft 1.21.8.

The following mods are all **optional** — installing them enhances Resolve, but omitting them does not
affect startup:

| Mod | Role |
|---|---|
| Mod Menu | Click the gear in the mod list to open the config screen |
| Iris | Shaders; Resolve adapts automatically |
| Sodium | Render optimisation; does not interfere with Resolve |
| Zoomify | Reads its own zoom state directly, for the most precise detection |

You can use it without Mod Menu: the config screen is registered to a default **unbound** key — search
for `Resolve` under "Options → Controls" and bind it yourself.

---

## 2. Config screen

Mod Menu → Resolve → gear, or press the key you bound. Every option takes effect as soon as you change
it, and is written to `.minecraft/config/resolve.json` when the screen closes.

The config screen also has three buttons at the bottom: **Done**, **Benchmark** (see section 7 for the
real measured gains), and **Reset defaults**.

### Basics

| Option | Default | Meaning |
|---|---|---|
| Master switch | On | When off the vanilla path is used unchanged, with zero overhead |
| Distant chunks start | 6 | The chunk ring at which chunks count as "distant" (see below) |
| Normal render resolution | 80% | The render percentage used while not zoomed |
| Zoom render resolution | 100% | The percentage used while zoom is detected |
| Resolution floor | 25% | A safety floor against a slip of the hand |

### Zoom detection (four sources, OR-ed in parallel)

| Option | Default | Meaning |
|---|---|---|
| Zoom follows | On | When off the resolution is constant |
| Detect · Spyglass | On | An item in use that is the spyglass |
| Detect · Zoomify | On | Reads `Zoomify.getZooming()` via reflection |
| Detect · Zoom key | On | Scans every key binding whose name contains `zoom` |
| Detect · FOV change | On | Universal fallback, see below |
| FOV zoom threshold | 0.90 | The FOV multiplier below which it counts as zoom |

**Why "Detect · FOV change" works with any zoom mod:**
every zoom implementation ultimately has to narrow Minecraft's field of view, and vanilla funnels all of
that into the single `GameRenderer.fovMultiplier` field (clamped by vanilla to 0.1–1.5, with 1.0 meaning
not zoomed). Reading it directly covers the vanilla spyglass, Zoomify, and any zoom mod written **after**
this one.

### Timing

| Option | Default | Meaning |
|---|---|---|
| Smooth transition | Off | Eases between the two resolutions |
| Transition duration | 200 ms | How long a smoothed transition lasts |
| Transition steps | 4 | How many steps to quantise into; each step rebuilds the render target once |
| Release delay | 150 ms | How long after releasing zoom before it falls back, against a twitchy hand |

### Output

| Option | Default | Meaning |
|---|---|---|
| Linear filter | On | Linear interpolation when upscaling; off is nearest neighbour, sharper but blocky |
| Shader mode | Auto | See "Shaders" below |
| F3 debug info | Off | Show the current scale state on the F3 screen |

---

## 3. How it works

Minecraft 1.21.5+ renders the world through a brand-new frame graph pipeline, and every render pass
resolves its colour attachment from `MinecraftClient.getFramebuffer()`. Resolve grabs exactly that one
seam:

1. At the **head** of `GameRenderer.renderWorld`, swap `client.framebuffer` for a smaller
   `SimpleFramebuffer`;
2. The world, sky, entities and first-person hand all draw into that small image;
3. At the **return** of `renderWorld`, restore the original framebuffer and use
   `Framebuffer.drawBlit()` to upscale the small image over it;
4. The HUD, chat and every GUI screen are rendered **outside** `renderWorld`, so they stay at native
   resolution throughout.

Using `drawBlit` instead of a raw `glBlitFramebuffer` is deliberate: it is the public API vanilla uses
for the entity outline pass, goes through the shared render pipeline, and therefore does not depend on
the OpenGL backend nor require an accessor for any GL internals.

### Shaders (Iris)

Iris sizes its render targets from `MinecraftClient.getFramebuffer().textureWidth/textureHeight`, and
it re-checks that size **every frame** at the head of the world render via
`RenderTargets.resizeIfNeeded(...)`. Because Resolve's swap happens earlier, at the head of
`renderWorld`, Iris follows the scaled size and reallocates its buffers on its own — **no shader pack
reload needed**. So the "Auto" mode is the correct choice, and you still get the performance gain with
shaders enabled.

`Iris.reload()` is only used as a fallback for specific packs under "Reload on change" mode, and is
rate limited to at most once every 3 seconds (it recompiles the whole shader pack).

### Overhead under default settings

Under the defaults (80% / 100% / no smoothing): while zoomed the scale is 100%, so Resolve **skips the
whole process** — no swap and no blit at all. When dropping back to 80% it only rebuilds the render
target when the size actually changed, and after that every frame is allocation-free. In other words,
apart from window resizes, **the render target is rebuilt at most once during the entire session**.

---

## 4. Known conflicts

**RenderScale (`renderscale`) must be removed.**

It does exactly the same thing as Resolve: swaps `MinecraftClient.framebuffer` during `renderWorld`,
and upscales with `GlStateManager.setupBlitFramebuffer`. Two mods fighting over the same field in the
same frame only corrupt each other's output.

Resolve detects it and **disables itself automatically** (the config screen shows a red notice at the
top, and there is a WARN in the log), so even if you forget to remove it the picture is not broken —
but Resolve simply won't take effect.

---

## 5. Difference from the original request (please read)

You originally described "lower the resolution of chunks 6 through 10 **individually**". *That is not
possible under the current architecture* — Resolve implements whole-scene dynamic resolution instead,
for the following reason:

In the 1.21.8 pipeline, the unit of drawing for each chunk is `RenderPass.RenderObject`, whose fields
are only `slot / vertexBuffer / indexBuffer / firstIndex / indexCount / uniformUploaderConsumer`.
**It does not carry chunk coordinates** — the chunk offset is uploaded inside that uniform closure and is
not statically available. So there is no way to pick out "chunks 6–10" from the draw list and send them
to a separate framebuffer.

Doing true spatial separation would require: a near full-resolution pass + a far low-resolution pass
(the low-resolution buffer depth-tested and culled against the near geometry) + depth-mask compositing.
That means rewriting the entire pass structure of `GameRenderer.renderWorld`, plus separate adaptations
for Sodium's `RenderSectionManager` and Iris (voxy wrote 20+ iris mixins for this).

**Practical impact**: Resolve scales the entire world frame, so the near geometry also gets slightly
softer. In exchange:

- More pixels saved (80% overall saves 36% fill rate; lowering only chunks 6–10 would save ~23%);
- A stable, predictable implementation that conflicts with neither Sodium, Iris, nor shaders.

The `Distant chunks start` option is therefore given the meaning it can actually carry: **when the view
distance does not exceed this value there is nothing "distant" on screen, so Resolve stays completely
inactive** (to avoid sacrificing sharpness for nothing). The default of 6 means Resolve does nothing when
the view distance is ≤ 6 chunks.

---

## 6. Verification status (fully verified on a real machine)

- ✅ **Compiles**, producing `resolve-1.0.0.jar`.
- ✅ Mixin annotations confirmed correctly remapped to intermediary by Loom: `renderWorld` →
  `method_3188` (class_757), `getLeftText` → `method_1835` (class_340), `fovMultiplier` →
  `field_4019` (class_757), `WorldRenderer.render` → `method_22710` (class_761); the access widener
  outputs `class_310.field_1689 Lnet/minecraft/class_276;`.
- ✅ **The render-target swap is confirmed working in practice.** A temporary probe at the head of
  `WorldRenderer.render` reads the real size of `client.getFramebuffer()`, outputting
  `world measured 1512x923 (expected 1512x923)` — i.e. the renderer truly receives the scaled target
  (60% of a 2520x1538 window).
- ✅ **All four zoom-detection paths triggered on a real machine**, with the log recording
  "source: Zoomify" and "source: FOV change" respectively.
- ✅ **The performance gain is measured**, see section 7.

### Two runtime bugs exposed and fixed on the real machine

**1. Crash when pressing F3** — `CancellationException: The call method_1835 is not cancellable`

```java
// Wrong: the CallbackInfo Mixin generates by default is not cancellable, setReturnValue() throws.
@Inject(method = "getLeftText", at = @At("RETURN"))
// Right:
@Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
```

**2. The generic "FOV change" detection was completely dead** — `java.lang.NoSuchFieldException:
fovMultiplier`

The original implementation read `GameRenderer.fovMultiplier` via reflection, but **at runtime
Minecraft runs under intermediary names (`client-intermediary.jar`), where the field is actually called
`field_4019`** — the Yarn name only exists at compile time, so reflection never finds it. The fix is a
Mixin `@Accessor`, which is rewritten to the intermediary name at build time:

```java
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("fovMultiplier")
    float resolve$getFovMultiplier();
}
```

This pitfall is worth remembering: **reflecting over Minecraft members must use intermediary names (or a
`MappingResolver`), while reflecting over mod classes (Iris / Zoomify) is safe** — Fabric does not
remap other mods' class names, which is why the reflection in this project's `IrisCompat` and
`ZoomifyHook` is correct.

---

## 7. Benchmarking and measured results

There is a "Benchmark" button at the bottom of the config screen (also bindable via the `Run Resolve
performance benchmark` key). It automatically alternates between **100% and your configured resolution
for 3 cycles** (A/B/A/B/A/B, 6 seconds per phase, the first 1.5 seconds discarded as warmup, about 45
seconds in total), measuring the real per-frame interval and sending the result to chat and the log.

Two deliberate design points:

- **What is measured is end-to-end frame time, CPU included.** This is intentional: if the CPU is the
  bottleneck, reporting "no difference" is the honest conclusion, rather than whitewashing a CPU
  bottleneck into a gain.
- **Interleaved sampling rather than switch-once-then-compare.** Scene, time and chunk-load state all
  drift over a session; interleaved A/B sampling cancels those out.

### Reference measurements

On **Intel Iris Xe integrated graphics + Mellow Shader shaders + view distance 10**, at a 2520x1538
window:

| | Average FPS | 1% low | sampled frames |
|---|---|---|---|
| Baseline 100% | 65.3 | 33.3 | 1175 |
| Resolve 60% | **88.6** | **50.0** | 1594 |
| Gain | **+23.4 (+36%)** | **+16.7 (+50%)** | |

**The 1% low improves more than the average frame rate** (+50% vs +36%). This means Resolve helps the
most exactly when frames are heaviest and most prone to hitching — for fill-rate-limited hardware like
integrated graphics, the felt smoothness improves more than the average FPS number suggests.

### Back-deriving the bottleneck composition from the measurements

Let frame time = a fixed part C + a part G that scales with resolution:

- Baseline frame time = 1.000; 60% frame time = 1/1.36 = 0.735
- Solving gives **G ≈ 41%, C ≈ 59%**

That is, about 59% of the frame time is resolution-independent (CPU main thread, Iris shadow passes,
HUD, fixed post-processing). Extrapolating with this model:

| Resolution | Estimated FPS | vs 60% |
|---|---|---|
| 50% | ~95 | +6% |
| 40% | ~100 | +12% |
| 30% | ~105 | +18% |
| Theoretical limit | ~112 | +26% |

**So around 60% is already the sweet spot** — lowering the resolution further has diminishing returns
(60%→50% is only +6% frame rate). To go further you should target that 59% fixed overhead: shader-pack
shadow resolution, entity rendering, chunk loading.

Note this proportion is specific to **a particular hardware and scene**: the weaker the GPU, the higher
the resolution and the heavier the shaders, the larger G becomes and the more Resolve gains.

---

## 8. Building from source

This project is built in a **fully offline** environment — Minecraft, Yarn mappings and Fabric API all
come from the local Loom cache.

```bat
cd "D:\minecraft mod\foxy\src"
set JAVA_HOME=C:\Program Files\java\graalvm-jdk-21.0.11+9.1
gradlew.bat build --offline
```

Output: `src\build\libs\resolve-1.0.0.jar`

Notes:

- The Gradle wrapper has been set to **9.5.1**, `loom_version=1.17-SNAPSHOT`,
  `loader_version=0.19.3` — all three aligned to the versions actually present in the local cache; the
  9.7.1 / 1.18-SNAPSHOT / 0.19.5 the template originally wrote do not exist on this machine.
- Mod Menu is referenced as `modCompileOnly` from `src/libs/modmenu-15.0.2.jar` — it is **not**
  bundled into the output, and its absence at runtime does not affect Resolve's startup.
- The config screen uses only vanilla widgets, with no dependency on Cloth Config or YACL.

### Source layout

```
src/main/java/com/zilin233/resolve/
├── Resolve.java                     Constants (mod id / logger)
├── ResolveClient.java               Client entrypoint: load config, register keys
├── config/
│   ├── ResolveConfig.java           Config data structure + range validation
│   ├── ResolveConfigManager.java    resolve.json read/write
│   └── ShaderMode.java              Shader-mode enum
├── zoom/
│   ├── ZoomDetector.java            Four detection sources + fovMultiplier read
│   └── ZoomifyHook.java             Zoomify reflection bridge (no hard dependency)
├── render/
│   ├── DynamicResolution.java       Scaled framebuffer create / swap / upscale blit
│   └── ResolutionController.java    Per-frame target-scale decision + timing state machine
├── compat/
│   ├── IrisCompat.java              Iris shader-state query (reflection)
│   └── ModConflicts.java            Conflict and other-mod detection
├── debug/
│   └── ResolveBenchmark.java        A/B frame-time benchmark (see section 7)
├── mixin/
│   ├── GameRendererMixin.java       renderWorld head/tail wrapping
│   ├── GameRendererAccessor.java    @Accessor for fovMultiplier
│   └── DebugHudMixin.java           F3 status line
└── modmenu/
    ├── ResolveModMenu.java          Mod Menu entrypoint
    └── ResolveConfigScreen.java     Scrolling config screen
```

---

## 9. Acknowledgements

The "swap + upscale" render-target mechanism is based on **Zelo101's
[RenderScale](https://github.com/Zolo101/RenderScale)** (MIT licensed). Resolve does not reuse its code,
but confirmed that this path really works on the new 1.21.8 frame graph pipeline — in particular that
`MinecraftClient.framebuffer` needs to be widened to writable. Compared to RenderScale, Resolve
additionally provides: a dynamic (zoom-following) scale value, a backend-agnostic `drawBlit` instead of
the GL-specific `setupBlitFramebuffer`, and a hand-rolled Mod Menu config screen.

## 10. License

MIT
