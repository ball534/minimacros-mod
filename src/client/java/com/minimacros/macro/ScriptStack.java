package com.minimacros.macro;

import java.util.ArrayList;
import java.util.List;

/**
 * One free-standing column of blocks inside a macro, with the canvas position it
 * was left at.
 *
 * <p>Blocks within a stack run one after another. Separate stacks run at the same
 * time as each other, so a macro can hold a movement loop beside a chat loop
 * without either waiting on the other.
 */
public class ScriptStack {
	private int x;
	private int y;
	private List<BlockInstance> blocks = new ArrayList<>();

	@SuppressWarnings("unused") // Gson
	ScriptStack() {
	}

	public ScriptStack(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public ScriptStack(int x, int y, List<BlockInstance> blocks) {
		this.x = x;
		this.y = y;
		this.blocks = new ArrayList<>(blocks);
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public void moveTo(int x, int y) {
		this.x = x;
		this.y = y;
	}

	/** The live list — the editor adds to and removes from it in place. */
	public List<BlockInstance> blocks() {
		return blocks;
	}

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	public ScriptStack copy() {
		ScriptStack clone = new ScriptStack(x, y);
		for (BlockInstance block : blocks) {
			clone.blocks.add(block.copy());
		}
		return clone;
	}

	/** Repairs a freshly deserialised stack; see {@link BlockInstance#normalize()}. */
	public void normalize() {
		if (blocks == null) {
			blocks = new ArrayList<>();
		}
		for (BlockInstance block : blocks) {
			block.normalize();
		}
	}
}
