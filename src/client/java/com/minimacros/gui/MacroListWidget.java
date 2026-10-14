package com.minimacros.gui;

import com.minimacros.macro.MacroDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The saved-macro list down the left edge, laid out like one of vanilla's own
 * option lists. Each row selects a macro; the
 * checkbox on its right arms or disarms that macro without opening it.
 */
public final class MacroListWidget {
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_GAP = 2;
	/** Room either side of the rows inside the panel. */
	private static final int INSET = 4;

	private final Font font;
	private final List<MacroDefinition> source;
	private final List<MacroDefinition> visible = new ArrayList<>();

	private int x;
	private int y;
	private int width;
	private int height;
	private final Scrollbar scrollbar = new Scrollbar();
	private String filter = "";

	private MacroDefinition selected;
	private Consumer<MacroDefinition> onSelect = macro -> {
	};

	public MacroListWidget(Font font, List<MacroDefinition> source) {
		this.font = font;
		this.source = source;
		refresh();
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void setOnSelect(Consumer<MacroDefinition> onSelect) {
		this.onSelect = onSelect;
	}

	public void setFilter(String filter) {
		this.filter = filter == null ? "" : filter;
		refresh();
	}

	private void select(MacroDefinition macro) {
		this.selected = macro;
		onSelect.accept(macro);
	}

	/** Moves the highlight without reloading the editor's draft. */
	public void highlight(MacroDefinition macro) {
		this.selected = macro;
	}

	/** Re-applies the search filter; call after macros are added or renamed. */
	public void refresh() {
		visible.clear();
		String needle = filter.trim().toLowerCase(Locale.ROOT);
		for (MacroDefinition macro : source) {
			if (needle.isEmpty() || macro.name().toLowerCase(Locale.ROOT).contains(needle)) {
				visible.add(macro);
			}
		}
		if (selected != null && !visible.contains(selected)) {
			scrollbar.reset();
		}
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private int contentHeight() {
		return visible.size() * (ROW_HEIGHT + ROW_GAP) + INSET * 2;
	}

	/** Re-aims the scrollbar at the current rows; run before drawing or hit-testing. */
	private void refreshScrollbar() {
		scrollbar.setTrack(x, y, width, height, contentHeight());
	}

	/** Left and right edge of a row, clear of the panel edge and the scrollbar. */
	private int rowLeft() {
		return x + INSET + 2;
	}

	private int rowRight() {
		return x + width - INSET - Panels.SCROLLBAR_WIDTH;
	}

	private int rowTop(int index) {
		return y + INSET - (int) scrollbar.amount() + index * (ROW_HEIGHT + ROW_GAP);
	}

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		refreshScrollbar();
		Panels.list(graphics, x, y, width, height, scrollbar.amount());

		graphics.enableScissor(x, y, x + width, y + height);
		for (int i = 0; i < visible.size(); i++) {
			int rowY = rowTop(i);
			if (rowY + ROW_HEIGHT >= y && rowY <= y + height) {
				renderRow(graphics, visible.get(i), rowY, mouseX, mouseY);
			}
		}

		if (visible.isEmpty()) {
			String message = filter.isBlank() ? "No macros yet" : "No matches";
			graphics.centeredText(font, message, x + width / 2,
					y + (height - font.lineHeight) / 2, Theme.TEXT_MUTED);
		}
		graphics.disableScissor();

		scrollbar.render(graphics);
	}

	private void renderRow(GuiGraphicsExtractor graphics, MacroDefinition macro, int rowY,
			int mouseX, int mouseY) {
		int left = rowLeft();
		int right = rowRight();
		boolean hovered = contains(mouseX, mouseY)
				&& mouseX >= left && mouseX < right && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

		// The one you are pointing at is framed; the one you are working on is
		// filled. Both can be the same row, and then it gets both.
		if (macro == selected) {
			graphics.fill(left, rowY, right, rowY + ROW_HEIGHT, Theme.ROW_SELECTED);
		}
		if (hovered) {
			graphics.outline(left, rowY, right - left, ROW_HEIGHT, Theme.ROW_HOVER_OUTLINE);
		}

		int boxSize = Checkbox.getBoxSize(font);
		int boxX = right - INSET - boxSize;
		int boxY = rowY + (ROW_HEIGHT - boxSize) / 2;
		Panels.checkbox(graphics, boxX, boxY, boxSize, macro.enabled(),
				hovered && mouseX >= boxX && mouseX < boxX + boxSize);

		int textLeft = left + INSET;
		int available = boxX - textLeft - INSET;
		int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;

		graphics.text(font, font.plainSubstrByWidth(macro.name(), available), textLeft, textY, Theme.TEXT);

	}

	public boolean mouseClicked(MouseButtonEvent event) {
		refreshScrollbar();
		if (scrollbar.mouseClicked(event.x(), event.y())) {
			return true;
		}

		int left = rowLeft();
		int right = rowRight();

		for (int i = 0; i < visible.size(); i++) {
			int rowY = rowTop(i);
			if (event.y() >= rowY && event.y() < rowY + ROW_HEIGHT
					&& event.x() >= left && event.x() < right) {
				MacroDefinition macro = visible.get(i);
				int boxSize = Checkbox.getBoxSize(font);
				if (event.x() >= right - INSET - boxSize) {
					macro.setEnabled(!macro.enabled());
				} else {
					select(macro);
				}
				return true;
			}
		}
		return false;
	}

	/** True while the scrollbar is being dragged, even off the panel. */
	public boolean isScrolling() {
		return scrollbar.dragging();
	}

	public void mouseDragged(double mouseY) {
		scrollbar.mouseDragged(mouseY);
	}

	public void mouseReleased() {
		scrollbar.mouseReleased();
	}

	public boolean mouseScrolled(double amount) {
		refreshScrollbar();
		scrollbar.scrollBy(amount, 12);
		return true;
	}
}
