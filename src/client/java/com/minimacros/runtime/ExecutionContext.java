package com.minimacros.runtime;

import com.minimacros.MiniMacrosClient;
import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything a block's action or evaluator is allowed to reach: the player, the
 * input simulator, this run's variables, and value resolution for slots.
 */
public final class ExecutionContext {
	private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([^}]*)}");

	private final MacroExecution execution;
	private final InputSimulator input;
	private final Map<String, Object> variables;
	/** Params already reported as holding a non-count, so a nested loop warns once. */
	private final Set<String> warnedCounts = new HashSet<>();

	ExecutionContext(MacroExecution execution, InputSimulator input) {
		this(execution, input, new LinkedHashMap<>());
	}

	ExecutionContext(MacroExecution execution, InputSimulator input, Map<String, Object> variables) {
		this.execution = execution;
		this.input = input;
		this.variables = variables;
	}

	public LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	/** The open screen, or null when the player is in the world. */
	public Screen screen() {
		return Minecraft.getInstance().gui.screen();
	}

	// --- values ---------------------------------------------------------

	/**
	 * Reads a slot: a plugged-in reporter if there is one, otherwise the typed
	 * text. Bare text resolves as a variable name when one is declared, so
	 * {@code print (x)} shows what x holds.
	 */
	public Object resolve(BlockInstance owner, String paramId) {
		BlockInstance plugged = owner.input(paramId);
		if (plugged != null) {
			return evaluate(plugged);
		}
		return resolveText(owner.arg(paramId));
	}

	public Object resolveText(String text) {
		String trimmed = text == null ? "" : text.trim();
		if (variables.containsKey(trimmed)) {
			return variables.get(trimmed);
		}
		return Values.parse(trimmed);
	}

	public Object evaluate(BlockInstance reporter) {
		BlockType type = reporter.type();
		if (type == null || type.evaluator() == null) {
			return Values.NONE;
		}
		return type.evaluator().evaluate(reporter, this);
	}

	public int resolveInt(BlockInstance owner, String paramId, int fallback) {
		Object value = resolve(owner, paramId);
		return value == Values.NONE ? fallback : Values.toInt(value);
	}

	/**
	 * A repeat count, which only a number can be. A comparison reporter dropped
	 * into {@code loop} used to coerce to 1 and run the body a single time, which
	 * read as the loop silently doing nothing — {@code while} is the block for a
	 * condition. Anything that is not a number counts as zero passes.
	 */
	public int resolveCount(BlockInstance owner, String paramId) {
		Object value = resolve(owner, paramId);
		if (value instanceof Double number && !number.isNaN()) {
			return (int) Math.round((double) number);
		}
		if (warnedCounts.add(owner.typeId() + "." + paramId)) {
			MiniMacrosClient.LOGGER.warn(
					"'{}' needs a number of times to repeat but was given {} — use a while block for a condition",
					owner.typeId(), Values.display(value));
		}
		return 0;
	}

	public double resolveNumber(BlockInstance owner, String paramId, double fallback) {
		Object value = resolve(owner, paramId);
		return value == Values.NONE ? fallback : Values.toNumber(value);
	}

	/** Renders a value for display, expanding any {@code ${name}} it contains. */
	public String text(Object value) {
		return interpolate(Values.display(value));
	}

	// --- variables ------------------------------------------------------

	/** Declares a variable, starting at None. Re-declaring keeps the value. */
	public void declare(String name) {
		variables.putIfAbsent(key(name), Values.NONE);
	}

	public void assign(String name, Object value) {
		variables.put(key(name), value);
	}

	public Object variable(String name) {
		return variables.getOrDefault(key(name), Values.NONE);
	}

	private static String key(String name) {
		return name == null ? "" : name.trim();
	}

	/** Substitutes {@code ${name}} inside a longer piece of text. */
	public String interpolate(String text) {
		if (text == null || text.indexOf('$') < 0) {
			return text == null ? "" : text;
		}
		Matcher matcher = INTERPOLATION.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(out,
					Matcher.quoteReplacement(Values.display(variable(matcher.group(1)))));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	// --- messages -------------------------------------------------------

	public void printChat(String text) {
		LocalPlayer player = player();
		if (player != null) {
			player.sendSystemMessage(Component.literal(text));
		}
	}

	public void actionBar(String text) {
		LocalPlayer player = player();
		if (player != null) {
			player.sendOverlayMessage(Component.literal(text));
		}
	}

	// --- input ----------------------------------------------------------

	public boolean inputHeld(String button) {
		return execution.physicalInput().held(button);
	}

	public boolean inputPressed(String button) {
		return execution.physicalInput().pressed(button);
	}

	public boolean inputReleased(String button) {
		return execution.physicalInput().released(button);
	}

	/**
	 * Holds a key or mouse button down until {@link #keyUp}. With a screen open
	 * the press goes to that screen instead, which is what makes the
	 * "buttonDown t, echo, buttonDown enter" chat pattern work.
	 */
	public void keyDown(String keyName) {
		Screen screen = screen();
		if (screen != null) {
			input.keyDownOnScreen(screen, keyName);
		} else {
			input.keyDown(keyName);
		}
	}

	public void keyUp(String keyName) {
		Screen screen = screen();
		if (screen != null) {
			input.keyUpOnScreen(screen, keyName);
		} else {
			input.keyUp(keyName);
		}
	}

	/** Types a string into the open screen, character by character. */
	public void echo(String text) {
		Screen screen = screen();
		if (screen != null) {
			input.typeIntoScreen(screen, text);
		}
	}

	/** Sweeps the camera over {@code ticks}; returns the ticks to wait for it. */
	public int turn(double yawDegrees, double pitchDegrees, int ticks) {
		return execution.scheduleTurn(yawDegrees, pitchDegrees, ticks);
	}
}
