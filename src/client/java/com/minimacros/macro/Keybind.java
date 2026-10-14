package com.minimacros.macro;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Helpers for keybinds that can carry modifiers, like "Left Shift + L". */
public final class Keybind {
	/**
	 * Left and right variants are tracked separately so a binding can say
	 * "Left Shift" specifically, the way the user would read it back.
	 */
	public static final int[] MODIFIER_KEYS = {
			InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT,
			InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL,
			InputConstants.KEY_LALT, InputConstants.KEY_RALT,
			InputConstants.KEY_LGUI, InputConstants.KEY_RGUI
	};

	private Keybind() {
	}

	public static boolean isModifier(int keyCode) {
		for (int modifier : MODIFIER_KEYS) {
			if (modifier == keyCode) {
				return true;
			}
		}
		return false;
	}

	/** The modifier keys physically held right now, for capturing a combination. */
	public static List<Integer> heldModifiers() {
		List<Integer> held = new ArrayList<>();
		for (int modifier : MODIFIER_KEYS) {
			if (InputConstants.isKeyDown(modifier)) {
				held.add(modifier);
			}
		}
		return held;
	}

	/**
	 * True when this binding's key is down along with every modifier it names.
	 *
	 * <p>Modifiers it does not name are ignored, which is what lets a plain "R"
	 * fire while ctrl is held to sprint, shift to sneak, or alt for anything else
	 * — those are keys a player is holding to play the game, not part of what they
	 * pressed. Demanding that no other modifier be down made a macro simply stop
	 * working while sprinting.
	 *
	 * <p>The trade is that "R" and "Ctrl + R" on two macros both fire on ctrl+R. A
	 * binding that wants ctrl to mean something different has to say so on both
	 * sides.
	 */
	public static boolean isCombinationDown(int keyCode, List<Integer> modifiers) {
		if (keyCode == InputConstants.UNKNOWN.getValue()
				|| !InputConstants.isKeyDown(keyCode)) {
			return false;
		}
		for (int modifier : modifiers) {
			if (modifier != keyCode && !InputConstants.isKeyDown(modifier)) {
				return false;
			}
		}
		return true;
	}

	public static Component displayName(int keyCode, List<Integer> modifiers) {
		if (keyCode == InputConstants.UNKNOWN.getValue()) {
			return Component.literal("Unbound");
		}
		StringBuilder text = new StringBuilder();
		for (int modifier : MODIFIER_KEYS) {
			if (modifiers.contains(modifier)) {
				text.append(keyName(modifier)).append(" + ");
			}
		}
		text.append(keyName(keyCode));
		return Component.literal(text.toString());
	}

	private static String keyName(int keyCode) {
		return InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
	}

	/** Converts the GLFW codes stored by the 26.2 mod into SDL scancodes. */
	public static int fromLegacyCode(int code) {
		if (code >= 65 && code <= 90) return InputConstants.KEY_A + code - 65;
		if (code >= 49 && code <= 57) return InputConstants.KEY_1 + code - 49;
		if (code >= 290 && code <= 301) return InputConstants.KEY_F1 + code - 290;
		if (code >= 302 && code <= 313) return InputConstants.KEY_F13 + code - 302;
		if (code >= 321 && code <= 329) return InputConstants.KEY_NUMPAD1 + code - 321;
		return switch (code) {
			case 32 -> InputConstants.KEY_SPACE;
			case 39 -> InputConstants.KEY_APOSTROPHE;
			case 44 -> InputConstants.KEY_COMMA;
			case 45 -> InputConstants.KEY_MINUS;
			case 46 -> InputConstants.KEY_PERIOD;
			case 47 -> InputConstants.KEY_SLASH;
			case 48 -> InputConstants.KEY_0;
			case 59 -> InputConstants.KEY_SEMICOLON;
			case 61 -> InputConstants.KEY_EQUALS;
			case 91 -> InputConstants.KEY_LBRACKET;
			case 92 -> InputConstants.KEY_BACKSLASH;
			case 93 -> InputConstants.KEY_RBRACKET;
			case 96 -> InputConstants.KEY_GRAVE;
			case 256 -> InputConstants.KEY_ESCAPE;
			case 257 -> InputConstants.KEY_RETURN;
			case 258 -> InputConstants.KEY_TAB;
			case 259 -> InputConstants.KEY_BACKSPACE;
			case 260 -> InputConstants.KEY_INSERT;
			case 261 -> InputConstants.KEY_DELETE;
			case 262 -> InputConstants.KEY_RIGHT;
			case 263 -> InputConstants.KEY_LEFT;
			case 264 -> InputConstants.KEY_DOWN;
			case 265 -> InputConstants.KEY_UP;
			case 266 -> InputConstants.KEY_PAGEUP;
			case 267 -> InputConstants.KEY_PAGEDOWN;
			case 268 -> InputConstants.KEY_HOME;
			case 269 -> InputConstants.KEY_END;
			case 280 -> InputConstants.KEY_CAPSLOCK;
			case 281 -> InputConstants.KEY_SCROLLLOCK;
			case 282 -> InputConstants.KEY_NUMLOCK;
			case 283 -> InputConstants.KEY_PRINTSCREEN;
			case 284 -> InputConstants.KEY_PAUSE;
			case 320 -> InputConstants.KEY_NUMPAD0;
			case 330 -> InputConstants.getKey("key.keyboard.keypad.decimal").getValue();
			case 331 -> InputConstants.getKey("key.keyboard.keypad.divide").getValue();
			case 332 -> InputConstants.KEY_MULTIPLY;
			case 333 -> InputConstants.getKey("key.keyboard.keypad.subtract").getValue();
			case 334 -> InputConstants.KEY_ADD;
			case 335 -> InputConstants.KEY_NUMPADENTER;
			case 336 -> InputConstants.KEY_NUMPADEQUALS;
			case 340 -> InputConstants.KEY_LSHIFT;
			case 341 -> InputConstants.KEY_LCONTROL;
			case 342 -> InputConstants.KEY_LALT;
			case 343 -> InputConstants.KEY_LGUI;
			case 344 -> InputConstants.KEY_RSHIFT;
			case 345 -> InputConstants.KEY_RCONTROL;
			case 346 -> InputConstants.KEY_RALT;
			case 347 -> InputConstants.KEY_RGUI;
			case 348 -> InputConstants.getKey("key.keyboard.menu").getValue();
			default -> InputConstants.UNKNOWN.getValue();
		};
	}
}
