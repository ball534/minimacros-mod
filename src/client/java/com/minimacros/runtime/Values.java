package com.minimacros.runtime;


/**
 * The value system behind slots and operations.
 *
 * <p>Values are plain Java objects: {@link Boolean}, {@link Double},
 * {@link String}, or {@link #NONE}. Operators follow Python where it is
 * reasonable to — {@code +} concatenates strings, {@code *} repeats them,
 * dividing by zero yields {@code None} instead of throwing.
 */
public final class Values {
	/** The {@code None} literal. */
	public static final Object NONE = new Object() {
		@Override
		public String toString() {
			return "None";
		}
	};

	public static final String TRUE_LITERAL = "True";
	public static final String FALSE_LITERAL = "False";
	public static final String NONE_LITERAL = "None";

	private Values() {
	}

	/** Reads a typed literal the way the user would expect it to read. */
	public static Object parse(String text) {
		if (text == null) {
			return NONE;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		switch (trimmed) {
			case TRUE_LITERAL -> {
				return Boolean.TRUE;
			}
			case FALSE_LITERAL -> {
				return Boolean.FALSE;
			}
			case NONE_LITERAL -> {
				return NONE;
			}
			default -> {
				try {
					return Double.valueOf(trimmed);
				} catch (NumberFormatException e) {
					return trimmed;
				}
			}
		}
	}

	/** Python-ish truthiness: empty, zero, False and None are all false. */
	public static boolean truthy(Object value) {
		if (value == null || value == NONE) {
			return false;
		}
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof Double number) {
			return number != 0;
		}
		if (value instanceof String text) {
			return !text.isEmpty();
		}
		return true;
	}

	/** How a value appears in chat, an action bar, or a filled-in slot. */
	public static String display(Object value) {
		if (value == null || value == NONE) {
			return NONE_LITERAL;
		}
		if (value instanceof Boolean bool) {
			return bool ? TRUE_LITERAL : FALSE_LITERAL;
		}
		if (value instanceof Double number) {
			if (number == Math.floor(number) && !number.isInfinite()) {
				return Long.toString(number.longValue());
			}
			return number.toString();
		}
		return value.toString();
	}

	public static double toNumber(Object value) {
		if (value instanceof Double number) {
			return number;
		}
		if (value instanceof Boolean bool) {
			return bool ? 1 : 0;
		}
		if (value instanceof String text) {
			try {
				return Double.parseDouble(text.trim());
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		return 0;
	}

	public static int toInt(Object value) {
		return (int) Math.round(toNumber(value));
	}

	/**
	 * Applies one operator. Assignment is not here — it is its own block, which
	 * needs the name on its left rather than the value.
	 */
	public static Object apply(String operator, Object a, Object b) {
		return switch (operator) {
			case "+" -> add(a, b);
			case "-" -> toNumber(a) - toNumber(b);
			case "*" -> multiply(a, b);
			case "/" -> divide(a, b, false);
			case "%" -> divide(a, b, true);
			case "**" -> Math.pow(toNumber(a), toNumber(b));
			case "==" -> areEqual(a, b);
			case "!=" -> !areEqual(a, b);
			case "<" -> compare(a, b) < 0;
			case "<=" -> compare(a, b) <= 0;
			case ">" -> compare(a, b) > 0;
			case ">=" -> compare(a, b) >= 0;
			case "and" -> truthy(a) && truthy(b);
			case "or" -> truthy(a) || truthy(b);
			default -> NONE;
		};
	}

	private static Object add(Object a, Object b) {
		if (a instanceof String || b instanceof String) {
			return display(a) + display(b);
		}
		return toNumber(a) + toNumber(b);
	}

	private static Object multiply(Object a, Object b) {
		if (a instanceof String text) {
			return text.repeat(Math.max(0, toInt(b)));
		}
		if (b instanceof String text) {
			return text.repeat(Math.max(0, toInt(a)));
		}
		return toNumber(a) * toNumber(b);
	}

	/** Division by zero yields None rather than crashing the macro. */
	private static Object divide(Object a, Object b, boolean remainder) {
		double divisor = toNumber(b);
		if (divisor == 0) {
			return NONE;
		}
		return remainder ? toNumber(a) % divisor : toNumber(a) / divisor;
	}

	private static boolean areEqual(Object a, Object b) {
		if (a == NONE || b == NONE) {
			return a == b;
		}
		if (a instanceof String || b instanceof String) {
			return display(a).equals(display(b));
		}
		return toNumber(a) == toNumber(b);
	}

	private static int compare(Object a, Object b) {
		if (a instanceof String left && b instanceof String right) {
			return left.compareTo(right);
		}
		return Double.compare(toNumber(a), toNumber(b));
	}

}
