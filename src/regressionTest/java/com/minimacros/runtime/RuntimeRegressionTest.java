package com.minimacros.runtime;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.MacroDefinition;
import com.minimacros.macro.ScriptStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Exercises the actual interpreter without launching a Minecraft client. */
public final class RuntimeRegressionTest {
	public static void main(String[] args) throws Exception {
		waitTicks(1);
		waitTicks(3);
		parallelStrands();
		loopAndBudget();
		stoppedTurnDoesNotRun();
		sharedInputOwnership();
		physicalSnapshot();
		pressModes();
		releaseAndSharedVariables();
		heldEvents();
		automaticEventsAndPanic();
		enabledLifecycleAndCapacity();
		inputWaits();
		System.out.println("Runtime regression checks passed");
	}

	private static void physicalSnapshot() {
		PhysicalInput input = new PhysicalInput();
		input.update(Set.of(), true);
		input.update(keys("left.control", "r", "mouse.right"), true);
		check(input.pressed("left.control + r") && input.pressed("mouse.right"), "physical combinations did not press");
		check(input.pressed("left.control + r"), "one listener consumed another listener's edge");
		input.update(keys("left.control", "r"), false);
		check(!input.held("r") && !input.released("mouse.right"), "screen input leaked into gameplay predicates");
		input.update(keys("left.control", "r"), true);
		check(input.held("r") && !input.pressed("r"), "closing a screen manufactured a press");
		input.update(Set.of(), true);
		check(input.released("left.control + r"), "physical combination did not release");
		KeyMapping.set(InputSimulator.resolveKey("w"), true);
		check(!input.held("w") && !input.pressed("w"), "simulated key mappings changed physical input");
		KeyMapping.set(InputSimulator.resolveKey("w"), false);
	}

	private static void pressModes() throws Exception {
		for (String mode : List.of("run once", "restart", "toggle")) {
			Harness harness = new Harness();
			BlockInstance hat = event(BlockRegistry.WHEN_PRESSED, "r");
			hat.setArg("mode", mode);
			ScriptStack stack = harness.stack(hat, increment("runs"), sleep(100));
			harness.tick();
			harness.tick("r");
			check(harness.value("runs").equals(1.0), "press did not start " + mode);
			harness.tick("r");
			check(harness.value("runs").equals(1.0), "held key repeatedly triggered " + mode);
			harness.tick();
			harness.tick("r");
			check(harness.value("runs").equals("restart".equals(mode) ? 2.0 : 1.0), "incorrect second-press behavior for " + mode);
			check(harness.running(stack) != "toggle".equals(mode), "incorrect execution state for " + mode);
			harness.runner.reset();
		}
	}

	private static void releaseAndSharedVariables() throws Exception {
		Harness harness = new Harness();
		harness.stack(event(BlockRegistry.WHEN_RELEASED, "mouse.right"), assign("released", "1"));
		harness.stack(event(BlockRegistry.WHEN_PRESSED, "left.control + r"), assign("shared", "7"));
		harness.stack(event(BlockRegistry.WHEN_PRESSED, "left.control + r"), assign("read", "shared"));
		harness.stack(assign("plain", "1"));
		harness.tick();
		harness.tick("mouse.right");
		check(harness.value("released") == Values.NONE, "release event fired on press");
		harness.tick();
		check(harness.value("released").equals(1.0), "release event did not fire");
		harness.tick("left.control", "r");
		check(harness.value("read").equals(7.0), "event stacks did not share macro variables");
		check(harness.value("plain") == Values.NONE, "plain stack ran without an event");
		harness.tick(false, false, "r");
		harness.tick("r");
		check(!harness.input.pressed("r"), "returning from screen produced a new edge");
	}

