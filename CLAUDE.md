# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ATPCore is a shared Spigot **library plugin**. Game plugins (Craft of Clans at `../clash2`, future infinite-parkour game) depend on it both at compile time (Maven `provided` dependency) and at runtime (`depend: [ATPCore]` in their plugin.yml). It ships as its own jar in the server's `plugins/` folder — nothing from here gets shaded into consumers.

## Build

```bash
mvn clean install
```

`install` (not just `package`) matters: consumers resolve `aaronpost:atpcore:1.0.0` from the local Maven repo (`~/.m2`). After changing ATPCore, rebuild it, then rebuild the consumer, and deploy **both** jars to the server (clash2's `scripts/upload.sh` handles both).

If no `mvn` is on PATH, IntelliJ's bundled Maven works:
`"/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn"`

## Architecture

Package root: `aaronpost.atpcore`

- **`ATPCore`** — plugin main. Statics: `ATPCore.plugin`, `log/logWarning/logError`, `isShuttingDown()`. `onEnable` registers the schematic wand `Controller` listener.
- **`schematics/`** — block-snapshot schematic system (not WorldEdit).
  - `Schematic` — capture (two corner blocks), cache (`buildCache()`), paste (instant/batched/rotated-at-pivot/layer-by-layer construction), grassland reset, sign text, block-like entities (armor stands, item frames, paintings; entities tagged `NPC` metadata are never removed). Persisted as pretty-printed Gson JSON keyed by field names — renaming fields breaks saved schematic files.
  - `Schematics.s` — singleton registry of loaded schematics + dirty tracking (`markUpdated`/`getUpdatedSchematics`). Also holds the two game hooks:
    - `setBannerResolver(BannerResolver)` — banner material swap + decoration during paste (CoC: clan banners). Null (default) pastes banners unchanged.
    - `setPasteConfig(PasteConfig)` — batch size / tick delay for batched pastes; default 200 blocks / 2 ticks.
  - `Controller` — wand listener (registered by ATPCore). Owns the wand display-name constants (`SCHEMATIC_WAND_NAME`, `EVENT_BLOCK_EDITOR_NAME`, `SAVE_COORDINATES_WAND_NAME`). Game plugins hand out the wand items; shift-crouch with the schematic wand opens `AdminSchematicMenu`.
  - `AdminCoordinateMenu` — paged teleport menu over `Registry.LocationData`; brand via `setMessagePrefix` / `setSaveCommandHint`.
  - `LocationWrapper` / `LocationWrapper2` (named + yaw/pitch, is an `IDataContainer`) / `Coordinates`.
- **`registries/`** — data registry framework.
  - `DataRegistry<T extends IDataContainer>` — validated `LinkedHashMap` keyed by `getName()`; `get()` throws `IllegalStateException` for missing keys until `setInitialized(true)`. `getStatAtLevel()` clamps per-level stat arrays.
  - `Registry` (base) — static registry-of-registries map + `register()`, `clear()`, `setAllInitialized()`. Game plugins **extend** this class, declare their `DataRegistry` fields, and expose an explicit `registerAll()` called from `onEnable` (a static block is not reliable: accessing the inherited `registries` field doesn't initialize the subclass). Also declares `LocationData` (named coordinates, "Locations.json") — the host plugin calls `initLocationData(subPath)` before registering (create-once, reload-safe).
- **`persistence/`** — `CoreSerializer.serializeSchematics(dir)` / `deserializeSchematics(dir)` / `serializeLocations(dir)` / `deserializeList(registry)`; `DeserializerAdapter` (polymorphic {type, properties} Gson envelope with configurable package prefix); `LocalDateTypeAdapter`. Paths are passed in so each plugin keeps data in its own folder.
- **`gui/`** — the inventory-GUI framework for every plugin on the server. There is exactly one menu system; do not add a second.
  - `InventoryGUI` — abstract base. Slot→`InventoryButton` map, all clicks cancelled by default. Default `createInventory()` builds a plain chest sized from the `sizeHint` constructor arg (rounded to rows, 9..54); override only for a custom `InventoryHolder`. Helpers: `setButton(slot, icon, Consumer<Player>)`, `refreshEverySecond()`/`refreshEvery(ticks)`/`refreshRequest()` (self-cancelling on close — an override of `onClose` **must** call super or the task leaks). Optional async pre-load via `isAsyncLoad()`/`loadAsync()`/`applyAsyncData()`.
  - `CompactGUI` — `sizeHint` is a slot *count* rather than a size; background pre-filled.
  - `menu/ConfirmJsonGUI` — json-backed yes/no dialog, the way to write an "are you sure?": `ConfirmJsonGUI.of("confirm_x").yes(p -> ...).no(p -> ...).open(player)`. Wording lives in a menu json; the reserved ids `yes`/`no` carry defaults (LIME_WOOL/RED_WOOL, "Confirm"/"Cancel") so the file only needs slots, and `setDefaults(...)` rebrands them per game. Extra buttons bind via `choice(id, action)`; a `no` that only closes needs no Java at all (`"action": "close"` in json). Both bindings close the inventory before running. Replaced the old fluent `ConfirmGUI`, which is gone.
  - `AbstractPagedMenu<T>` — paginated menu built on `InventoryGUI`. **Constructing one opens it**, which decorates it from inside the constructor — before subclass fields assigned after `super(...)` exist. That first render is therefore best-effort (exceptions swallowed) and a second `decorate` runs on the next tick, so a subclass reading its own fields during render still lands correct. A subclass that wants the right content in the same tick can call `renderPage(1)` at the end of its constructor. Nav arrows at slot `itemsPerPage` and the last slot, `renderFooter()`/`onFooterClick()` in between; async entries via `loadAsyncEntries`/`applyAsyncEntries`.
  - `GUIManager` — routes inventory events by backing `Inventory`. `ATPCore.guiManager` is the single instance and `onEnable` registers the only `GUIListener`; consumers must not register their own.
  - `GUIUtil` — filler panes (incl. seeded material scatter), arrow-head texture URLs, `item()`, `attach*`, `{token}` substitution, `prettyIndent`, `toSlotIndex`, `drawLineIntoArray`, `makeRainbow`. `attachLore`/`attachNameAndLore` prefix `DEFAULT_LORE_COLOR` (gray) onto any lore line that doesn't set its own colour, so lore never renders as vanilla purple italic — every lore path (code items, `ItemTemplate`, json menus) goes through them.
  - `ItemTemplate` — material + tokenized name/lore; `build(tokenKv...)`. The payload type for json-driven menus. The `ItemStack` constructor templates over a prepared stack (textured head, coloured banner) and clones it per build.
  - `menu/` — json-driven menus. **The rule: json owns layout, Java owns behaviour.** `MenuData`/`MenuItemData` are the file format (slot, material, name, lore, amount, `skullUrl`, `loreIndent`, plus an `id` that code binds to). `AbstractJsonMenu<C>` is the base a host plugin subclasses once to pin its context type; per-menu classes then bind behaviour in `bindMenuData()` via `on(id, …)` / `showIf(id, …)` / `token(k, v)`. Resolve menus by wiring `setMenuSource(…)` in `onEnable`.
    - The **only** actions expressible in json are `close` and `message:<text>`; `MenuItemData.validate()` rejects anything else so the format cannot regrow into a scripting language.
    - Omitting `material` on an item that has an `id` reserves the slot for code to paint in `decorateDynamic`, which runs **after** the json items so it always wins.
    - The `(menuKey, defaultTitle, String[] titleTokens)` constructor lets a family of menus share a title convention and omit `title` from their json.
  - `OfflineSkull` (textured player heads), `IDisplayable`, `ClickQuantityAdjuster` (±1/±5 click math).
- **`books/`** — `BookData` (written-book data container with `&`-color and `<a>link</a>` parsing).
- **`util/`** — `Pair`.

## Conventions

- No big block comments that over-explain. A short line where the reasoning isn't obvious, nothing where it is. One line is the default, javadoc included — if the name says it, say nothing; if there's a non-obvious reason, that reason is the whole comment.
- Never write a fully-qualified class name inline where an import would do — `PlayerTeleportEvent.TeleportCause.PLUGIN`, not `org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN`. Check the imports first; the import is often already there. Only a real name collision justifies qualifying, and then only the colliding use.
- Statics are shared across all plugins on the server (consumers' classloaders delegate here). One `Schematics.s` pool serves every plugin — schematic names must be globally unique on a server.
- Never introduce dependencies on game plugins; game-specific behavior enters only through hooks (`BannerResolver`, `PasteConfig`).
- Gson-persisted classes: field names are the file format. Don't rename fields without a migration.
- Version bumps: update `pom.xml` version, consumers' dependency version, and `clash2/scripts/upload.sh` jar name together.
