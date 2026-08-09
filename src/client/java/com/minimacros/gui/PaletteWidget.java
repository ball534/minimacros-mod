package com.minimacros.gui;

import com.minimacros.macro.BlockCategory;
import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.BlockType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The block store on the right of the splitter: a category picker over a
 * scrolling shelf of blocks that can be dragged onto the canvas.
 *
 * <p>The picker wears a vanilla button face and drops a vanilla popup, so it
 * behaves the way the rest of the game's pickers look like they should.
 */
public final class PaletteWidget {
	public static final int PICKER_HEIGHT = Panels.WIDGET_HEIGHT;
	/** Gap under the picker, leaving room for the shelf's top separator. */
	private static final int PICKER_GAP = 4;
	private static final int ENTRY_GAP = 6;
	private static final int OPTION_HEIGHT = 14;

	/** "All Blocks" followed by every category, in declaration order. */
	private static final List<String> OPTION_LABELS = buildOptionLabels();

	private final Font font;

	private int x;
	private int y;
	private int width;
	private int height;
	private final Scrollbar scrollbar = new Scrollbar();

	/** null means "every category". */
	private BlockCategory category;
	private boolean categoryListOpen;

	private final List<Entry> entries = new ArrayList<>();

	/** One shelf item, laid out exactly as the block will be on the canvas. */
	private record Entry(BlockType type, BlockLayout.Layout layout, int height) {
	}

	public PaletteWidget(Font font) {
		this.font = font;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public boolean categoryListOpen() {
		return categoryListOpen;
	}

	public void closeCategoryList() {
		categoryListOpen = false;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Top of the shelf panel, below the picker and its separator. */
	private int shelfTop() {
		return y + PICKER_HEIGHT + PICKER_GAP;
	}

	private int shelfHeight() {
		return Math.max(0, y + height - shelfTop());
	}

	private String categoryLabel() {
		return category == null ? "All Blocks" : category.displayName();
	}

	/**
	 * Rebuilds the shelf against the current category, filter and scroll.
	 * Cheap enough to run every frame, which keeps hit-testing and drawing from
	 * ever disagreeing about where a block is.
	 *
	 * <p>The scrollbar is aimed first, from the shelf as it stood last time. A
	 * block's height does not depend on where it sits, so the measurement holds
	 * from one frame to the next; only the very first frame has nothing to go on,
	 * and that one has nothing to scroll either.
	 */
	private void rebuild() {
		scrollbar.setTrack(x, shelfTop(), width, shelfHeight(), contentHeight());

		entries.clear();
		int cursorY = shelfTop() + ENTRY_GAP - (int) scrollbar.amount();

		for (BlockType type : BlockRegistry.all()) {
			if (category != null && type.category() != category) {
				continue;
			}

			BlockLayout.Layout layout = BlockLayout.compute(List.of(BlockInstance.create(type)),
					x + Theme.PADDING, cursorY, font);
			int entryHeight = layout.nodes.isEmpty() ? BlockLayout.HEADER_HEIGHT : layout.nodes.getFirst().height;
			entries.add(new Entry(type, layout, entryHeight));
			cursorY += entryHeight + ENTRY_GAP;
		}
	}

	private int contentHeight() {
		int total = ENTRY_GAP;
		for (Entry entry : entries) {
			total += entry.height() + ENTRY_GAP;
		}
		return total;
	}

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		rebuild();

		boolean overPicker = mouseX >= x && mouseX < x + width
				&& mouseY >= y && mouseY < y + PICKER_HEIGHT;
		graphics.fill(x, y, x + width, y + PICKER_HEIGHT,
				overPicker ? Theme.MENU_ROW_HOVER : Theme.MENU_BACKGROUND);
		graphics.outline(x, y, width, PICKER_HEIGHT, Theme.MENU_BORDER);
		graphics.centeredText(font, categoryLabel(), x + width / 2,
				y + (PICKER_HEIGHT - font.lineHeight) / 2, Theme.TEXT);
		graphics.text(font, categoryListOpen ? "^" : "v", x + width - 12,
				y + (PICKER_HEIGHT - font.lineHeight) / 2, Theme.TEXT_MUTED);

		Panels.list(graphics, x, shelfTop(), width, shelfHeight(), scrollbar.amount());

		graphics.enableScissor(x, shelfTop(), x + width, y + height);
		for (Entry entry : entries) {
			BlockRenderer.drawStack(graphics, font, entry.layout().nodes, null, null);
		}
		graphics.disableScissor();

		scrollbar.render(graphics);
	}

	/** Drawn after everything else so the open list sits above the shelf. */
	public void renderCategoryList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!categoryListOpen) {
			return;
		}
		graphics.nextStratum();

