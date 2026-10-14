package com.minimacros.runtime;

import com.minimacros.MiniMacrosClient;
import com.minimacros.config.MacroStorage;
import com.minimacros.gui.MacroEditorScreen;
import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.MacroDefinition;
import com.minimacros.macro.ScriptStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Watches event hats and advances at most one execution per event stack. */
public final class MacroRunner {
	private static final int MAX_CONCURRENT = 16;
	private static final MacroRunner INSTANCE = new MacroRunner();

	private final Map<String, MacroState> listeners = new LinkedHashMap<>();
	private final PhysicalInput physicalInput = new PhysicalInput();
	private ClientLevel lastLevel;
	private LocalPlayer lastPlayer;

	MacroRunner() {
	}

	public static MacroRunner get() {
		return INSTANCE;
	}

	public void onEndClientTick(Minecraft minecraft, boolean allowStarting) {
		if (minecraft.player == null || minecraft.level == null) {
			reset();
			physicalInput.update(Set.of(), false);
			return;
		}
		boolean worldChanged = minecraft.level != lastLevel || minecraft.player != lastPlayer;
		if (worldChanged) {
			reset();
		}
		lastLevel = minecraft.level;
		lastPlayer = minecraft.player;
		boolean canRun = minecraft.isWindowActive() && !minecraft.isPaused()
				&& minecraft.player.isAlive() && !(minecraft.gui.screen() instanceof MacroEditorScreen);
		boolean allowEvents = canRun && !worldChanged && minecraft.gui.screen() == null;
		physicalInput.capture(allowEvents && allowStarting);
		tick(MacroStorage.macros(), physicalInput, canRun, allowEvents, !allowStarting);
	}

	/** Client-independent dispatch boundary: the input snapshot is already fixed. */
	void tick(List<MacroDefinition> macros, PhysicalInput input, boolean canRun, boolean allowEvents, boolean panic) {
		Set<String> present = new HashSet<>();
		for (MacroDefinition macro : macros) {
			present.add(macro.id());
			MacroState state = listeners.get(macro.id());
			if (state == null || state.macro != macro) {
				if (state != null) {
					state.stop();
				}
				state = new MacroState(macro);
				listeners.put(macro.id(), state);
			}
			if (!macro.enabled()) {
				state.disable();
				continue;
			}
			if (!state.enabled) {
				state.enabled = true;
			}
			synchronizeStacks(state);
			if (!canRun) {
				state.stop();
				continue;
			}
			if (panic) {
				state.stop();
				state.autoSuppressed = true;
				continue;
			}
			for (ScriptStack stack : macro.stacks()) {
				EventState event = state.events.get(stack);
				if (event == null) {
					continue;
				}
				BlockInstance hat = stack.blocks().getFirst();
				if (allowEvents && input.active()) {
					dispatch(state, event, hat, input);
				} else if (BlockRegistry.WHILE_HELD.equals(hat.typeId())) {
					event.heldActive = false;
					event.stop();
				}
				advance(state, event);
			}
		}
		for (Iterator<MacroState> iterator = listeners.values().iterator(); iterator.hasNext();) {
			MacroState state = iterator.next();
			if (!present.contains(state.macro.id())) {
				state.stop();
				iterator.remove();
			}
		}
	}

	private void synchronizeStacks(MacroState state) {
		state.events.entrySet().removeIf(entry -> {
			if (!state.macro.stacks().contains(entry.getKey())) {
				entry.getValue().stop();
				return true;
			}
			return false;
		});
		for (ScriptStack stack : state.macro.stacks()) {
			if (!stack.isEmpty() && stack.blocks().getFirst().type() != null
					&& stack.blocks().getFirst().type().isEvent()) {
				state.events.computeIfAbsent(stack, EventState::new);
			} else {
				EventState removed = state.events.remove(stack);
				if (removed != null) {
					removed.stop();
				}
			}
		}
	}

