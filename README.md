# Mini Macros

Build client-side macros out of drag-and-drop blocks, Scratch style, and bind
them to a key.

A macro is a set of block stacks laid out on a canvas. Stacks standing side by
side all run at the same time, so one can drive movement while another watches
for something and echoes into chat.

## Using it

Open the editor from **Mods → Mini Macros → Config**, or bind a key to
*Open Mini Macros* under **Options → Controls**.

- Drag blocks from the store on the right onto the canvas.
- Blocks snap to the top or bottom of a stack; dropped in open space they start
  a stack of their own.
- Drop reporters (ovals) and conditions (hexagons) into the matching holes.
- Right-click a block to duplicate it and everything below it.
- Drag a block onto the store to throw it away.
- Give the macro a name and a key, then **Save**. **Done** closes the editor,
  keeping which macros are ticked on.

**Right Shift** stops every running macro. It is bound by default and can be
changed under **Options → Controls**.

Macros are stored in `config/mini-macros/macros.json`, one file for all of them.
A single macro can be copied to and from the clipboard with Ctrl+C / Ctrl+V
while the editor has nothing else focused.

## Building

```
./gradlew build
```

The mod jar lands in `build/libs/`. `./gradlew runClient` starts a development
client with the mod loaded.

Requires Java 25 and Minecraft 26.2 with Fabric Loader and Fabric API. Mod Menu
is optional but is how the editor is normally reached.

## License

Copyright (C) 2026 Ball534.

Mini Macros is free software: you may redistribute it and/or modify it under
the terms of version 3 of the GNU General Public License, as published by the
Free Software Foundation. Any redistributed copy — modified or not — must come
with its source under the same terms. See `LICENSE` for the full text.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE.
