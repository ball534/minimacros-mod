package com.minimacros.macro;

/**
 * Palette groupings. Each carries its own colour so a category is recognisable
 * at a glance before the label is even read.
 */
public enum BlockCategory {
	EVENTS("Events", 0xFF594638, 0xFFD19A68),
	INPUT("Input", 0xFF344D6A, 0xFF82AFE5),
	CONTROL("Control", 0xFF604921, 0xFFD6AD5C),
	MESSAGES("Messages", 0xFF304B47, 0xFF74C7B2),
	// Amethyst values, moss operators and rose conditions remain distinct when nested.
	VARIABLES("Variables", 0xFF493956, 0xFFB49AD8),
	OPERATORS("Operators", 0xFF314D3C, 0xFF8FBD83),
	COMPARATORS("Comparators", 0xFF603B4C, 0xFFCF8AA8);

	private final String displayName;
	private final int color;
	private final int accent;

	BlockCategory(String displayName, int color, int accent) {
		this.displayName = displayName;
		this.color = color;
		this.accent = accent;
	}

	public String displayName() {
		return displayName;
	}

	public int color() {
		return color;
	}

	public int accent() {
		return accent;
	}
}
