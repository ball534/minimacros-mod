package com.minimacros.macro;

import com.minimacros.runtime.Values;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
	public static final String WAIT_UNTIL = "control.wait_until";
	public static final String WAIT_NEXT_PRESS = "control.wait_next_press";

	public static final String WHEN_PRESSED = "events.when_pressed";
	public static final String WHEN_RELEASED = "events.when_released";
	public static final String WHILE_HELD = "events.while_held";
	public static final String WHEN_ENABLED = "events.when_enabled";
	public static final String EVERY = "events.every";

	public static final String ASSIGN = "vars.assign";
	public static final String VARIABLE = "vars.variable";

	/** Keyboard and mouse both go through these; see {@code InputSimulator.resolveKey}. */
	public static final String BUTTON_DOWN = "input.button_down";
	public static final String BUTTON_UP = "input.button_up";

	private static final Map<String, BlockType> TYPES = new LinkedHashMap<>();
	/**
	 * The registry never changes after class initialization, so all callers share
	 * one immutable list rather than creating copies on lookup.
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
		registerEvents();
		registerInput();
		registerInputPredicates();
		registerControl();
		registerMessages();
		registerVariables();
		registerPlayerState();
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

	// --- Events ----------------------------------------------------------

	private static void registerEvents() {
		register(BlockType.builder(WHEN_PRESSED, BlockCategory.EVENTS, "When %s is pressed %s")
				.params(ParamSpec.key("button"),
						ParamSpec.choice("mode", "run once", List.of("run once", "restart", "toggle")))
				.event()
				.build());

		register(BlockType.builder(WHEN_RELEASED, BlockCategory.EVENTS, "When %s is released")
				.params(ParamSpec.key("button"))
				.event()
				.build());

		register(BlockType.builder(WHILE_HELD, BlockCategory.EVENTS, "While %s is held")
				.params(ParamSpec.key("button"))
				.event()
				.build());

		register(BlockType.builder(WHEN_ENABLED, BlockCategory.EVENTS, "When this macro is enabled")
				.event()
				.build());

		register(BlockType.builder(EVERY, BlockCategory.EVENTS, "Every %s seconds")
				.params(ParamSpec.number("seconds", "5"))
				.event()
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

		register(BlockType.builder(WAIT_UNTIL, BlockCategory.CONTROL, "wait until %s")
				.params(ParamSpec.booleanSlot("condition"))
				.build());

		register(BlockType.builder(WAIT_NEXT_PRESS, BlockCategory.CONTROL, "wait for next press of %s")
				.params(ParamSpec.keySlot("button"))
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

	// --- Player state ---------------------------------------------------

	/**
	 * Readings off the player, as reporters that plug into any slot: what a
	 * script needs to decide whether to eat, to swap tools, or to stop.
	 *
	 * <p>They report {@code None} whenever there is no player to read — the
	 * editor is reachable from the title screen — rather than a zero, which a
	 * script would take for a genuine reading of empty.
	 */
	private static void registerPlayerState() {
		register(BlockType.builder("player.health", BlockCategory.VARIABLES, "health")
				.reporter(ofPlayer(player -> rounded(player.getHealth())))
				.build());

		register(BlockType.builder("player.food_level", BlockCategory.VARIABLES, "foodLevel")
				.reporter(ofPlayer(player -> (double) player.getFoodData().getFoodLevel()))
				.build());

		register(BlockType.builder("player.saturation_level", BlockCategory.VARIABLES, "saturationLevel")
				.reporter(ofPlayer(player -> rounded(player.getFoodData().getSaturationLevel())))
				.build());

		// An empty hand is a reading in its own right, so these report empty text
		// and a count of zero rather than None.
		register(BlockType.builder("player.selected_item_name", BlockCategory.VARIABLES, "selectedItemName")
				.reporter(ofHeldItem(stack -> stack.isEmpty() ? "" : stack.getHoverName().getString()))
				.build());

		register(BlockType.builder("player.selected_item_qty", BlockCategory.VARIABLES, "selectedItemQty")
				.reporter(ofHeldItem(stack -> (double) stack.getCount()))
				.build());

		// Uses left, not damage taken, so "durability < 20" reads as the warning it
		// looks like. Anything that cannot wear out has no reading at all, which
		// keeps a stack of dirt from looking like a tool about to break.
		register(BlockType.builder("player.selected_item_durability", BlockCategory.VARIABLES,
						"selectedItemDurability")
				.reporter(ofHeldItem(stack -> stack.isDamageableItem()
						? (double) (stack.getMaxDamage() - stack.getDamageValue())
						: Values.NONE))
				.build());

		// Counted 1-9 as the hotbar keys are, not 0-8 as the inventory holds it, so
		// the number this reports is the one buttonDown takes to select that slot.
		register(BlockType.builder("player.selected_item_slot", BlockCategory.VARIABLES, "selectedItemSlot")
				.reporter(ofPlayer(player -> (double) (player.getInventory().getSelectedSlot() + 1)))
				.build());
	}

	private static void registerInputPredicates() {
		register(BlockType.builder("input.is_held", BlockCategory.INPUT, "%s is held")
				.params(ParamSpec.keySlot("button"))
				.predicate((block, ctx) -> ctx.inputHeld(Values.display(ctx.resolve(block, "button"))))
				.build());

		register(BlockType.builder("input.was_pressed", BlockCategory.INPUT, "%s was pressed this tick")
				.params(ParamSpec.keySlot("button"))
				.predicate((block, ctx) -> ctx.inputPressed(Values.display(ctx.resolve(block, "button"))))
				.build());

		register(BlockType.builder("input.was_released", BlockCategory.INPUT, "%s was released this tick")
				.params(ParamSpec.keySlot("button"))
				.predicate((block, ctx) -> ctx.inputReleased(Values.display(ctx.resolve(block, "button"))))
				.build());
	}

	/** Wraps a reading of the player so that having no player reports None. */
	private static BlockType.Evaluator ofPlayer(Function<LocalPlayer, Object> reading) {
		return (block, ctx) -> {
			LocalPlayer player = ctx.player();
			return player == null ? Values.NONE : reading.apply(player);
		};
	}

	/** As {@link #ofPlayer}, for a reading of whatever is in the main hand. */
	private static BlockType.Evaluator ofHeldItem(Function<ItemStack, Object> reading) {
		return ofPlayer(player -> reading.apply(player.getInventory().getSelectedItem()));
	}

	/**
	 * Health and saturation are floats, and a float widened to a double carries
	 * noise the player never sees on their own hunger bar — 6.6 arriving as
	 * 6.599999904632568. Two places is finer than either is ever displayed.
	 */
	private static Double rounded(float value) {
		return Math.round(value * 100.0) / 100.0;
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
