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
	public static final int TEXT_ON_BLOCK = 0xFFEDF0E8;

	/** Workbench colours, scoped to the library, canvas and blocks. */
	public static final int WORKSPACE_BACKGROUND = 0xFF171A1F;
	public static final int WORKSPACE_GRID = 0xFF292E36;
	public static final int LIBRARY_BACKGROUND = 0xFF22262D;
	public static final int WORKSPACE_BORDER = 0xFF39414C;
	public static final int WORKSPACE_TEXT = 0xFFEDF0E8;
	public static final int WORKSPACE_MUTED = 0xFFA5ADB8;
	public static final int SLOT_BORDER = 0xFF65707F;

	/** A white frame around the row the cursor is on. */
	public static final int ROW_HOVER_OUTLINE = 0xFFFFFFFF;
	/** A pale wash marking the row that is selected, read through by its text. */
	public static final int ROW_SELECTED = 0x55FFFFFF;

	/** Every block is outlined in the same dark grey, whatever its category colour. */
	public static final int BLOCK_BORDER = 0xFF111419;
	/** Hover highlight: solid white, traced around the block's own silhouette. */
	public static final int BLOCK_HOVER = 0xFFFFFFFF;
	public static final int DROP_INDICATOR = 0xFF74C7B2;

	public static final int PARAM_FILL = 0xFF171A1F;
	public static final int PARAM_TEXT = 0xFFEDF0E8;
	/** Ring around the field currently being typed into. */
	public static final int PARAM_FOCUSED = 0xFFFFFFFF;

	private Theme() {
	}
}
