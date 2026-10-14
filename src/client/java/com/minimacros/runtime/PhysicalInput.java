package com.minimacros.runtime;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A physical input snapshot shared by every event and predicate in one tick. */
public final class PhysicalInput {
	private Set<InputConstants.Key> previous = Set.of();
	private Set<InputConstants.Key> current = Set.of();
	private boolean active;
	private boolean edgesAllowed;
	private long tickNumber;
	private final Map<String, List<InputConstants.Key>> combinations = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<InputConstants.Key>> entry) {
			return size() > 256;
		}
	};

	/** Reads SDL state, never Minecraft's simulated key mappings. */
	public void capture(boolean gameplayInputActive) {
		Set<InputConstants.Key> down = new HashSet<>();
		ByteBuffer keyboard = SDLKeyboard.SDL_GetKeyboardState();
		if (keyboard != null) {
			for (int code = 1; code < keyboard.limit(); code++) {
				if (keyboard.get(code) != 0) {
					down.add(InputConstants.Type.KEYBOARD.getOrCreate(code));
				}
			}
		}
		int mouse = SDLMouse.SDL_GetMouseState(null, null);
		for (int button = 1; button <= Integer.SIZE; button++) {
			if ((mouse & (1 << (button - 1))) != 0) {
				down.add(InputConstants.Type.MOUSE.getOrCreate(button));
			}
		}
		update(down, gameplayInputActive);
	}

	/** Also provides the deterministic input boundary used by headless checks. */
	public void update(Set<InputConstants.Key> down, boolean gameplayInputActive) {
		previous = current;
		current = Set.copyOf(down);
		edgesAllowed = active && gameplayInputActive;
		active = gameplayInputActive;
		tickNumber++;
	}

	public long tickNumber() {
		return tickNumber;
	}

	public boolean active() {
		return active;
	}

	public boolean held(String combination) {
		return active && matches(combination, current);
	}

	public boolean pressed(String combination) {
		return edgesAllowed && matches(combination, current) && !matches(combination, previous);
	}

	public boolean released(String combination) {
		return edgesAllowed && matches(combination, previous) && !matches(combination, current);
	}

	private boolean matches(String combination, Set<InputConstants.Key> down) {
		String value = combination == null ? "" : combination.trim();
		List<InputConstants.Key> keys = combinations.computeIfAbsent(value, raw -> {
			List<InputConstants.Key> parsed = new ArrayList<>();
			for (String token : raw.split("\\+", -1)) {
				InputConstants.Key key = InputSimulator.resolveKey(token);
				if (key.equals(InputConstants.UNKNOWN)) {
					return List.of();
				}
				parsed.add(key);
			}
			return List.copyOf(parsed);
		});
		return !keys.isEmpty() && down.containsAll(keys);
	}
}
