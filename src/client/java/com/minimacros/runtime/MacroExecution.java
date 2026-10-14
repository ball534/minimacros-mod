package com.minimacros.runtime;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.BlockType;
import com.minimacros.macro.MacroDefinition;
import com.minimacros.macro.ScriptStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * One in-flight macro.
 *
 * <p>Each of the macro's stacks becomes a {@link Strand} with its own frame stack
 * and its own wait timer, and every strand is advanced on every tick — so stacks
 * standing side by side on the canvas genuinely run alongside each other, while
 * the blocks within one stack still run strictly in order. Frames are explicit
 * rather than recursive so a strand can pause mid-script for a {@code wait} and
 * pick up again on a later tick.
 *
 * <p>Variables and held keys are shared across strands: they are one macro, so
 * one stack can set a variable another reads, or hold a key another releases.
 */
public final class MacroExecution {
	/**
	 * Blocks a strand executes per tick before it yields. This is what keeps a big
	 * {@code loop} with no {@code sleep} inside it from freezing the client — it
	 * simply gets the rest of its work on the next tick.
	 */
	private static final int BLOCK_BUDGET_PER_TICK = 1000;

	/** Guards against a script nesting itself into a stack overflow. */
	private static final int MAX_DEPTH = 128;

	private final MacroDefinition macro;
	private final InputSimulator input = new InputSimulator();
	private final ExecutionContext context;
	private final List<Strand> strands = new ArrayList<>();

	/** The strand currently being stepped, so blocks act on the right one. */
	private Strand active;

	private boolean stopRequested;
	private boolean finished;

	public MacroExecution(MacroDefinition macro) {
		this.macro = macro;
		this.context = new ExecutionContext(this, input);
		for (ScriptStack stack : macro.stacks()) {
			if (!stack.isEmpty()) {
				strands.add(new Strand(stack.blocks()));
			}
		}
	}

	public MacroDefinition macro() {
		return macro;
	}

	public boolean finished() {
		return finished;
	}

	/** Ends the run and releases anything it was holding down. */
	public void stop() {
		if (!finished) {
			finished = true;
			input.releaseAll();
		}
	}

	/**
	 * Starts a camera sweep spread over {@code ticks} for the running strand.
	 *
	 * @return the ticks that strand should wait for it to finish.
	 */
	int scheduleTurn(double yawDegrees, double pitchDegrees, int ticks) {
		Strand strand = active;
		if (strand == null || !Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
			return 0;
		}
		strand.turnYawRemaining = yawDegrees;
		strand.turnPitchRemaining = pitchDegrees;

		if (ticks <= 1) {
			strand.turnTicksRemaining = 1;
			applyTurnSlice(strand);
			return 0;
		}
		strand.turnTicksRemaining = ticks;
		return ticks;
	}

	private void applyTurnSlice(Strand strand) {
		if (strand.turnTicksRemaining <= 0) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			strand.turnTicksRemaining = 0;
			return;
		}

		double yawStep = strand.turnYawRemaining / strand.turnTicksRemaining;
		double pitchStep = strand.turnPitchRemaining / strand.turnTicksRemaining;
		player.setYRot((float) (player.getYRot() + yawStep));
		player.setXRot(Math.clamp((float) (player.getXRot() + pitchStep), -90f, 90f));

