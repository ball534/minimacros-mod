package com.minimacros.macro;

/**
 * Palette groupings. Each carries its own colour so a category is recognisable
 * at a glance before the label is even read.
 */
public enum BlockCategory {
	INPUT("Input", 0xFF4C97FF),
	CONTROL("Control", 0xFFFFAB19),
	MESSAGES("Messages", 0xFF59C059),
	// Purple rather than a second orange: a variable reporter is usually sitting
	// inside a gold control block, and the two oranges were hard to tell apart.
	VARIABLES("Variables", 0xFF9966FF),
	// Teal rather than green, which the messages blocks already use — an operator
	// is most often nested in a variable or control block, so it has to read apart
	// from purple and gold at a glance.
	OPERATORS("Operators", 0xFF2FB8A8),
	// Rose, well away from the operator teal: the two sections hold the same kind
	// of expression but their blocks fit different holes, so they must not read
	// as variants of one another.
	COMPARATORS("Comparators", 0xFFC9599E);

	private final String displayName;
	private final int color;

	BlockCategory(String displayName, int color) {
		this.displayName = displayName;
		this.color = color;
	}

	public String displayName() {
		return displayName;
	}

	public int color() {
		return color;
	}
}
