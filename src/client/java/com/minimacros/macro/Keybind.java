package com.minimacros.macro;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Helpers for keybinds that can carry modifiers, like "Left Shift + L". */
public final class Keybind {
	/**
	 * Left and right variants are tracked separately so a binding can say
	 * "Left Shift" specifically, the way the user would read it back.
	 */
	public static final int[] MODIFIER_KEYS = {
			GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
			GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
			GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
			GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER
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
	public static List<Integer> heldModifiers(Window window) {
		List<Integer> held = new ArrayList<>();
		for (int modifier : MODIFIER_KEYS) {
			if (InputConstants.isKeyDown(window, modifier)) {
				held.add(modifier);
			}
		}
		return held;
	}

	/**
	 * True when exactly this combination is down — every required modifier held
	 * and no others. Without the "no others" half, a bare "L" binding would also
	 * fire while the player holds shift to sprint.
	 */
	public static boolean isCombinationDown(Window window, int keyCode, List<Integer> modifiers) {
		if (keyCode == InputConstants.UNKNOWN.getValue()
				|| !InputConstants.isKeyDown(window, keyCode)) {
			return false;
		}
		for (int modifier : MODIFIER_KEYS) {
			if (modifier == keyCode) {
				continue;
			}
			boolean required = modifiers.contains(modifier);
			if (required != InputConstants.isKeyDown(window, modifier)) {
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
		return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
	}
}
