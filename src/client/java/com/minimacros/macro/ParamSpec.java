package com.minimacros.macro;

import java.util.List;
import java.util.Objects;

/**
 * One editable slot inside a block, rendered as an inline field.
 *
 * <p>{@link #kind} is what the param is; {@link #literalKind} is what may be
 * typed into it. They differ for slots, which are all {@code SLOT} but each
 * accept a different sort of literal when no reporter is plugged in.
 */
public record ParamSpec(String id, Kind kind, String defaultValue, int minWidth, Kind literalKind,
		List<String> choices) {

	public ParamSpec(String id, Kind kind, String defaultValue, int minWidth, Kind literalKind) {
		this(id, kind, defaultValue, minWidth, literalKind, List.of());
	}

	public ParamSpec {
		choices = List.copyOf(Objects.requireNonNull(choices));
	}

	public enum Kind {
		/** Whole number. */
		INT,
		/** Number that may carry a sign and a decimal point. */
		NUMBER,
		/** Free text. */
		STRING,
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
		BOOLEAN,
		/** A value selected from a fixed list. */
		CHOICE
	}

	/**
	 * Blocks start empty and their boxes grow with what is typed into them, so
	 * these are only wide enough to stay clickable while blank.
	 */
	private static final int EMPTY_WIDTH = 12;
	/** Wider than the rest: the hexagon's points eat into its usable width. */
	private static final int EMPTY_BOOLEAN_WIDTH = 22;

	public static ParamSpec string(String id) {
		return new ParamSpec(id, Kind.STRING, "", EMPTY_WIDTH, Kind.STRING);
	}

	/** A key captured as a direct literal rather than a reporter-capable slot. */
	public static ParamSpec key(String id) {
		return new ParamSpec(id, Kind.KEY, "", EMPTY_WIDTH, Kind.KEY);
	}

	/** A direct number literal with an explicit default. */
	public static ParamSpec number(String id, String defaultValue) {
		return new ParamSpec(id, Kind.NUMBER, defaultValue, EMPTY_WIDTH, Kind.NUMBER);
	}

	/** A slot whose typed literal is free text. */
	public static ParamSpec slot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", EMPTY_WIDTH, Kind.STRING);
	}

	/** A slot that still takes a reporter, but only accepts digits when typed into. */
	public static ParamSpec intSlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", EMPTY_WIDTH, Kind.INT);
	}

	/** As {@link #intSlot}, but a decimal point and a sign are allowed too. */
	public static ParamSpec numberSlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", EMPTY_WIDTH, Kind.NUMBER);
	}

	/** A slot filled by pressing a key; still accepts a reporter holding a key name. */
	public static ParamSpec keySlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", EMPTY_WIDTH, Kind.KEY);
	}

	/** A condition: a hexagonal hole that only a boolean block fits. */
	public static ParamSpec booleanSlot(String id) {
		return new ParamSpec(id, Kind.SLOT, "", EMPTY_BOOLEAN_WIDTH, Kind.BOOLEAN);
	}

	/** A dropdown with a fixed set of string values. */
	public static ParamSpec choice(String id, String defaultValue, List<String> choices) {
		return new ParamSpec(id, Kind.CHOICE, defaultValue, EMPTY_WIDTH, Kind.CHOICE, choices);
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
