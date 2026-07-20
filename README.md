<img width="1024" height="285" alt="atpcore" src="https://github.com/user-attachments/assets/ce3fbbb7-ede1-47a6-9469-6f5444fe139b" />

**ATPCore** is a library of shared utilities and frameworks I use to build Minecraft plugins. It ships as its own Spigot plugin — game plugins declare `depend: [ATPCore]`, compile against it as a `provided` Maven dependency, and build on top of its systems instead of reinventing them.

It currently powers <a href="https://www.youtube.com/watch?v=nL1OREsZn2o">Craft of Clans</a>, and I plan to use it for more projects. There may be some useful utilities for other folks in this repo as well.

---

## Highlights

- **Full-featured schematic system** — capture regions in-world with a wand, cache them, and paste them instantly, in batches, rotated around a pivot, or layer-by-layer for construction animations. Handles signs, beds, doors, attached blocks, banners, and block-like entities (armor stands, item frames, paintings).
- **Data registry system** — a framework I'm quite proud of that makes it easy to deserialize data from JSON and bind containers to objects at runtime. Registries validate on load, catch missing keys early during startup, and preserve a deliberate load order.
- **GUI utilities** — a paginated inventory menu that's easy to build on top of (self-registering listeners, optional async loading), plus handlers and helpers for skulls, filler panes, and click-quantity math.
- **Moderation tools** — a mute system with pluggable persistence and a leetspeak-normalizing content filter.
- **Hook-driven design** — ATPCore never depends on a game plugin. Game-specific behavior (banner theming, paste batching config, mute storage, chat branding) enters through small interfaces the host plugin installs at startup.

---

## Project Structure

All code lives under `aaronpost.atpcore`:

```
aaronpost/atpcore
├── schematics/       → schematic capture, caching, and pasting
├── registries/       → JSON data registry framework
├── persistence/      → Gson serialization helpers
├── gui/              → inventory menu building blocks
├── moderation/       → mutes + content filtering
├── books/            → written-book data containers
└── util/             → small general-purpose helpers
```

### `schematics/`

A custom block-snapshot system akin to WorldEdit.

| Class | Purpose |
|---|---|
| `Schematic` | Capture a region between two corner blocks, build an in-memory block cache, and paste it back: instantly, batched over ticks, rotated at a pivot point, or bottom-up with scaffolding for construction animations. Also supports named **event blocks** (e.g. a `pivotPoint` or a spawn marker inside the schematic) you can query in world space after pasting. |
| `Schematics` | Singleton registry of loaded schematics with dirty-tracking, so only created/modified schematics get rewritten to disk. Also holds the game hooks: `setBannerResolver(...)` and `setPasteConfig(...)`. |
| `Controller` | Listener for the schematic wand, coordinate wand, and event-block editor. Registered by ATPCore itself — game plugins just hand out the wand items. |
| `AdminSchematicMenu` | Paged admin menu: paste any schematic where you stand, adjust its Y-offset with shift-clicks. Opened by shift-crouching with the schematic wand. |
| `AdminCoordinateMenu` | Paged teleport menu over the shared coordinate registry. Brand it with your plugin's chat prefix and save-command hint. |
| `LocationWrapper` / `LocationWrapper2` | Serialization-safe location holders (world stored by name; `LocationWrapper2` adds a name + yaw/pitch and plugs into the registry system). |
| `BannerResolver` / `PasteConfig` | The hook interfaces. Craft of Clans, for example, swaps pasted banners to the owner's clan colors and patterns, and feeds batch sizes from its live config. |

Schematics persist as pretty-printed JSON keyed by field names — saved files survive refactors as long as the fields do.

### `registries/`

The data registry system: define a container class, drop a JSON list file in your plugin folder, and get validated, queryable data at runtime.

