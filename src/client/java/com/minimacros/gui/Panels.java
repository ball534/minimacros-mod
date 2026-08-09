package com.minimacros.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The vanilla furniture the editor sits in: the tiled list panels, the strips
 * that separate them, the scrollbars and the popup backdrop.
 *
 * <p>These are the same textures and the same measurements Minecraft's own
 * options screens use, drawn the same way, so the editor reads as part of the
 * game rather than as a window pasted over it. Minecraft picks a different set
 * of textures in a world than it does at the title screen; every helper here
 * asks the same question and follows the same answer.
 */
public final class Panels {
	/** Vanilla's scrollbar width, reserved down the right edge of a panel. */
	public static final int SCROLLBAR_WIDTH = 6;
	/** The separator strips sit outside the panel, above it and below it. */
	public static final int SEPARATOR_HEIGHT = 2;
	/** Height of a vanilla button, and of every control on the toolbars. */
	public static final int WIDGET_HEIGHT = 20;

	private static final Identifier MENU_LIST_BACKGROUND =
			Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");
	private static final Identifier INWORLD_MENU_LIST_BACKGROUND =
			Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");

	private static final Identifier POPUP_BACKGROUND = Identifier.withDefaultNamespace("popup/background");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
	private static final Identifier SCROLLER_BACKGROUND =
			Identifier.withDefaultNamespace("widget/scroller_background");
	private static final Identifier CHECKBOX = Identifier.withDefaultNamespace("widget/checkbox");
	private static final Identifier CHECKBOX_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
	private static final Identifier CHECKBOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");
	private static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED =
			Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");

	private Panels() {
	}

	private static boolean inWorld() {
		return Minecraft.getInstance().level != null;
	}

	/** A list panel with the strips vanilla draws above and below it. */
	public static void list(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double scroll) {
		background(graphics, x, y, width, height, scroll);
		separators(graphics, x, y, width, height);
	}

	/**
	 * The tiled panel itself. The texture is anchored to the panel's far corner
	 * and pushed along by the scroll, which is what stops it sliding under the
	 * rows as they move.
	 */
	public static void background(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double scroll) {
		graphics.blit(RenderPipelines.GUI_TEXTURED,
				inWorld() ? INWORLD_MENU_LIST_BACKGROUND : MENU_LIST_BACKGROUND,
				x, y, x + width, (float) (y + height + scroll), width, height, 32, 32);
	}

	public static void separators(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		horizontalSeparator(graphics, x, y - SEPARATOR_HEIGHT, width, true);
		horizontalSeparator(graphics, x, y + height, width, false);
	}

	/** One strip on its own, for the screen's header and footer. */
	public static void horizontalSeparator(GuiGraphicsExtractor graphics, int x, int y, int width, boolean header) {
		Identifier sprite;
		if (header) {
			sprite = inWorld() ? Screen.INWORLD_HEADER_SEPARATOR : Screen.HEADER_SEPARATOR;
		} else {
			sprite = inWorld() ? Screen.INWORLD_FOOTER_SEPARATOR : Screen.FOOTER_SEPARATOR;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, sprite, x, y, 0.0F, 0.0F, width, SEPARATOR_HEIGHT, 32, 2);
	}

	/**
	 * The track and thumb of a scrollbar. {@link Scrollbar} works out where the
	 * thumb goes and is what actually calls this.
	 */
	public static void scrollbar(GuiGraphicsExtractor graphics, int barX, int trackY, int trackHeight,
			int thumbY, int thumbHeight) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND,
				barX, trackY, SCROLLBAR_WIDTH, trackHeight);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER,
				barX, thumbY, SCROLLBAR_WIDTH, thumbHeight);
	}

	/** The backdrop vanilla puts behind popups, used here for the dropdowns. */
	public static void popup(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, POPUP_BACKGROUND, x, y, width, height);
	}

	public static void checkbox(GuiGraphicsExtractor graphics, int x, int y, int size,
			boolean checked, boolean hovered) {
		Identifier sprite;
		if (checked) {
			sprite = hovered ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED;
		} else {
			sprite = hovered ? CHECKBOX_HIGHLIGHTED : CHECKBOX;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size);
	}
}
