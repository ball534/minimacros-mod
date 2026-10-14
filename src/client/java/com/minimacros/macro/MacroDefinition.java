package com.minimacros.macro;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** A named, keybindable script: one or more stacks that all run at once. */
public class MacroDefinition {
	public static final int UNBOUND = InputConstants.UNKNOWN.getValue();

	/** Where a stack migrated from a single-script save is placed on the canvas. */
	private static final int LEGACY_STACK_X = 20;
	private static final int LEGACY_STACK_Y = 16;

	private String id = UUID.randomUUID().toString();
	private String name = "New Macro";
	private boolean enabled = true;
	private int keyCode = UNBOUND;
	/** SDL scancodes of the modifier keys held alongside {@link #keyCode}. */
	private List<Integer> modifierKeys = new ArrayList<>();
	private List<ScriptStack> stacks = new ArrayList<>();
	/**
	 * The single block list macros used to hold. Only ever read, by
	 * {@link #normalize()}, which folds it into {@link #stacks} and clears it so it
	 * is left out of the next save.
	 */
	private List<BlockInstance> script;

	public MacroDefinition() {
	}

	public MacroDefinition(String name) {
		this.name = name;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/** Disabled macros keep their keybind but never fire. */
	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** SDL scancode, or {@link #UNBOUND}. */
	public int keyCode() {
		return keyCode;
	}

	public List<Integer> modifierKeys() {
		return modifierKeys;
	}

	public void setKeybind(int keyCode, List<Integer> modifierKeys) {
		this.keyCode = keyCode;
		this.modifierKeys = new ArrayList<>(modifierKeys);
	}

	public void clearKeybind() {
		this.keyCode = UNBOUND;
		this.modifierKeys = new ArrayList<>();
	}

	public void migrateLegacyKeybind() {
		keyCode = Keybind.fromLegacyCode(keyCode);
		modifierKeys = modifierKeys.stream().map(Keybind::fromLegacyCode)
				.filter(key -> key != UNBOUND).collect(Collectors.toCollection(ArrayList::new));
	}

	public boolean hasKeybind() {
		return keyCode != UNBOUND;
	}

	public Component keyDisplayName() {
		return Keybind.displayName(keyCode, modifierKeys);
	}

	/** The live list — the editor adds and removes stacks in place. */
	public List<ScriptStack> stacks() {
		return stacks;
	}

	/** A faithful clone, id included — used for the editor's working copy. */
	public MacroDefinition copy() {
		MacroDefinition clone = new MacroDefinition(name);
		clone.id = id;
		clone.enabled = enabled;
		clone.keyCode = keyCode;
		clone.modifierKeys = new ArrayList<>(modifierKeys);
		for (ScriptStack stack : stacks) {
			clone.stacks.add(stack.copy());
		}
		return clone;
	}

	/** Repairs a freshly deserialised macro; see {@link BlockInstance#normalize()}. */
	public void normalize() {
		if (id == null) {
			id = UUID.randomUUID().toString();
		}
		if (name == null) {
			name = "Macro";
		}
		if (modifierKeys == null) {
			modifierKeys = new ArrayList<>();
		}
		modifierKeys.removeIf(key -> key == null);
		if (stacks == null) {
			stacks = new ArrayList<>();
		}
		stacks.removeIf(stack -> stack == null);
		// A save from before macros could hold more than one stack.
		if (script != null) {
			script.removeIf(block -> block == null);
			if (!script.isEmpty()) {
				stacks.add(new ScriptStack(LEGACY_STACK_X, LEGACY_STACK_Y, script));
			}
			script = null;
		}
		for (ScriptStack stack : stacks) {
			stack.normalize();
		}
	}
}
