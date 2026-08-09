package com.minimacros.gui;

/**
 * Shared colours and metrics, so the panels all look like one editor.
 *
 * <p>The chrome itself is drawn from vanilla's own textures by {@link Panels};
 * what is left here are the colours vanilla writes text in, and the handful of
 * block-editor colours it has no equivalent for.
 */
public final class Theme {
	/** Vanilla's own text colours, so labels read like the rest of the game. */
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_MUTED = 0xFFA0A0A0;
	public static final int TEXT_PROMPT = 0xFFFFFF55;
	public static final int TEXT_ON_BLOCK = 0xFFFFFFFF;

	/** A white frame around the row the cursor is on. */
	public static final int ROW_HOVER_OUTLINE = 0xFFFFFFFF;
	/** A pale wash marking the row that is selected, read through by its text. */
	public static final int ROW_SELECTED = 0x55FFFFFF;

	// The category picker keeps its own flat panel rather than vanilla's furniture.
	public static final int MENU_BACKGROUND = 0xFF1B1B1F;
	public static final int MENU_BORDER = 0xFF3A3A42;
	/** Border of the picker's list while it is open. */
	public static final int MENU_BORDER_OPEN = 0xFF7A7AFF;
	public static final int MENU_ROW_HOVER = 0xFF2A2A32;

	/** Every block is outlined in the same dark grey, whatever its category colour. */
	public static final int BLOCK_BORDER = 0xFF2A2A30;
	/** Hover highlight: solid white, traced around the block's own silhouette. */
	public static final int BLOCK_HOVER = 0xFFFFFFFF;
	public static final int DROP_INDICATOR = 0xFFFFFFFF;

	public static final int PARAM_FILL = 0xFF56565E;
	public static final int PARAM_TEXT = 0xFFFFFFFF;
	/** Ring around the field currently being typed into. */
	public static final int PARAM_FOCUSED = 0xFFFFFFFF;

	public static final int PADDING = 6;

	private Theme() {
	}
}
