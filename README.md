# Mini Macros

Build client-side programs out of drag-and-drop blocks, with events that decide
when each stack runs.

The mod loads only on the client. It adds an editor, keybindings, and a macro
runner; it does not register server features, replace game mechanics, or use
mixins. Running a macro deliberately performs its configured inputs, camera
turns, and messages through Minecraft's existing client systems. Normal
multiplayer actions performed by those inputs still reach the server as usual.

A macro is a set of block stacks laid out on a canvas. Start each stack with an
event block from **Events**. Stacks can run independently at the same time and
share variables within their macro while it is enabled.

## Using it

Open the editor from **Mods → Mini Macros → Config**, or bind a key to
*Open Mini Macros* under **Options → Controls**.

- Search or filter the block library on the right, then drag blocks onto the canvas.
- Pick a block category from the dropdown below the library search box.
- Scroll over the canvas to zoom in or out around the pointer (50%-200%). Drag
  empty canvas space to pan; scrolling over the library still scrolls its blocks.
- Blocks snap to the top or bottom of a stack; dropped in open space they start
  a stack of their own.
- Event blocks have a raised top and belong at the start of a stack. A stack
  without an event is inactive. The macro's checkbox enables its event listeners.
- Drag an event above an existing ordinary stack to attach it, or drop it in
  empty space to start a new stack. Events cannot go inside loops or branches.
- Drop value blocks and conditions (hexagons) into the matching holes.
- Right-click a block to duplicate it and everything below it.
- Drag a block onto the library to throw it away.
- Give the macro a name, then **Save**. **Done** closes the editor,
  keeping which macros are ticked on.

**Right Shift** stops every running macro. It is bound by default and can be
changed under **Options → Controls**.

Running macros also stop when the client loses focus, pauses, disconnects,
changes world or player, shuts down, or opens the editor, and when the player
dies. A key held while leaving a screen must be released and pressed again to
start a macro.

Macros are stored in `config/mini-macros/macros.json`, one file for all of them.
A single macro can be copied to and from the clipboard with Ctrl+C / Ctrl+V
while the editor has nothing else focused.

## Events and physical input

- **When [button] is pressed** starts a stack on a fresh physical press. Its
  dropdown selects **run once** (ignore extra presses while running), **restart**,
  or **toggle** (the next press stops the stack).
- **When [button] is released** starts on release.
- **While [button] is held** repeats the stack while held and stops on release.
- **When this macro is enabled** runs once when its listeners become active.
- **Every [5] seconds** starts a periodic stack, without overlapping its runs.

Click an event's button field, then press a key or mouse button. Hold modifiers
first to capture a combination such as Left Control + R. Input conditions and
input waits support combinations too; simulated buttonDown/buttonUp actions
continue to capture one button at a time.

Under **Input**, the hexagonal **button is held**, **button was pressed this
tick**, and **button was released this tick** conditions fit inside if/while and
wait-until blocks. They read physical input, so simulated macro inputs do not
trigger other stacks. New input events fire only during gameplay, not while
typing into a screen.

**Wait until [condition]** continues immediately if the condition is already
true. **Wait for next press of [button]** requires a new press after reaching the
block; if the button is already held, release it and press it again.

For example:

```text
When this macro is enabled
    print [Press Space to begin]
    Wait for next press of [Space]
    print [Started!]
```

Panic stops running stacks. Fresh manual input can start them again; automatic
enabled/periodic listeners require disabling and re-enabling the macro to rearm.
Existing saved keybinds are migrated into restart event blocks on their stacks.

## Typing text

The **type text** block (formerly **echo**) opens chat when no screen is open,
then types into its input. If a screen is already open, it types into that
screen's focused editable field instead. Text is left unsent; submitting it
requires an explicit Enter press. Existing echo blocks keep working with the
new label.

```text
When [R] is pressed [run once]
    type text [Hello!]
```

This opens chat with `Hello!` ready to send. There is no need to press the chat
key or insert a delay first. Literal spaces are preserved, and variables and
`${name}` interpolation still work. If the open screen does not accept text,
the block shows a local actionbar message asking you to check the destination.

## Building

```
./gradlew build
```

The mod jar lands in `build/libs/`. `./gradlew runClient` starts a development
client with the mod loaded.

Requires Java 25 and Minecraft 26.3 with Fabric Loader and Fabric API. Mod Menu
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
