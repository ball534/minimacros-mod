package com.minimacros.runtime;

import com.minimacros.config.MacroStorage;
import com.minimacros.MiniMacrosClient;
import com.minimacros.gui.MacroEditorScreen;
import com.minimacros.macro.Keybind;
import com.minimacros.macro.MacroDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Watches for macro keybinds and steps whatever is running.
 *
 * <p>A running macro keeps going while a screen is open — that is what lets one
 * open chat and type into it. Only <em>starting</em> a macro is gated on being
 * in the world, so pressing a macro's key while typing does not fire it.
 */
public final class MacroRunner {
	/** Bounds the work performed by simultaneous macros each tick. */
	private static final int MAX_CONCURRENT = 16;

	private static final MacroRunner INSTANCE = new MacroRunner();

	private final List<MacroExecution> running = new ArrayList<>();
	private final Set<String> firingLastTick = new HashSet<>();
	private final Set<String> firingNow = new HashSet<>();
	private ClientLevel lastLevel;
	private LocalPlayer lastPlayer;

	private MacroRunner() {
	}

	public static MacroRunner get() {
		return INSTANCE;
	}

	public void onEndClientTick(Minecraft minecraft, boolean allowStarting) {
		if (minecraft.player == null || minecraft.level == null) {
			stopAll();
			firingLastTick.clear();
			lastLevel = null;
			lastPlayer = null;
			return;
		}
		boolean worldChanged = minecraft.level != lastLevel || minecraft.player != lastPlayer;
		lastLevel = minecraft.level;
		lastPlayer = minecraft.player;
		boolean canRun = minecraft.isWindowActive() && !minecraft.isPaused()
				&& minecraft.player.isAlive() && !(minecraft.gui.screen() instanceof MacroEditorScreen);
		if (worldChanged || !canRun) {
			stopAll();
		}

		// Running macros keep going while a screen is open — that is what lets a
		// macro open chat and echo into it. Starting one is still gated, so
		// typing a letter that happens to be a macro key does not fire it.
		// Track held combinations in screens as well, so closing a screen does
		// not turn a key still held from typing into a fresh macro press.
		dispatchKeybinds(allowStarting && canRun && !worldChanged && minecraft.gui.screen() == null);

		for (Iterator<MacroExecution> iterator = running.iterator(); iterator.hasNext(); ) {
			MacroExecution execution = iterator.next();
			try {
				execution.tick();
			} catch (RuntimeException exception) {
				execution.stop();
				MiniMacrosClient.LOGGER.error("Stopped macro '{}' after an execution error", execution.macro().name(), exception);
			}
			if (execution.finished()) {
				iterator.remove();
			}
		}
	}

	/**
	 * Fires on the rising edge of a macro's key combination — one press, one run.
	 * Tracked per macro rather than per key, so two macros can share a key with
	 * different modifiers.
	 */
	private void dispatchKeybinds(boolean allowStarting) {
		firingNow.clear();

		for (MacroDefinition macro : MacroStorage.macros()) {
			if (!macro.enabled() || !macro.hasKeybind()) {
				continue;
			}
			if (!Keybind.isCombinationDown(macro.keyCode(), macro.modifierKeys())) {
				continue;
			}
			firingNow.add(macro.id());
			if (allowStarting && !firingLastTick.contains(macro.id())) {
				start(macro);
			}
		}

		firingLastTick.clear();
		firingLastTick.addAll(firingNow);
	}

	/**
	 * Starts a macro. Pressing the key again while it is still running restarts
	 * it, which doubles as a way to interrupt a long script.
	 */
	public void start(MacroDefinition macro) {
		stop(macro);
		if (running.size() >= MAX_CONCURRENT) {
			return;
		}
		running.add(new MacroExecution(macro));
	}

	public void stop(MacroDefinition macro) {
		running.removeIf(execution -> {
			if (execution.macro() == macro) {
				execution.stop();
				return true;
			}
			return false;
		});
	}

	/** The panic key, and anything else that needs everything to stop now. */
	public void stopAll() {
		for (MacroExecution execution : running) {
			execution.stop();
		}
		running.clear();
	}
}
