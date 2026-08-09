package com.minimacros.runtime;

import com.minimacros.MiniMacrosClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers input through Minecraft's own systems. Nothing here touches the
 * operating system, so a macro can only ever affect Minecraft.
 *
 * <p>In the world, presses go through {@link KeyMapping}. With a screen open
 * they are dispatched to that screen instead, because key mappings are ignored
 * while a screen has focus — that is what lets a macro open chat and type.
 *
 * <p>Every key this class presses is tracked so a macro that stops early — or
 * is killed by the panic key — never leaves one stuck down. It deliberately
 * avoids the global {@link KeyMapping#releaseAll()}, which would also clear
 * keys the player is genuinely holding.
 */
public final class InputSimulator {
	private final Set<InputConstants.Key> heldKeys = new HashSet<>();

	/**
	 * Common spellings for keys whose Minecraft names are awkward to guess —
	 * vanilla calls left shift "left.shift", which nobody types by accident.
	 */
	private static final Map<String, String> KEY_ALIASES = Map.ofEntries(
			Map.entry("shift", "left.shift"),
			Map.entry("lshift", "left.shift"),
			Map.entry("leftshift", "left.shift"),
			Map.entry("rshift", "right.shift"),
			Map.entry("rightshift", "right.shift"),
			Map.entry("ctrl", "left.control"),
			Map.entry("control", "left.control"),
			Map.entry("lctrl", "left.control"),
			Map.entry("leftctrl", "left.control"),
			Map.entry("leftcontrol", "left.control"),
			Map.entry("rctrl", "right.control"),
			Map.entry("rightctrl", "right.control"),
			Map.entry("rightcontrol", "right.control"),
			Map.entry("alt", "left.alt"),
			Map.entry("lalt", "left.alt"),
			Map.entry("leftalt", "left.alt"),
			Map.entry("ralt", "right.alt"),
			Map.entry("rightalt", "right.alt"),
			Map.entry("esc", "escape"),
			Map.entry("return", "enter"),
			Map.entry("win", "left.win"),
			Map.entry("super", "left.win"),
			Map.entry("cmd", "left.win"),
			Map.entry("pageup", "page.up"),
			Map.entry("pgup", "page.up"),
			Map.entry("pagedown", "page.down"),
			Map.entry("pgdn", "page.down"),
			Map.entry("capslock", "caps.lock"),
			Map.entry("numlock", "num.lock"),
			Map.entry("scrolllock", "scroll.lock"),
			Map.entry("printscreen", "print.screen"),
			// Mouse buttons are keys too, as far as Minecraft is concerned. They
			// must be spelled "mouse.left" and not "left", which is the arrow key.
			Map.entry("mouse1", "mouse.left"),
			Map.entry("lmb", "mouse.left"),
			Map.entry("leftclick", "mouse.left"),
			Map.entry("leftmouse", "mouse.left"),
			Map.entry("mouse2", "mouse.right"),
			Map.entry("rmb", "mouse.right"),
			Map.entry("rightclick", "mouse.right"),
			Map.entry("rightmouse", "mouse.right"),
			Map.entry("mouse3", "mouse.middle"),
			Map.entry("mmb", "mouse.middle"),
			Map.entry("middleclick", "mouse.middle"),
			Map.entry("middlemouse", "mouse.middle"));

	/** Key names already reported as unresolvable, so the log is warned once each. */
	private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

	/**
	 * Resolves a macro's button argument. Single characters map to that letter or
	 * digit; longer values are names, so "space", "enter", "shift", "esc" and
	 * "f3" all work, as does Minecraft's own spelling like "left.shift".
	 *
	 * <p>Mouse buttons resolve here too, spelled "mouse.left" the way
	 * {@link InputConstants.Type#MOUSE} names them, which is what lets one pair
	 * of blocks cover the keyboard and the mouse.
	 */
	public static InputConstants.Key resolveKey(String value) {
		String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (raw.isEmpty()) {
			return InputConstants.UNKNOWN;
		}

		String stripped = raw.replace(" ", "").replace("_", "").replace("-", "");
		String name = KEY_ALIASES.getOrDefault(stripped, raw);

		InputConstants.Key key;
		try {
			key = InputConstants.getKey(name.startsWith("mouse.")
					? "key." + name
					: "key.keyboard." + name);
		} catch (RuntimeException e) {
			key = InputConstants.UNKNOWN;
		}

		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			if (WARNED_KEYS.add(raw)) {
				MiniMacrosClient.LOGGER.warn("Macro used an unknown key name '{}' — nothing was pressed", raw);
			}
			return InputConstants.UNKNOWN;
		}
		return key;
	}

	// --- in-world input -------------------------------------------------

	/**
	 * Holds a key down until {@link #keyUp}. {@link KeyMapping#click} is what
	 * makes {@code consumeClick()} fire, which is how vanilla registers one
	 * discrete press; the hold alongside it covers continuous actions.
	 */
	public void keyDown(String value) {
		InputConstants.Key key = resolveKey(value);
		if (key.equals(InputConstants.UNKNOWN)) {
			return;
		}
		KeyMapping.set(key, true);
		KeyMapping.click(key);
		heldKeys.add(key);
	}

	public void keyUp(String value) {
		InputConstants.Key key = resolveKey(value);
		if (key.equals(InputConstants.UNKNOWN)) {
			return;
		}
		KeyMapping.set(key, false);
		heldKeys.remove(key);
	}

	// --- screen input ---------------------------------------------------

	/**
	 * Sends a key press to an open screen. A mouse button is not a key event, so
	 * it falls back to the in-world path rather than being delivered as one.
	 */
	public void keyDownOnScreen(Screen screen, String value) {
		InputConstants.Key key = resolveKey(value);
		if (key.equals(InputConstants.UNKNOWN)) {
			return;
		}
		if (key.getType() == InputConstants.Type.MOUSE) {
			keyDown(value);
			return;
		}
		screen.keyPressed(new KeyEvent(key.getValue(), 0, 0));
	}

	public void keyUpOnScreen(Screen screen, String value) {
		InputConstants.Key key = resolveKey(value);
		if (key.equals(InputConstants.UNKNOWN)) {
			return;
		}
		if (key.getType() == InputConstants.Type.MOUSE) {
			keyUp(value);
			return;
		}
		screen.keyReleased(new KeyEvent(key.getValue(), 0, 0));
	}

	/** Types text into an open screen, one character at a time and in order. */
	public void typeIntoScreen(Screen screen, String text) {
		if (text == null) {
			return;
		}
		text.codePoints().forEach(codePoint -> screen.charTyped(new CharacterEvent(codePoint)));
	}

	// --- lifecycle ------------------------------------------------------

	/**
	 * Releases everything this simulator pressed, and nothing else. This is what
	 * stops a macro that ends between a keyDown and its keyUp from leaving the
	 * key stuck down.
	 */
	public void releaseAll() {
		for (InputConstants.Key key : heldKeys) {
			KeyMapping.set(key, false);
		}
		heldKeys.clear();
	}
}
