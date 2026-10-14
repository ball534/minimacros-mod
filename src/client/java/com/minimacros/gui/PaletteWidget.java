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
	private static final int CHIP_HEIGHT = 18;
	private static final int CHIP_GAP = 4;
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
		searchField.setPosition(x + INSET + 5, y + 29);
		searchField.setWidth(Math.max(1, width - INSET * 2 - 10));
		searchField.setHeight(font.lineHeight + 2);
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private int columns() { return width >= 180 ? (height < 240 ? 4 : 3) : 2; }
	private int chipWidth() { return Math.max(1, (width - INSET * 2 - CHIP_GAP * (columns() - 1)) / columns()); }
	private int chipX(int index) { return x + INSET + index % columns() * (chipWidth() + CHIP_GAP); }
	private int chipY(int index) { return y + 48 + index / columns() * (CHIP_HEIGHT + CHIP_GAP); }

	private int shelfTop() {
		int rows = (CATEGORIES.length + 1 + columns() - 1) / columns();
		return y + 48 + rows * (CHIP_HEIGHT + CHIP_GAP) + 22;
	}

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
		graphics.text(font, "BLOCK LIBRARY", x + INSET, y + 9, Theme.WORKSPACE_TEXT);
		graphics.fill(x + INSET, y + 23, x + width - INSET, y + 43, Theme.PARAM_FILL);
		graphics.outline(x + INSET, y + 23, width - INSET * 2, 20,
				searchField.isFocused() ? Theme.PARAM_FOCUSED : Theme.WORKSPACE_BORDER);
		for (int i = 0; i <= CATEGORIES.length; i++) {
			BlockCategory chipCategory = i == 0 ? null : CATEGORIES[i - 1];
			int cx = chipX(i);
			int cy = chipY(i);
			boolean selected = category == chipCategory;
			boolean hovered = mouseX >= cx && mouseX < cx + chipWidth() && mouseY >= cy && mouseY < cy + CHIP_HEIGHT;
			int fill = chipCategory == null ? Theme.WORKSPACE_BORDER : chipCategory.color();
			int accent = chipCategory == null ? Theme.DROP_INDICATOR : chipCategory.accent();
			graphics.fill(cx, cy, cx + chipWidth(), cy + CHIP_HEIGHT, fill);
			if (selected || hovered) graphics.outline(cx, cy, chipWidth(), CHIP_HEIGHT,
					selected ? accent : Theme.WORKSPACE_TEXT);
			String label = chipCategory == null ? "All" : chipCategory.displayName();
			graphics.centeredText(font, font.plainSubstrByWidth(label, Math.max(1, chipWidth() - 8)),
					cx + chipWidth() / 2, BlockLayout.textTop(cy, CHIP_HEIGHT, font), Theme.WORKSPACE_TEXT);
		}
		graphics.text(font, category == null ? "All blocks" : category.displayName(),
				x + INSET, shelfTop() - 15, Theme.WORKSPACE_MUTED);
		graphics.fill(x + INSET, shelfTop() - 4, x + width - INSET, shelfTop() - 3, Theme.WORKSPACE_BORDER);
		graphics.disableScissor();
		if (shelfHeight() <= 0) return;
		graphics.enableScissor(x + INSET, shelfTop(), x + width - INSET - Panels.SCROLLBAR_WIDTH, y + height - INSET);
		for (Entry entry : entries) {
			BlockLayout.Node node = entry.layout().nodes.getFirst();
			if (node.y + entry.height() <= shelfTop() || node.y >= y + height - INSET) continue;
			BlockLayout.Node hovered = overShelf(mouseX, mouseY)
					? BlockLayout.hitTest(entry.layout().nodes, mouseX, mouseY) : null;
			BlockRenderer.drawStack(graphics, font, entry.layout().nodes, hovered, null);
		}
		if (entries.isEmpty()) graphics.text(font, "No matching blocks", x + INSET, shelfTop() + ENTRY_GAP, Theme.WORKSPACE_MUTED);
		graphics.disableScissor();
		scrollbar.render(graphics);
	}

	private boolean overShelf(double mouseX, double mouseY) {
		return mouseX >= x + INSET && mouseX < x + width - INSET - Panels.SCROLLBAR_WIDTH
				&& mouseY >= shelfTop() && mouseY < y + height - INSET;
	}

	public boolean mouseClicked(MouseButtonEvent event) {
		if (!contains(event.x(), event.y())) return false;
		rebuild();
		for (int i = 0; i <= CATEGORIES.length; i++) {
			if (event.x() >= chipX(i) && event.x() < chipX(i) + chipWidth()
					&& event.y() >= chipY(i) && event.y() < chipY(i) + CHIP_HEIGHT) {
				category = i == 0 ? null : CATEGORIES[i - 1];
				scrollbar.reset();
				layoutDirty = true;
				return true;
			}
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
		rebuild();
		scrollbar.scrollBy(amount, 24);
		return true;
	}
}