		strand.turnYawRemaining -= yawStep;
		strand.turnPitchRemaining -= pitchStep;
		strand.turnTicksRemaining--;
	}

	public void tick() {
		if (finished) {
			return;
		}

		// Camera sweeps keep running even while their strand sleeps.
		for (Strand strand : strands) {
			applyTurnSlice(strand);
		}

		boolean anyRunning = false;
		for (Strand strand : strands) {
			if (strand.done) {
				continue;
			}
			tickStrand(strand);
			if (stopRequested) {
				break;
			}
			anyRunning |= !strand.done;
		}

		// The run is over once every strand has fallen off the end of its stack.
		if (stopRequested || !anyRunning) {
			stop();
		}
	}

	/** Advances one strand until it yields, finishes or runs out of budget. */
	private void tickStrand(Strand strand) {
		if (strand.waitTicks > 0 && --strand.waitTicks > 0) {
			return;
		}

		active = strand;
		try {
			int budget = BLOCK_BUDGET_PER_TICK;
			while (budget-- > 0) {
				if (stopRequested) {
					return;
				}
				if (strand.frames.isEmpty()) {
					strand.done = true;
					return;
				}

				Frame frame = strand.frames.peek();

				// Reached the end of this frame's list: loop back around, or leave it.
				if (frame.index >= frame.blocks.size()) {
					if (!shouldRepeat(frame)) {
						strand.frames.pop();
					}
					continue;
				}

				BlockInstance block = frame.blocks.get(frame.index);
				BlockType type = block.type();
				if (type == null) {
					// A macro saved by a newer version, or a block that was removed.
					frame.index++;
					continue;
				}

				if (!step(strand, frame, block, type)) {
					return;
				}
			}
		} finally {
			active = null;
		}
	}

	/**
	 * Runs one block.
	 *
	 * @return false if the strand should yield the rest of this tick.
	 */
	private boolean step(Strand strand, Frame frame, BlockInstance block, BlockType type) {
		switch (type.id()) {
			case BlockRegistry.LOOP -> {
				frame.index++;
				int times = context.resolveCount(block, "times");
				if (times > 0) {
					push(strand, block, block.branch(0), times);
				}
			}
			case BlockRegistry.WHILE -> {
				frame.index++;
				// The condition is re-checked in shouldRepeat before every further
				// pass, so the body sees each change it makes to what it tests.
				if (Values.truthy(context.resolve(block, "condition"))) {
					push(strand, block, block.branch(0), 0);
				}
			}
			case BlockRegistry.IF_ELSE -> {
				frame.index++;
				boolean condition = Values.truthy(context.resolve(block, "condition"));
				push(strand, null, condition ? block.branch(0) : block.branch(1), 0);
			}
			default -> {
				frame.index++;
				BlockType.Action action = type.action();
				if (action != null) {
					int wait = action.run(block, context);
					if (wait > 0) {
						strand.waitTicks = wait;
						return false;
					}
				} else if (type.evaluator() != null) {
					// Input blocks cannot be placed in a script any more, but one
					// saved by an older version still can be — run it for its effect
					// rather than silently skipping it.
					context.evaluate(block);
				}
			}
		}
		return true;
	}

	/** Decides what happens when a frame runs off the end of its block list. */
	private boolean shouldRepeat(Frame frame) {
		if (frame.loopBlock == null) {
			return false;
		}
		String typeId = frame.loopBlock.typeId();

		if (BlockRegistry.WHILE.equals(typeId)) {
			if (!Values.truthy(context.resolve(frame.loopBlock, "condition"))) {
				return false;
			}
			frame.index = 0;
			return true;
		}

		if (!BlockRegistry.LOOP.equals(typeId)) {
			return false;
		}
		frame.iterationsLeft--;
		if (frame.iterationsLeft <= 0) {
			return false;
		}
		frame.index = 0;
		return true;
	}

	private void push(Strand strand, BlockInstance loopBlock, List<BlockInstance> blocks, int iterations) {
		if (strand.frames.size() >= MAX_DEPTH) {
			stopRequested = true;
			return;
		}
		strand.frames.push(new Frame(blocks, loopBlock, iterations));
	}

	/**
	 * One stack running on its own. Waits and camera sweeps are per strand, so a
	 * {@code sleep} in one stack does not hold up any of the others.
	 */
	private static final class Strand {
		private final Deque<Frame> frames = new ArrayDeque<>();
		private int waitTicks;
		private boolean done;

		// An in-progress lookX/lookY sweep, applied a slice at a time.
		private double turnYawRemaining;
		private double turnPitchRemaining;
		private int turnTicksRemaining;

		private Strand(List<BlockInstance> blocks) {
			frames.push(new Frame(blocks, null, 0));
		}
	}

	/**
	 * A position within one list of blocks. {@code loopBlock} is set when the
	 * list is a loop body, so running off the end can decide whether to wrap.
	 */
	private static final class Frame {
		private final List<BlockInstance> blocks;
		private final BlockInstance loopBlock;
		private int index;
		private int iterationsLeft;

		private Frame(List<BlockInstance> blocks, BlockInstance loopBlock, int iterations) {
			this.blocks = blocks;
			this.loopBlock = loopBlock;
			this.iterationsLeft = iterations;
		}
	}
}