		List<String> options = optionLabels();
		int top = y + PICKER_HEIGHT;
		int listHeight = options.size() * OPTION_HEIGHT + 2;

		graphics.fill(x, top, x + width, top + listHeight, Theme.MENU_BACKGROUND);
		graphics.outline(x, top, width, listHeight, Theme.MENU_BORDER_OPEN);

		for (int i = 0; i < options.size(); i++) {
			int optionY = optionTop(i);
			boolean hovered = mouseX >= x && mouseX < x + width
					&& mouseY >= optionY && mouseY < optionY + OPTION_HEIGHT;
			if (hovered) {
				graphics.fill(x + 1, optionY, x + width - 1, optionY + OPTION_HEIGHT, Theme.MENU_ROW_HOVER);
			}
			boolean current = (i == 0 && category == null)
					|| (i > 0 && BlockCategory.values()[i - 1] == category);
			graphics.text(font, options.get(i), x + Theme.PADDING,
					optionY + (OPTION_HEIGHT - font.lineHeight) / 2,
					current ? Theme.TEXT_PROMPT : Theme.TEXT);
		}
	}

	private int optionTop(int index) {
		return y + PICKER_HEIGHT + 1 + index * OPTION_HEIGHT;
	}

	/** Built once: the categories are fixed, and this is read every frame. */
	private static List<String> optionLabels() {
		return OPTION_LABELS;
	}

	private static List<String> buildOptionLabels() {
		List<String> options = new ArrayList<>();
		options.add("All Blocks");
		for (BlockCategory value : BlockCategory.values()) {
			options.add(value.displayName());
		}
		return List.copyOf(options);
	}

	/** @return true if the click was consumed. */
	public boolean mouseClicked(MouseButtonEvent event) {
		rebuild();

		if (categoryListOpen && handleCategoryListClick(event)) {
			return true;
		}

		if (event.y() >= y && event.y() < y + PICKER_HEIGHT) {
			categoryListOpen = !categoryListOpen;
			return true;
		}

		categoryListOpen = false;
		return scrollbar.mouseClicked(event.x(), event.y());
	}

	private boolean handleCategoryListClick(MouseButtonEvent event) {
		List<String> options = optionLabels();

		for (int i = 0; i < options.size(); i++) {
			int optionY = optionTop(i);
			if (event.x() >= x && event.x() < x + width
					&& event.y() >= optionY && event.y() < optionY + OPTION_HEIGHT) {
				category = i == 0 ? null : BlockCategory.values()[i - 1];
				scrollbar.reset();
				categoryListOpen = false;
				return true;
			}
		}
		categoryListOpen = false;
		return true;
	}

	/**
	 * The block under the cursor on the shelf, as a fresh instance ready to drag.
	 *
	 * @return null if the cursor is not over a block.
	 */
	public BlockInstance blockAt(double mouseX, double mouseY, int[] grabOffsetOut) {
		if (mouseY < shelfTop()) {
			return null;
		}
		rebuild();

		for (Entry entry : entries) {
			BlockLayout.Node node = entry.layout().nodes.isEmpty() ? null : entry.layout().nodes.getFirst();
			if (node == null) {
				continue;
			}
			if (mouseX >= node.x && mouseX < node.x + node.headerWidth
					&& mouseY >= node.y && mouseY < node.y + entry.height()) {
				grabOffsetOut[0] = (int) Math.round(mouseX - node.x);
				grabOffsetOut[1] = (int) Math.round(mouseY - node.y);
				return BlockInstance.create(entry.type());
			}
		}
		return null;
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
		rebuild();
		scrollbar.scrollBy(amount, 14);
		return true;
	}
}
