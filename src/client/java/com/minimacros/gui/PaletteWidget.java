package com.minimacros.gui;

import com.minimacros.macro.BlockCategory;
import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.BlockType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable workbench library. All text uses the screen's Minecraft font. */
public final class PaletteWidget {
	private static final int INSET = 8;
	private static final int PICKER_HEIGHT = 20;
	private static final int OPTION_HEIGHT = 18;
	private static final int ENTRY_GAP = 10;
	private static final BlockCategory[] CATEGORIES = BlockCategory.values();
	private final Font font;
	private final EditBox searchField;
	private final Scrollbar scrollbar = new Scrollbar();
	private final List<Entry> entries = new ArrayList<>();
	private int x;
	private int y;
	private int width;
	private int height;
	private BlockCategory category;
	private boolean categoryMenuOpen;
	private int firstOption;
	private boolean layoutDirty = true;
	private int contentHeight;
	private int appliedScroll;

	private record Entry(BlockType type, BlockLayout.Layout layout, int height) {}

	public PaletteWidget(Font font) {
		this.font = font;
		searchField = new EditBox(font, 0, 0, 100, 20, Component.literal("Search blocks"));
		searchField.setHint(Component.literal("Search blocks..."));
		searchField.setBordered(false);
		searchField.setTextColor(Theme.WORKSPACE_TEXT);
		searchField.setResponder(text -> {
			scrollbar.reset();
			layoutDirty = true;
		});
	}

	public EditBox searchField() { return searchField; }

