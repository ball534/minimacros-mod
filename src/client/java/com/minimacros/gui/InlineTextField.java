package com.minimacros.gui;

import com.minimacros.macro.ParamSpec;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * A minimal text editor that lives inside a block.
 *
 * <p>Deliberately not {@link net.minecraft.client.gui.components.EditBox}: that
 * widget draws and hit-tests in screen coordinates, which fights the canvas's
 * pan offset and scissor rectangle. This one holds nothing but text and a
 * cursor, and the block renderer draws it wherever the param happens to be.
 */
public final class InlineTextField {
	private static final int MAX_LENGTH = 256;
	private static final long CARET_BLINK_MS = 500;

	private final ParamSpec spec;
	private String value;
	private int cursor;
	private long startedAt = System.currentTimeMillis();

	public InlineTextField(ParamSpec spec, String initial) {
		this.spec = spec;
		this.value = initial == null ? "" : initial;
		this.cursor = this.value.length();
	}

	public String value() {
		return value;
	}

	public int cursor() {
		return cursor;
	}

	public boolean caretVisible() {
		return ((System.currentTimeMillis() - startedAt) / CARET_BLINK_MS) % 2 == 0;
	}

	public boolean charTyped(CharacterEvent event) {
		char typed = (char) event.codepoint();
		if (!accepts(typed)) {
			return false;
		}
		if (value.length() >= MAX_LENGTH) {
			return true;
		}
		value = value.substring(0, cursor) + typed + value.substring(cursor);
		cursor++;
		resetBlink();
		return true;
	}

	/** @return true if the key was consumed by the field. */
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case InputConstants.KEY_BACKSPACE -> {
				if (cursor > 0) {
					value = value.substring(0, cursor - 1) + value.substring(cursor);
					cursor--;
				}
				resetBlink();
				return true;
			}
			case InputConstants.KEY_DELETE -> {
				if (cursor < value.length()) {
					value = value.substring(0, cursor) + value.substring(cursor + 1);
				}
				resetBlink();
				return true;
			}
			case InputConstants.KEY_LEFT -> {
				cursor = Math.max(0, cursor - 1);
				resetBlink();
				return true;
			}
			case InputConstants.KEY_RIGHT -> {
				cursor = Math.min(value.length(), cursor + 1);
				resetBlink();
				return true;
			}
			case InputConstants.KEY_HOME -> {
				cursor = 0;
				resetBlink();
				return true;
			}
			case InputConstants.KEY_END -> {
				cursor = value.length();
				resetBlink();
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/**
	 * Numeric params reject anything that could not parse. Slots are checked by
	 * their literal kind, not by {@code SLOT} itself — otherwise every slot would
	 * silently accept text that resolves to nothing at run time.
	 */
	private boolean accepts(char typed) {
		if (typed < ' ' || typed == 127) {
			return false;
		}
		return switch (spec.literalKind()) {
			case INT -> Character.isDigit(typed) || (typed == '-' && cursor == 0);
			case NUMBER -> Character.isDigit(typed) || typed == '.' || (typed == '-' && cursor == 0);
			default -> true;
		};
	}

	private void resetBlink() {
		startedAt = System.currentTimeMillis();
	}
}
