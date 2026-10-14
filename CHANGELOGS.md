# Changelog

All notable changes to Mini Macros are recorded here. Versions follow
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Changed

- Restyled only the block library, blocks, and canvas with muted material colours,
  pixel bevels, recessed slots, and a subtle grid. The rest of the editor retains
  its original design, and all text uses Minecraft's font.
- Added library search and visible category filters. Cached library layouts and
  skipped drawing offscreen blocks instead of rebuilding them every frame.
- Removed unused category-menu constants and execution-context accessors.
- Reused keybind tracking collections and replaced an unnecessary concurrent
  collection on the client thread.

### Fixed

- Repaired null entries in saved macros without shifting if/else branches or
  breaking legacy migrations.
- Releasing macro inputs no longer clears keys held by other macros or the player;
  releasing a key after opening a screen also cleans up its previous world hold.
- Sleep resumes exactly the requested number of ticks later. Stopped macros no
  longer apply pending camera turns, and nonfinite turn angles are ignored.
- Macros stop on disconnect, shutdown, world/player changes, focus loss, pause,
  death, or opening the editor. Execution errors stop the affected macro.
- Panic no longer allows immediate retriggering, and closing a screen with a
  key still held no longer starts a macro unexpectedly.

### Validation

- Added dependency-free regression checks for saved data and macro execution,
  included in the Gradle build.

## 1.1.0

### Added

- **Player state blocks.** Seven reporters under **Variables**, each of which
  plugs into any round slot: `health`, `foodLevel`, `saturationLevel`,
  `selectedItemName`, `selectedItemQty`, `selectedItemDurability` and
  `selectedItemSlot`. A script can now read the player it is driving — enough
  to decide whether to eat, to swap tools, or to stop.
  - `selectedItemSlot` counts 1-9 as the hotbar keys do, not 0-8 as the
    inventory holds it, so the number it reports is the one `buttonDown` takes
    to select that slot.
  - `selectedItemDurability` reports uses *left*, so `durability < 20` reads as
    the warning it looks like. Anything that cannot wear out reports `None`
    rather than `0`, which keeps a stack of dirt from looking like a tool about
    to break.
  - All seven report `None` when there is no player — the editor opens from the
    title screen — rather than a zero a script would take for a real reading.
    An empty hand is a reading in its own right, so it gives empty text and a
    count of zero.

### Changed

- **Reporters and value slots are properly round.** Their ends are a circular
  arc now rather than a straight three-pixel taper, which makes each end a
  half-circle and the block a pill. A taper steep enough to read as round comes
  to a point, and a point is what a hexagon uses to say it is a condition.
- **The hover highlight is solid white**, where it was a 40% wash that read as
  grey.
- **The Save button is green**, alongside the red Delete.
- **A dragged block snaps from the cursor**, not from the dragged stack's own
  top-left corner. Grabbing a wide block by its right-hand end put that corner
  most of a block away from the hand holding it, so a join would light up while
  the cursor was still nowhere near it. A stack that snaps onto nothing is
  still set down where its ghost was drawn.
- **Editor buttons no longer carry tooltips.**

### Fixed

- **A keybind no longer stops working while sprinting.** Matching demanded that
  no modifier beyond the ones a binding named be held, so a macro bound to `R`
  went dead the moment ctrl was down to sprint, shift to sneak, or alt for
  anything else. A binding now requires only the modifiers it names and ignores
  the rest. The trade is that `R` and `Ctrl + R` on two macros both fire on
  ctrl+R — a binding that wants ctrl to mean something different has to say so
  on both sides.
- **Clicking overlapping stacks picks the one on top.** Hit testing walked the
  stacks front to back and took the first match, but stacks are *drawn* in that
  same order, so the first match is the one lying underneath. Since a dropped or
  duplicated stack is added to the end, the block just placed on top was
  reliably the one that could not be grabbed.

### Removed

- The unreachable dropdown parameter machinery: the `DROPDOWN` param kind and
  its popup, arrow and hit-testing, left behind when the operator dropdown
  became one block per operator and mouse buttons became key names. Saved
  macros using the old blocks still migrate on load.
- Unused accessors: `MacroRunner.running()`, `MacroExecution.requestStop()`,
  `ScriptStack.moveTo()`, `MacroDefinition.setStacks()`,
  `BlockInstance.branches()`, `MacroListWidget.selected()`,
  `ParamSpec.number()` and `Panels.popup()`.

## 1.0.0

First release. Drag-and-drop block macros bound to a key: input, control flow,
messages, variables, operators and comparators, with stacks standing side by
side on one canvas all running at the same time.