	public void setBounds(int x, int y, int width, int height) {
		if (this.x != x || this.y != y || this.width != width || this.height != height) layoutDirty = true;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		categoryMenuOpen = false;
		searchField.setPosition(x + INSET + 5, y + INSET + 6);
		searchField.setWidth(Math.max(1, width - INSET * 2 - 10));
		searchField.setHeight(font.lineHeight + 2);
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private int pickerY() { return y + INSET + 26; }
	private String categoryLabel(int index) { return index == 0 ? "All blocks" : CATEGORIES[index - 1].displayName(); }
	public boolean categoryMenuOpen() { return categoryMenuOpen; }
	public void closeCategoryMenu() { categoryMenuOpen = false; }

	private int shelfTop() {
		return pickerY() + PICKER_HEIGHT + 8;
	}

	private int menuRows() {
		return Math.clamp((y + height - INSET - menuTop() - 2) / OPTION_HEIGHT, 0, CATEGORIES.length + 1);
	}

	private int menuTop() { return pickerY() + PICKER_HEIGHT; }

	private int shelfHeight() { return Math.max(0, y + height - INSET - shelfTop()); }

	private void rebuild() {
		if (layoutDirty) rebuildEntries();
		scrollbar.setTrack(x + INSET, shelfTop(), Math.max(0, width - INSET * 2), shelfHeight(), contentHeight);
		int scroll = (int) scrollbar.amount();
		int delta = appliedScroll - scroll;
		if (delta != 0) {
			for (Entry entry : entries) {
				for (BlockLayout.Node node : entry.layout().nodes) node.translate(0, delta);
			}
		}
		appliedScroll = scroll;
	}

	private void rebuildEntries() {
		entries.clear();
		String query = searchField.getValue().strip().toLowerCase(Locale.ROOT);
		int cursorY = shelfTop() + ENTRY_GAP;
		for (BlockType type : BlockRegistry.all()) {
			if (category != null && type.category() != category) continue;
			String searchable = type.label() + " " + type.id() + " " + type.category().displayName();
			if (!searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
			BlockLayout.Layout layout = BlockLayout.compute(List.of(BlockInstance.create(type)), x + INSET, cursorY, font);
			int entryHeight = layout.nodes.getFirst().height;
			entries.add(new Entry(type, layout, entryHeight));
			cursorY += entryHeight + ENTRY_GAP;
		}
		contentHeight = cursorY - shelfTop();
		appliedScroll = 0;
		layoutDirty = false;
	}

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		rebuild();
		graphics.fill(x, y, x + width, y + height, Theme.LIBRARY_BACKGROUND);
		graphics.outline(x, y, width, height, Theme.WORKSPACE_BORDER);
		graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
		graphics.fill(x + INSET, y + INSET, x + width - INSET, y + INSET + 20, Theme.PARAM_FILL);
		graphics.outline(x + INSET, y + INSET, width - INSET * 2, 20,
				searchField.isFocused() ? Theme.PARAM_FOCUSED : Theme.WORKSPACE_BORDER);
		graphics.fill(x + INSET, pickerY(), x + width - INSET, pickerY() + PICKER_HEIGHT, Theme.PARAM_FILL);
		graphics.outline(x + INSET, pickerY(), width - INSET * 2, PICKER_HEIGHT,
				categoryMenuOpen ? Theme.DROP_INDICATOR : Theme.WORKSPACE_BORDER);
		int selectedIndex = category == null ? 0 : category.ordinal() + 1;
		graphics.text(font, font.plainSubstrByWidth(categoryLabel(selectedIndex), Math.max(1, width - INSET * 2 - 24)),
				x + INSET + 5, BlockLayout.textTop(pickerY(), PICKER_HEIGHT, font), Theme.WORKSPACE_TEXT);
		graphics.text(font, categoryMenuOpen ? "^" : "v", x + width - INSET - 12,
				BlockLayout.textTop(pickerY(), PICKER_HEIGHT, font), Theme.WORKSPACE_MUTED);
		graphics.fill(x + INSET, shelfTop() - 4, x + width - INSET, shelfTop() - 3, Theme.WORKSPACE_BORDER);
		graphics.disableScissor();
		if (shelfHeight() <= 0) return;
		graphics.enableScissor(x + INSET, shelfTop(), x + width - INSET - Panels.SCROLLBAR_WIDTH, y + height - INSET);
		for (Entry entry : entries) {
			BlockLayout.Node node = entry.layout().nodes.getFirst();
			if (node.y + entry.height() <= shelfTop() || node.y >= y + height - INSET) continue;
			BlockLayout.Node hovered = !categoryMenuOpen && overShelf(mouseX, mouseY)
					? BlockLayout.hitTest(entry.layout().nodes, mouseX, mouseY) : null;
			BlockRenderer.drawStack(graphics, font, entry.layout().nodes, hovered, null);
		}
		if (entries.isEmpty()) graphics.text(font, "No matching blocks", x + INSET, shelfTop() + ENTRY_GAP, Theme.WORKSPACE_MUTED);
		graphics.disableScissor();
		scrollbar.render(graphics);
	}

	/** Render after screen widgets so the dropdown covers the shelf below it. */
	public void renderCategoryMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!categoryMenuOpen) return;
		int rows = menuRows();
		graphics.nextStratum();
		graphics.fill(x + INSET, menuTop(), x + width - INSET, menuTop() + rows * OPTION_HEIGHT + 2,
				Theme.LIBRARY_BACKGROUND);
		graphics.outline(x + INSET, menuTop(), width - INSET * 2, rows * OPTION_HEIGHT + 2, Theme.WORKSPACE_BORDER);
		for (int row = 0; row < rows; row++) {
			int index = firstOption + row;
			int top = menuTop() + 1 + row * OPTION_HEIGHT;
			boolean hovered = mouseX >= x + INSET && mouseX < x + width - INSET
					&& mouseY >= top && mouseY < top + OPTION_HEIGHT;
			if (hovered) graphics.fill(x + INSET + 1, top, x + width - INSET - 1, top + OPTION_HEIGHT, Theme.WORKSPACE_BORDER);
			BlockCategory option = index == 0 ? null : CATEGORIES[index - 1];
			int accent = option == null ? Theme.DROP_INDICATOR : option.accent();
			graphics.fill(x + INSET + 5, top + 6, x + INSET + 8, top + 11, accent);
			graphics.text(font, font.plainSubstrByWidth(categoryLabel(index), Math.max(1, width - INSET * 2 - 20)),
					x + INSET + 13, BlockLayout.textTop(top, OPTION_HEIGHT, font), category == option ? accent : Theme.WORKSPACE_TEXT);
		}
		if (rows < CATEGORIES.length + 1 && rows > 0) {
			int trackHeight = rows * OPTION_HEIGHT - 4;
			int thumbHeight = Math.max(8, trackHeight * rows / (CATEGORIES.length + 1));
			int thumbTop = menuTop() + 3 + firstOption * (trackHeight - thumbHeight) / (CATEGORIES.length + 1 - rows);
			graphics.fill(x + width - INSET - 3, menuTop() + 3, x + width - INSET - 1,
					menuTop() + 3 + trackHeight, Theme.WORKSPACE_BORDER);
			graphics.fill(x + width - INSET - 3, thumbTop, x + width - INSET - 1,
					thumbTop + thumbHeight, Theme.WORKSPACE_MUTED);
		}
	}

	private boolean overShelf(double mouseX, double mouseY) {
		return mouseX >= x + INSET && mouseX < x + width - INSET - Panels.SCROLLBAR_WIDTH
				&& mouseY >= shelfTop() && mouseY < y + height - INSET;
	}

	public boolean mouseClicked(MouseButtonEvent event) {
		if (categoryMenuOpen) {
			int row = ((int) event.y() - menuTop() - 1) / OPTION_HEIGHT;
			if (event.x() >= x + INSET && event.x() < x + width - INSET
					&& event.y() >= menuTop() + 1 && row >= 0 && row < menuRows()) {
				int index = firstOption + row;
				category = index == 0 ? null : CATEGORIES[index - 1];
				scrollbar.reset();
				layoutDirty = true;
			}
			closeCategoryMenu();
			return true;
		}
		if (!contains(event.x(), event.y())) return false;
		rebuild();
		if (event.x() >= x + INSET && event.x() < x + width - INSET
				&& event.y() >= pickerY() && event.y() < pickerY() + PICKER_HEIGHT && menuRows() > 0) {
			categoryMenuOpen = true;
			firstOption = Math.clamp(category == null ? 0 : category.ordinal() + 1, 0, CATEGORIES.length + 1 - menuRows());
			return true;
		}
		return event.y() < shelfTop() || scrollbar.mouseClicked(event.x(), event.y());
	}

	public BlockInstance blockAt(double mouseX, double mouseY, int[] grabOffsetOut) {
		if (!overShelf(mouseX, mouseY)) return null;
		rebuild();
		for (Entry entry : entries) {
			BlockLayout.Node node = entry.layout().nodes.getFirst();
			if (BlockLayout.hitTest(entry.layout().nodes, (int) mouseX, (int) mouseY) != null) {
				grabOffsetOut[0] = (int) Math.round(mouseX - node.x);
				grabOffsetOut[1] = (int) Math.round(mouseY - node.y);
				return BlockInstance.create(entry.type());
			}
		}
		return null;
	}

	public boolean isScrolling() { return scrollbar.dragging(); }
	public void mouseDragged(double mouseY) { scrollbar.mouseDragged(mouseY); }
	public void mouseReleased() { scrollbar.mouseReleased(); }
	public boolean mouseScrolled(double amount) {
		if (categoryMenuOpen) {
			firstOption = Math.clamp(firstOption - (int) Math.signum(amount), 0, CATEGORIES.length + 1 - menuRows());
			return true;
		}
		rebuild();
		scrollbar.scrollBy(amount, 24);
		return true;
	}
}