	private static void heldEvents() throws Exception {
		Harness harness = new Harness();
		ScriptStack stack = harness.stack(event(BlockRegistry.WHILE_HELD, "r"), increment("runs"), sleep(100));
		harness.tick();
		harness.tick("r");
		check(harness.running(stack), "held event did not start");
		harness.tick();
		check(!harness.running(stack), "held event did not stop immediately on release");
		harness.tick("r");
		check(harness.value("runs").equals(2.0), "new hold did not start a fresh body");
		harness.tick(false, false, "r");
		harness.tick("r");
		check(!harness.running(stack), "held event resumed a key held across a screen");
		harness.tick();
		harness.tick("r");
		check(harness.value("runs").equals(3.0), "held event failed to rearm after a screen");
		harness.runner.reset();

		Harness repeating = new Harness();
		repeating.stack(event(BlockRegistry.WHILE_HELD, "r"), increment("runs"));
		repeating.tick();
		repeating.tick("r");
		repeating.tick("r");
		check(repeating.value("runs").equals(2.0), "completed held body did not repeat once per tick");
	}

	private static void automaticEventsAndPanic() throws Exception {
		Harness harness = new Harness();
		harness.stack(event(BlockRegistry.WHEN_ENABLED, ""), increment("enabled"));
		BlockInstance every = event(BlockRegistry.EVERY, "");
		every.setArg("seconds", "0.1");
		harness.stack(every, increment("periodic"));
		harness.stack(event(BlockRegistry.WHEN_PRESSED, "r"), increment("manual"));
		harness.tick();
		check(harness.value("enabled").equals(1.0) && harness.value("periodic") == Values.NONE,
				"enable/periodic initial timing is wrong");
		harness.tick();
		harness.tick();
		check(harness.value("enabled").equals(1.0) && harness.value("periodic").equals(1.0),
				"periodic interval or one-shot enable is wrong");
		harness.tick(true, true);
		harness.tick();
		harness.tick("r");
		harness.tick();
		harness.tick();
		check(harness.value("manual").equals(1.0), "panic prevented a new manual gesture");
		check(harness.value("enabled").equals(1.0) && harness.value("periodic").equals(1.0),
				"automatic events immediately resumed after panic");
		harness.macro.setEnabled(false);
		harness.tick();
		check(harness.value("enabled") == Values.NONE, "disabling a macro retained its variables");
		harness.macro.setEnabled(true);
		harness.tick();
		harness.tick();
		harness.tick();
		check(harness.value("enabled").equals(1.0) && harness.value("periodic").equals(1.0),
				"disable/re-enable failed to rearm automatic events");
	}

	private static void inputWaits() throws Exception {
		Harness held = new Harness();
		BlockInstance waitUntil = BlockInstance.create(BlockRegistry.get(BlockRegistry.WAIT_UNTIL));
		BlockInstance predicate = BlockInstance.create(BlockRegistry.get("input.is_held"));
		predicate.setArg("button", "mouse.right");
		waitUntil.setInput("condition", predicate);
		held.stack(event(BlockRegistry.WHEN_ENABLED, ""), waitUntil, assign("after", "1"));
		held.tick();
		held.tick();
		check(held.value("after") == Values.NONE, "wait until advanced while false");
		held.tick("mouse.right");
		check(held.value("after").equals(1.0), "wait until failed to re-evaluate physical input");

		Harness next = new Harness();
		BlockInstance waitPress = BlockInstance.create(BlockRegistry.get(BlockRegistry.WAIT_NEXT_PRESS));
		waitPress.setArg("button", "r");
		next.stack(event(BlockRegistry.WHEN_PRESSED, "r"), waitPress, assign("after", "1"));
		next.tick();
		next.tick("r");
		next.tick("r");
		check(next.value("after") == Values.NONE, "wait for press consumed its triggering/already-held press");
		next.tick(false, false);
		next.tick(false, false, "r");
		next.tick("r");
		check(next.value("after") == Values.NONE, "wait for press consumed a screen gesture");
		next.tick();
		next.tick("r");
		check(next.value("after").equals(1.0), "wait for press did not resume on a fresh press");
	}

