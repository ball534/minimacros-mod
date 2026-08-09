package com.minimacros.macro;

import java.util.List;

/**
 * One editable slot inside a block. Text-ish kinds render as an inline field,
 * DROPDOWN renders as a click-to-open list.
 *
 * <p>{@link #kind} is what the param is; {@link #literalKind} is what may be
 * typed into it. They differ for slots, which are all {@code SLOT} but each
 * accept a different sort of literal when no reporter is plugged in.
 */
public record ParamSpec(String id, Kind kind, String defaultValue, List<String> options,
                        int minWidth, Kind literalKind) {

	public enum Kind {
		/** Whole number. */
		INT,
		/** Number that may carry a sign and a decimal point. */
		NUMBER,
		/** Free text. */
		STRING,
		/** Pick one of {@link #options}. */
		DROPDOWN,
		/**
		 * Accepts either a typed literal or a reporter block dropped into it.
		 * When a block is plugged in, {@link BlockInstance#input} holds it and
		 * the typed text is ignored.
		 */
		SLOT,
		/**
		 * Filled by pressing the key rather than by typing its name, so
		 * "space" and "left.shift" are recorded exactly as
		 * {@link com.minimacros.runtime.InputSimulator#resolveKey} spells them.
		 */
		KEY,
		/**
		 * A hexagonal hole. Nothing can be typed into it and only a boolean block
		 * fits, which is what stops {@code loop}'s old mistake — a comparison
		 * landing somewhere that wanted a number — from being expressible.
		 */
		BOOLEAN
	}

	/**
	 * Blocks start empty and their boxes grow with what is typed into them, so
	 * these are only wide enough to stay clickable while blank.
	 */
	private static final int EMPTY_WIDTH = 12;
	private static final int EMPTY_DROPDOWN_WIDTH = 16;
	/** Wider than the rest: the hexagon's points eat into its usable width. */
	private static final int EMPTY_BOOLEAN_WIDTH = 22;

	public static ParamSpec number(String id) {
		return new ParamSpec(id, Kind.NUMBER, "", List.of(), EMPTY_WIDTH, Kind.NUMBER);
	}

	public static ParamSpec string(String id) {
		return new ParamSpec(id, Kind.STRING, "", List.of(), EMPTY_WIDTH, Kind.STRING);
	}

	/** A slot whose typed literal is free text. */
	public static ParamSpec slot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", List.of(), EMPTY_WIDTH, Kind.STRING);
	}

	/** A slot that still takes a reporter, but only accepts digits when typed into. */
	public static ParamSpec intSlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", List.of(), EMPTY_WIDTH, Kind.INT);
	}

	/** As {@link #intSlot}, but a decimal point and a sign are allowed too. */
	public static ParamSpec numberSlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", List.of(), EMPTY_WIDTH, Kind.NUMBER);
	}

	/** A slot filled by pressing a key; still accepts a reporter holding a key name. */
	public static ParamSpec keySlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", List.of(), EMPTY_WIDTH, Kind.KEY);
	}

	/** A condition: a hexagonal hole that only a boolean block fits. */
	public static ParamSpec booleanSlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", List.of(), EMPTY_BOOLEAN_WIDTH, Kind.BOOLEAN);
	}

	public static ParamSpec dropdown(String id, List<String> options) {
		return new ParamSpec(id, Kind.DROPDOWN, "", options, EMPTY_DROPDOWN_WIDTH, Kind.DROPDOWN);
	}

	/** True when only a boolean block may be dropped here, and nothing typed. */
	public boolean acceptsOnlyBoolean() {
		return literalKind == Kind.BOOLEAN;
	}

	/** True when clicking this param should capture a keypress instead of opening a text field. */
	public boolean capturesKey() {
		return literalKind == Kind.KEY;
	}
}