	private void dispatch(MacroState macro, EventState event, BlockInstance hat, PhysicalInput input) {
		String button = hat.arg("button");
		switch (hat.typeId()) {
			case BlockRegistry.WHEN_PRESSED -> {
				if (input.pressed(button)) {
					String mode = hat.arg("mode").trim().toLowerCase(Locale.ROOT);
					if (event.execution != null && ("restart".equals(mode) || "toggle".equals(mode))) {
						event.stop();
						if ("toggle".equals(mode)) {
							return;
						}
					}
					start(macro, event, input);
				}
			}
			case BlockRegistry.WHEN_RELEASED -> {
				if (input.released(button)) {
					start(macro, event, input);
				}
			}
			case BlockRegistry.WHILE_HELD -> {
				if (input.pressed(button)) {
					event.heldActive = true;
				}
				if (!input.held(button)) {
					event.heldActive = false;
					event.stop();
				} else if (event.heldActive) {
					start(macro, event, input);
				}
			}
			case BlockRegistry.WHEN_ENABLED -> {
				if (!event.enabledFired && !macro.autoSuppressed && start(macro, event, input)) {
					event.enabledFired = true;
				}
			}
			case BlockRegistry.EVERY -> {
				if (macro.autoSuppressed) {
					return;
				}
				int interval = intervalTicks(hat.arg("seconds"));
				if (event.nextRunTick == 0) {
					event.nextRunTick = input.tickNumber() + interval;
				} else if (input.tickNumber() >= event.nextRunTick) {
					event.nextRunTick = input.tickNumber() + interval;
					start(macro, event, input);
				}
			}
			default -> { }
		}
	}

	private static int intervalTicks(String seconds) {
		try {
			double ticks = Double.parseDouble(seconds.trim()) * 20.0;
			return Double.isFinite(ticks) && ticks > 0
					? (int) Math.clamp(Math.ceil(ticks), 1, Integer.MAX_VALUE) : Integer.MAX_VALUE;
		} catch (NumberFormatException exception) {
			return 100;
		}
	}

	private boolean start(MacroState macro, EventState event, PhysicalInput input) {
		if (event.execution != null) {
			return false;
		}
		int running = 0;
		for (MacroState state : listeners.values()) {
			for (EventState other : state.events.values()) {
				if (other.execution != null) {
					running++;
				}
			}
		}
		if (running < MAX_CONCURRENT) {
			event.execution = new MacroExecution(macro.macro, event.stack, input, macro.variables);
			return true;
		}
		return false;
	}

	private static void advance(MacroState macro, EventState event) {
		if (event.execution == null) {
			return;
		}
		try {
			event.execution.tick();
		} catch (RuntimeException exception) {
			event.stop();
			MiniMacrosClient.LOGGER.error("Stopped event in macro '{}' after an execution error", macro.macro.name(), exception);
		}
		if (event.execution != null && event.execution.finished()) {
			event.execution = null;
		}
	}

	/** Panic stops current bodies; automatic hats stay latched until re-enabled. */
	public void stopAll() {
		for (MacroState state : listeners.values()) {
			state.stop();
			state.autoSuppressed = true;
		}
	}

	/** A new world/session starts with new listeners and clean macro variables. */
	public void reset() {
		for (MacroState state : listeners.values()) {
			state.stop();
		}
		listeners.clear();
		lastLevel = null;
		lastPlayer = null;
	}

	private static final class MacroState {
		private final MacroDefinition macro;
		private final Map<String, Object> variables = new LinkedHashMap<>();
		private final Map<ScriptStack, EventState> events = new IdentityHashMap<>();
		private boolean enabled;
		private boolean autoSuppressed;

		private MacroState(MacroDefinition macro) {
			this.macro = macro;
		}

		private void stop() {
			for (EventState event : events.values()) {
				event.stop();
				event.heldActive = false;
				event.nextRunTick = 0;
			}
		}

		private void disable() {
			stop();
			for (EventState event : events.values()) {
				event.enabledFired = false;
			}
			variables.clear();
			enabled = false;
			autoSuppressed = false;
		}
	}

	private static final class EventState {
		private final ScriptStack stack;
		private MacroExecution execution;
		private boolean heldActive;
		private boolean enabledFired;
		private long nextRunTick;

		private EventState(ScriptStack stack) {
			this.stack = stack;
		}

		private void stop() {
			if (execution != null) {
				execution.stop();
				execution = null;
			}
		}
	}
}