	private static void enabledLifecycleAndCapacity() throws Exception {
		Harness resumed = new Harness();
		resumed.stack(event(BlockRegistry.WHEN_ENABLED, ""), increment("enabled"));
		resumed.tick();
		resumed.input.update(Set.of(), false);
		resumed.runner.tick(List.of(resumed.macro), resumed.input, false, false, false);
		resumed.tick();
		check(resumed.value("enabled").equals(1.0), "temporary focus/pause/editor suspension retriggered When enabled");

		Harness capacity = new Harness();
		for (int i = 0; i < 17; i++) {
			capacity.stack(event(BlockRegistry.WHEN_ENABLED, ""), increment("started"), sleep(2));
		}
		capacity.tick();
		check(capacity.value("started").equals(16.0), "execution capacity did not bound concurrent starts");
		capacity.tick();
		capacity.tick();
		check(capacity.value("started").equals(17.0), "capacity discarded a pending When enabled event");
		capacity.tick();
		capacity.tick();
		check(capacity.value("started").equals(17.0), "enabled events fired more than once while draining capacity");
		capacity.runner.reset();
	}

	private static BlockInstance event(String type, String button) {
		BlockInstance block = BlockInstance.create(BlockRegistry.get(type));
		block.setArg("button", button);
		return block;
	}

	private static BlockInstance increment(String variable) {
		BlockInstance add = BlockInstance.create(BlockRegistry.get("vars.op.add"));
		BlockInstance read = BlockInstance.create(BlockRegistry.get(BlockRegistry.VARIABLE));
		read.setArg("name", variable);
		add.setInput("a", read);
		add.setArg("b", "1");
		BlockInstance assignment = assign(variable, "");
		assignment.setInput("b", add);
		return assignment;
	}

	private static Set<InputConstants.Key> keys(String... names) {
		return Arrays.stream(names).map(InputSimulator::resolveKey).collect(Collectors.toSet());
	}

	private static final class Harness {
		private final MacroRunner runner = new MacroRunner();
		private final PhysicalInput input = new PhysicalInput();
		private final MacroDefinition macro = new MacroDefinition("Events regression");

		private ScriptStack stack(BlockInstance... blocks) {
			ScriptStack stack = new ScriptStack(0, 0, List.of(blocks));
			macro.stacks().add(stack);
			return stack;
		}

		private void tick(String... held) {
			tick(true, false, held);
		}

		private void tick(boolean gameplayInput, boolean panic, String... held) {
			input.update(keys(held), gameplayInput && !panic);
			runner.tick(List.of(macro), input, true, gameplayInput, panic);
		}

		private Object state() throws Exception {
			return ((Map<?, ?>) field(runner, "listeners")).get(macro.id());
		}

		private Object value(String variable) throws Exception {
			return ((Map<?, ?>) field(state(), "variables")).getOrDefault(variable, null) == null
					? Values.NONE : ((Map<?, ?>) field(state(), "variables")).get(variable);
		}

		private boolean running(ScriptStack stack) throws Exception {
			Object event = ((Map<?, ?>) field(state(), "events")).get(stack);
			return event != null && field(event, "execution") != null;
		}
	}

	private static void waitTicks(int ticks) throws Exception {
		MacroExecution execution = execution(List.of(assign("result", "1"), sleep(ticks), assign("result", "2")));
		execution.tick();
		check(value(execution, "result").equals(1.0), "sleep must yield on its starting tick");
		for (int i = 1; i < ticks; i++) {
			execution.tick();
			check(value(execution, "result").equals(1.0), "sleep resumed too early");
		}
		execution.tick();
		check(value(execution, "result").equals(2.0), "sleep must resume exactly N ticks later");
		check(execution.finished(), "completed script did not finish");
	}

	private static void parallelStrands() throws Exception {
		MacroExecution execution = execution(List.of(sleep(2), assign("slow", "1")), List.of(assign("fast", "1")));
		execution.tick();
		check(value(execution, "fast").equals(1.0), "one sleeping strand blocked its sibling");
		check(value(execution, "slow") == Values.NONE, "sleeping strand ran too early");
		execution.tick();
		check(!execution.finished(), "macro finished while a strand was waiting");
		execution.tick();
		check(value(execution, "slow").equals(1.0) && execution.finished(), "waiting strand failed to finish");
	}

