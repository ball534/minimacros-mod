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

/** Exercises the actual interpreter without launching a Minecraft client. */
public final class RuntimeRegressionTest {
	public static void main(String[] args) throws Exception {
		waitTicks(1);
		waitTicks(3);
		parallelStrands();
		loopAndBudget();
		stoppedTurnDoesNotRun();
		sharedInputOwnership();
		System.out.println("Runtime regression checks passed");
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