| Class | Purpose |
|---|---|
| `IDataContainer` | The contract: `getName()` (registry key) + `validate()` (called on load; invalid entries are skipped with a warning, never crash the load). |
| `DataRegistry<T>` | An ordered, validated map of containers keyed by name. During startup, `get()` **throws** on missing keys so misconfigured data is caught immediately; after initialization it returns null instead. Includes `getStatAtLevel(...)` for per-level stat arrays that clamp to their last element. |
| `Registry` | The registry-of-registries base class. Game plugins **extend** it, declare their `DataRegistry` fields, and register them in an explicit `registerAll()` — registration order defines load order. Also owns the shared `LocationData` coordinate registry (`initLocationData(path)` decides where `Locations.json` lives). |

### `persistence/`

Gson helpers shared by every consumer:

- `CoreSerializer` — load/save schematics to a folder, write the coordinate registry, and `deserializeList(registry)` to hydrate any `DataRegistry` from its backing JSON file. All paths are passed in, so each plugin keeps data in its own folder.
- `DeserializerAdapter<T>` — polymorphic Gson adapter using a `{type, properties}` envelope; resolves concrete classes against a package prefix you supply. This is what lets a single JSON file hold many subclasses.
- `LocalDateTypeAdapter` — `LocalDate` ⇄ `"yyyy-MM-dd"`.

### `gui/`

Building blocks for inventory UIs:

- `AbstractPagedMenu<T>` — the paginated menu base. Give it a title and a list of entries; it handles pagination, navigation arrows, click cooldowns, per-instance event scoping via a custom `InventoryHolder`, listener cleanup on close, and an optional async pre-load hook for entries that come from a database.
- `OfflineSkull` — textured player heads from UUIDs, names, texture URLs, or base64 profile blobs.
- `GUIUtil` — filler panes, name/lore attachment, lore indenting, arrow-head textures.
- `IDisplayable` — "this thing can render itself as an item" contract used across menus.
- `ClickQuantityAdjuster` — reusable ±1/±5 click math with min/max clamping.

### `moderation/`

- `MuteManager` — in-memory mute cache with permanent/timed mutes and lazy expiry. Persistence is delegated to a `MuteStore` your plugin provides (Craft of Clans backs it with its SQL database); without one, mutes are memory-only.
- `MuteChatListener` — cancels chat from muted players and tells them how long is left.
- `ContentFilter` — server-local blocklist filter for names and chat. Normalizes input (lowercase, leetspeak mapping, non-letter stripping, repeated-letter collapsing, Unicode NFKD) to defeat the usual bypass tricks. Word lists live in a `content_filter.json` the server operator maintains — never in source control.

### `books/`

- `BookData` — a registry container for written books, with `&`-color-code parsing and `<a>https://...</a>` clickable links in page text.

### 🔧 `util/`

- `Pair<K, V>` — sometimes you just need a pair.

---

## Using ATPCore in a plugin

**1. Build & install locally:**

```bash
mvn clean install
```

**2. Depend on it in your `pom.xml`:**

```xml
<dependency>
    <groupId>aaronpost</groupId>
    <artifactId>atpcore</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

**3. Declare the runtime dependency in `plugin.yml`:**

```yaml
depend: [ATPCore]
```

**4. Wire it up in `onEnable`:**

```java
// Registries: extend aaronpost.atpcore.registries.Registry, then
Registry.registerAll();

// Schematic hooks
Schematics.s.setBannerResolver(new MyBannerResolver());
Schematics.s.setPasteConfig(new MyPasteConfig());

// Load schematics from your data folder
List<Schematic> schematics = CoreSerializer.deserializeSchematics(schematicsDir);
Schematics.s.addSchematic(schematics);
schematics.forEach(Schematic::buildCache);
```

Deploy the ATPCore jar to the server's `plugins/` folder alongside your plugin — nothing is shaded into consumers.

---

## Requirements

- Java 21
- Spigot/Paper 1.21+
- Maven

## Used by

- <a href="https://www.youtube.com/watch?v=nL1OREsZn2o">Craft of Clans</a> — a base-building and raiding game inspired by Clash of Clans
- **Infinite Parkour** *(in development)* — players build levels saved as schematics, pasted down endlessly as courses