	private static void loopAndBudget() throws Exception {
		BlockInstance sum = BlockInstance.create(BlockRegistry.get("vars.op.add"));
		sum.setArg("a", "count");
		sum.setArg("b", "1");
		BlockInstance increment = assign("count", "");
		increment.setInput("b", sum);
		BlockInstance loop = BlockInstance.create(BlockRegistry.get(BlockRegistry.LOOP));
		loop.setArg("times", "3");
		loop.branch(0).add(increment);
		MacroExecution finite = execution(List.of(assign("count", "0"), loop));
		finite.tick();
		check(finite.finished() && value(finite, "count").equals(3.0), "loop iteration count changed");

		loop.setArg("times", "2147483647");
		MacroExecution runaway = execution(List.of(assign("count", "0"), loop));
		runaway.tick();
		check(!runaway.finished() && Values.toNumber(value(runaway, "count")) > 0,
				"large loop did not yield after doing bounded work");
		Object before = value(runaway, "count");
		runaway.stop();
		runaway.tick();
		check(runaway.finished() && value(runaway, "count").equals(before), "stopped script still executed blocks");
	}

	private static void stoppedTurnDoesNotRun() throws Exception {
		MacroExecution execution = execution(List.of(sleep(10)));
		execution.tick();
		Object strand = ((List<?>) field(execution, "strands")).getFirst();
		Field turnTicks = strand.getClass().getDeclaredField("turnTicksRemaining");
		turnTicks.setAccessible(true);
		turnTicks.setInt(strand, 1);
		execution.stop();
		execution.tick();
		check(turnTicks.getInt(strand) == 1, "stopped execution applied a pending camera sweep");
	}

	@SuppressWarnings("unchecked")
	private static void sharedInputOwnership() throws Exception {
		InputSimulator first = new InputSimulator();
		InputSimulator second = new InputSimulator();
		InputConstants.Key key = InputSimulator.resolveKey("w");
		Set<InputConstants.Key> secondHeld = (Set<InputConstants.Key>) field(second, "heldKeys");
		Map<InputConstants.Key, Integer> counts = (Map<InputConstants.Key, Integer>) field(first, "HOLD_COUNTS");
		KeyMapping mapping = new KeyMapping("key.mini-macros.regression", InputConstants.Type.KEYBOARD,
				key.getValue(), KeyMapping.Category.MISC);
		try {
			KeyMapping.set(key, true);
			first.keyUp("w");
			check(mapping.isDown(), "unowned buttonUp released physical gameplay input");
			first.keyDown("w");
			first.keyDown("w");
			check(counts.get(key) == 1, "repeated buttonDown acquired duplicate ownership");
			second.keyDown("w");
			check(counts.get(key) == 2, "second macro did not own its hold");
			first.releaseAll();
			check(counts.get(key) == 1 && secondHeld.contains(key) && mapping.isDown(),
					"stopping one macro released another macro's key");
			first.keyUp("w");
			first.releaseAll();
			check(counts.get(key) == 1, "unowned or repeated release changed another macro's hold");
		} finally {
			secondHeld.clear();
			counts.clear();
			KeyMapping.set(key, false);
		}
	}

	@SafeVarargs
	private static MacroExecution execution(List<BlockInstance>... stacks) {
		MacroDefinition macro = new MacroDefinition("Regression");
		for (List<BlockInstance> stack : stacks) {
			macro.stacks().add(new ScriptStack(0, 0, stack));
		}
		return new MacroExecution(macro);
	}

	private static BlockInstance assign(String name, String value) {
		BlockInstance block = BlockInstance.create(BlockRegistry.get(BlockRegistry.ASSIGN));
		block.setArg("a", name);
		block.setArg("b", value);
		return block;
	}

	private static BlockInstance sleep(int ticks) {
		BlockInstance block = BlockInstance.create(BlockRegistry.get("control.sleep"));
		block.setArg("ticks", Integer.toString(ticks));
		return block;
	}

	private static Object value(MacroExecution execution, String name) throws Exception {
		return ((ExecutionContext) field(execution, "context")).variable(name);
	}

	private static Object field(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static void check(boolean passed, String message) {
		if (!passed) {
			throw new AssertionError(message);
		}
	}
}
