package com.minimacros.macro;

import com.minimacros.runtime.Values;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every block Mini Macros knows about.
 *
 * <p>Control flow registers no action — {@link com.minimacros.runtime.MacroExecution}
 * recognises {@link #LOOP} and {@link #IF_ELSE} by id because they drive the
 * frame stack rather than doing work themselves.
 */
public final class BlockRegistry {

	// Control-flow ids the interpreter handles directly.
	public static final String LOOP = "control.loop";
	public static final String WHILE = "control.while";
	public static final String IF_ELSE = "control.if_else";

	public static final String ASSIGN = "vars.assign";
	public static final String VARIABLE = "vars.variable";

	/** Keyboard and mouse both go through these; see {@code InputSimulator.resolveKey}. */
	public static final String BUTTON_DOWN = "input.button_down";
	public static final String BUTTON_UP = "input.button_up";

	private static final Map<String, BlockType> TYPES = new LinkedHashMap<>();
	/**
	 * The registry never changes after class init, so the palette — which rebuilds
	 * its shelf every frame — gets one shared list rather than a fresh copy each
	 * time it asks.
	 */
	private static final List<BlockType> ALL;

	/**
	 * One block per operator, as {id suffix, symbol} in palette order. The symbol
	 * is both what the block reads as and what {@link Values#apply} switches on,
	 * so the two can never drift apart.
	 *
	 * <p>The three groups differ in shape, and the shape is the rule: maths
	 * reports a value into any slot, comparisons and logic are hexagons that only
	 * a condition takes, and logic takes only hexagons in turn.
	 */
	private static final String[][] MATH_OPERATORS = {
			{"add", "+"}, {"subtract", "-"}, {"multiply", "*"}, {"divide", "/"},
			{"modulo", "%"}, {"power", "**"},
	};

	private static final String[][] COMPARISON_OPERATORS = {
			{"greater", ">"}, {"less", "<"}, {"equals", "=="},
			{"greater_equal", ">="}, {"less_equal", "<="}, {"not_equals", "!="},
	};

	private static final String[][] LOGIC_OPERATORS = {
			{"and", "and"}, {"or", "or"},
	};

	private BlockRegistry() {
	}

	// Declared after every constant it reads: static initialisers run in
	// textual order, so a constant below this block would still be null here.
	static {
		registerInput();
		registerControl();
		registerMessages();
		registerVariables();
		registerOperators();
		registerComparators();
		ALL = List.copyOf(TYPES.values());
	}

	// --- lookup ---------------------------------------------------------

	public static BlockType get(String id) {
		return TYPES.get(id);
	}

	public static List<BlockType> all() {
		return ALL;
	}

	private static void register(BlockType type) {
		TYPES.put(type.id(), type);
	}

	/** The block id carrying {@code symbol}, e.g. {@code "+"} to {@code vars.op.add}. */
	public static String operatorBlockId(String symbol) {
		for (String[][] group : new String[][][] {MATH_OPERATORS, COMPARISON_OPERATORS, LOGIC_OPERATORS}) {
			for (String[] operator : group) {
				if (operator[1].equals(symbol)) {
					return "vars.op." + operator[0];
				}
			}
		}
		return null;
	}

	// --- Input ----------------------------------------------------------

	private static void registerInput() {
		// One pair of blocks for the whole keyboard and mouse: the capture records
		// whatever was pressed, and InputSimulator resolves both the same way.
		// Down and up are separate blocks, so how long something is held is just
		// a sleep between them.
		register(BlockType.builder(BUTTON_DOWN, BlockCategory.INPUT, "buttonDown %s")
				.params(ParamSpec.keySlot("button"))
				.action((block, ctx) -> {
					ctx.keyDown(Values.display(ctx.resolve(block, "button")));
					return 0;
				})
				.build());

		register(BlockType.builder(BUTTON_UP, BlockCategory.INPUT, "buttonUp %s")
				.params(ParamSpec.keySlot("button"))
				.action((block, ctx) -> {
					ctx.keyUp(Values.display(ctx.resolve(block, "button")));
					return 0;
				})
				.build());

		register(BlockType.builder("input.look_x", BlockCategory.INPUT, "lookX %s degrees over %s ticks")
				.params(ParamSpec.numberSlot("degrees"), ParamSpec.intSlot("duration"))
				.action((block, ctx) -> ctx.turn(ctx.resolveNumber(block, "degrees", 0), 0,
						ctx.resolveInt(block, "duration", 1)))
				.build());

		register(BlockType.builder("input.look_y", BlockCategory.INPUT, "lookY %s degrees over %s ticks")
				.params(ParamSpec.numberSlot("degrees"), ParamSpec.intSlot("duration"))
				.action((block, ctx) -> ctx.turn(0, ctx.resolveNumber(block, "degrees", 0),
						ctx.resolveInt(block, "duration", 1)))
				.build());

		register(BlockType.builder("input.echo", BlockCategory.INPUT, "echo %s")
				.params(ParamSpec.slot("text"))
				.action((block, ctx) -> {
					ctx.echo(ctx.text(ctx.resolve(block, "text")));
					return 0;
				})
				.build());
	}

	// --- Control --------------------------------------------------------

	private static void registerControl() {
		register(BlockType.builder("control.sleep", BlockCategory.CONTROL, "sleep %s ticks")
				.params(ParamSpec.intSlot("ticks"))
				.action((block, ctx) -> Math.max(0, ctx.resolveInt(block, "ticks", 0)))
				.build());

		// A fixed number of passes, counted once when the loop is reached. Anything
		// that is not a number is not a count — see ExecutionContext.resolveCount.
		register(BlockType.builder(LOOP, BlockCategory.CONTROL, "loop %s")
				.params(ParamSpec.intSlot("times"))
				.branches("")
				.build());

		// Re-checks its condition before every pass, which is what "loop" cannot do.
		register(BlockType.builder(WHILE, BlockCategory.CONTROL, "while %s")
				.params(ParamSpec.booleanSlot("condition"))
				.branches("")
				.build());

		register(BlockType.builder(IF_ELSE, BlockCategory.CONTROL, "if %s")
				.params(ParamSpec.booleanSlot("condition"))
				.branches("", "else")
				.build());
	}

	// --- Messages -------------------------------------------------------

	private static void registerMessages() {
		register(BlockType.builder("messages.print", BlockCategory.MESSAGES, "print %s")
				.params(ParamSpec.slot("text"))
				.action((block, ctx) -> {
					ctx.printChat(ctx.text(ctx.resolve(block, "text")));
					return 0;
				})
				.build());

		register(BlockType.builder("messages.actionbar", BlockCategory.MESSAGES, "actionbar %s")
				.params(ParamSpec.slot("text"))
				.action((block, ctx) -> {
					ctx.actionBar(ctx.text(ctx.resolve(block, "text")));
					return 0;
				})
				.build());
	}

	// --- Variables ------------------------------------------------------

	/** Registration order is palette order, so {@code =} leads and the literals trail. */
	private static void registerVariables() {
		// A regular block, not a reporter: assigning is something the script does,
		// so it joins the stack the way print does rather than plugging into a slot.
		register(BlockType.builder(ASSIGN, BlockCategory.VARIABLES, "%s = %s")
				.params(ParamSpec.slot("a"), ParamSpec.slot("b"))
				.action((block, ctx) -> {
					ctx.assign(assignmentTarget(block), ctx.resolve(block, "b"));
					return 0;
				})
				.build());

		// An input block, so it stands for its variable wherever a value is wanted.
		register(BlockType.builder(VARIABLE, BlockCategory.VARIABLES, "var %s")
				.params(ParamSpec.string("name"))
				.reporter((block, ctx) -> {
					String name = block.arg("name");
					ctx.declare(name);
					return ctx.variable(name);
				})
				.build());

		// Input blocks, not compare blocks: they are values to be assigned or
		// printed, and a condition should be a comparison rather than a constant.
		register(BlockType.builder("vars.true", BlockCategory.VARIABLES, Values.TRUE_LITERAL)
				.reporter((block, ctx) -> Boolean.TRUE)
				.build());

		register(BlockType.builder("vars.false", BlockCategory.VARIABLES, Values.FALSE_LITERAL)
				.reporter((block, ctx) -> Boolean.FALSE)
				.build());

		register(BlockType.builder("vars.none", BlockCategory.VARIABLES, Values.NONE_LITERAL)
				.reporter((block, ctx) -> Values.NONE)
				.build());
	}

	// --- Operators ------------------------------------------------------

	/** Rounded input blocks: they report a number or a string into any round slot. */
	private static void registerOperators() {
		for (String[] operator : MATH_OPERATORS) {
			register(operatorBuilder(operator[0], operator[1], BlockCategory.OPERATORS)
					.params(ParamSpec.slot("a"), ParamSpec.slot("b"))
					.reporter((block, ctx) ->
							Values.apply(operator[1], ctx.resolve(block, "a"), ctx.resolve(block, "b")))
					.build());
		}
	}

	/** Hexagonal compare blocks: the only things a condition will take. */
	private static void registerComparators() {
		// Compares two values, so its own slots are round.
		for (String[] operator : COMPARISON_OPERATORS) {
			register(operatorBuilder(operator[0], operator[1], BlockCategory.COMPARATORS)
					.params(ParamSpec.slot("a"), ParamSpec.slot("b"))
					.predicate((block, ctx) ->
							Values.apply(operator[1], ctx.resolve(block, "a"), ctx.resolve(block, "b")))
					.build());
		}

		// Combines conditions, so it takes conditions: hexagonal in and out.
		for (String[] operator : LOGIC_OPERATORS) {
			register(operatorBuilder(operator[0], operator[1], BlockCategory.COMPARATORS)
					.params(ParamSpec.booleanSlot("a"), ParamSpec.booleanSlot("b"))
					.predicate((block, ctx) ->
							Values.apply(operator[1], ctx.resolve(block, "a"), ctx.resolve(block, "b")))
					.build());
		}
	}

	/** Every operator reads as {@code a <symbol> b} and is named after its symbol. */
	private static BlockType.Builder operatorBuilder(String idSuffix, String symbol,
			BlockCategory category) {
		return BlockType.builder("vars.op." + idSuffix, category, "%s " + symbol + " %s");
	}

	/**
	 * What an {@code =} assigns to. A {@code var} block in the left slot names
	 * the target; otherwise the typed text is the name.
	 */
	private static String assignmentTarget(BlockInstance operation) {
		BlockInstance plugged = operation.input("a");
		if (plugged != null && VARIABLE.equals(plugged.typeId())) {
			return plugged.arg("name");
		}
		return operation.arg("a");
	}
}
