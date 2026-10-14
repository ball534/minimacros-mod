package com.minimacros.macro;

import com.minimacros.runtime.ExecutionContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The definition of a kind of block: how it looks, what it can be given, and
 * what it does. Adding a block to the mod is a single entry in
 * {@link BlockRegistry}.
 *
 * <p>The label uses {@code %s} placeholders, filled left to right by
 * {@link #params()}. Control-flow blocks leave {@link #action()} null — the
 * interpreter recognises them by id because they drive the frame stack rather
 * than doing work themselves.
 */
public final class BlockType {

	/**
	 * Stack blocks join top to bottom; reporters are ovals that plug into slots.
	 * Booleans are hexagons — they report a value like a reporter does, but their
	 * shape is what says they belong in a condition, and only they fit one.
	 */
	public enum Shape {
		STACK,
		REPORTER,
		BOOLEAN,
		EVENT
	}

	@FunctionalInterface
	public interface Action {
		/**
		 * Performs the block's effect.
		 *
		 * @return ticks to wait before running the next block; 0 continues immediately.
		 */
		int run(BlockInstance block, ExecutionContext ctx);
	}

	@FunctionalInterface
	public interface Evaluator {
		/** Produces this reporter's value; see {@link com.minimacros.runtime.Values}. */
		Object evaluate(BlockInstance block, ExecutionContext ctx);
	}

	private final String id;
	private final BlockCategory category;
	private final String label;
	private final List<ParamSpec> params;
	private final List<String> branchLabels;
	private final Shape shape;
	private final Action action;
	private final Evaluator evaluator;
	private final List<String> labelSegments;

	private BlockType(Builder builder) {
		this.id = builder.id;
		this.category = builder.category;
		this.label = builder.label;
		this.params = List.copyOf(builder.params);
		this.branchLabels = List.copyOf(builder.branchLabels);
		this.shape = builder.shape;
		this.action = builder.action;
		this.evaluator = builder.evaluator;
		this.labelSegments = List.of(builder.label.split("%s", -1));
	}

	public static Builder builder(String id, BlockCategory category, String label) {
		return new Builder(id, category, label);
	}

	public String id() {
		return id;
	}

	public BlockCategory category() {
		return category;
	}

	public String label() {
		return label;
	}

	public List<ParamSpec> params() {
		return params;
	}

	/**
	 * Literal text around the {@code %s} placeholders. Always one longer than
	 * {@link #params()}, so the renderer can alternate text, field, text, field…
	 */
	public List<String> labelSegments() {
		return labelSegments;
	}

	public int branchCount() {
		return branchLabels.size();
	}

	/** Text drawn on the divider above each branch after the first (e.g. "else"). */
	public String branchLabel(int index) {
		return branchLabels.get(index);
	}

	public Shape shape() {
		return shape;
	}

	/** True for value-producing shapes that can be plugged into a slot. */
	public boolean isReporter() {
		return shape == Shape.REPORTER || shape == Shape.BOOLEAN;
	}

	/** True only for hexagons, which are the sole things a condition slot takes. */
	public boolean isBoolean() {
		return shape == Shape.BOOLEAN;
	}

	/** True for a hat block that starts an event-driven stack. */
	public boolean isEvent() {
		return shape == Shape.EVENT;
	}

	public Action action() {
		return action;
	}

	public Evaluator evaluator() {
		return evaluator;
	}

	public static final class Builder {
		private final String id;
		private final BlockCategory category;
		private final String label;
		private List<ParamSpec> params = Collections.emptyList();
		private List<String> branchLabels = Collections.emptyList();
		private Shape shape = Shape.STACK;
		private Action action;
		private Evaluator evaluator;

		private Builder(String id, BlockCategory category, String label) {
			this.id = id;
			this.category = category;
			this.label = label;
		}

		public Builder params(ParamSpec... specs) {
			this.params = Arrays.asList(specs);
			return this;
		}

		/** One label per branch; the first is unused (it sits under the header). */
		public Builder branches(String... labels) {
			this.branchLabels = Arrays.asList(labels);
			return this;
		}

		public Builder action(Action action) {
			this.action = action;
			return this;
		}

		/** Marks this a reporter oval and gives it its value. */
		public Builder reporter(Evaluator evaluator) {
			this.shape = Shape.REPORTER;
			this.evaluator = evaluator;
			return this;
		}

		/** Marks this a boolean hexagon — a reporter that only fits a condition. */
		public Builder predicate(Evaluator evaluator) {
			this.shape = Shape.BOOLEAN;
			this.evaluator = evaluator;
			return this;
		}

		/** Marks this a hat block that starts an event-driven stack. */
		public Builder event() {
			this.shape = Shape.EVENT;
			return this;
		}

		public BlockType build() {
			if (label.split("%s", -1).length - 1 != params.size()) {
				throw new IllegalStateException("Block '" + id + "' has "
						+ (label.split("%s", -1).length - 1) + " placeholders but " + params.size() + " params");
			}
			return new BlockType(this);
		}
	}
}
