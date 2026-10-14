package com.minimacros.gui;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockType;
import com.minimacros.macro.ParamSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws laid-out blocks. Coordinates are whatever the caller has translated
 * into, so the same code renders the canvas, the palette and the drag ghost.
 */
public final class BlockRenderer {

	/** Shown in a key slot while it waits for the player to press something. */
	public static final String KEY_PROMPT = "press a key";

	/**
	 * Which param, if any, currently has an open inline editor. A null
	 * {@link #field} means the param is capturing a keypress instead of text.
	 */
	public record EditTarget(BlockInstance block, String paramId, InlineTextField field) {
		boolean matches(BlockInstance candidate, ParamSpec spec) {
			return candidate == block && spec.id().equals(paramId);
		}

		boolean capturing() {
			return field == null;
		}
	}

	private BlockRenderer() {
	}

	public static void drawStack(GuiGraphicsExtractor graphics, Font font, List<BlockLayout.Node> nodes,
			BlockLayout.Node hovered, EditTarget edit) {
		for (BlockLayout.Node node : nodes) {
			draw(graphics, font, node, hovered, edit);
		}
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, BlockLayout.Node node,
			BlockLayout.Node hovered, EditTarget edit) {
		BlockType type = node.block.type();
		if (type == null) {
			drawUnknown(graphics, font, node);
			return;
		}

		int color = type.category().color();
		int border = Theme.BLOCK_BORDER;
		int accent = type.category().accent();

		if (type.isEvent()) {
			drawEvent(graphics, node, color, border);
			drawConnector(graphics, node, color, border);
		} else if (type.isBoolean()) {
			drawHexagon(graphics, node.x, node.y, node.x + node.headerWidth,
					node.y + node.headerHeight, color, accent);
		} else if (type.isReporter()) {
			drawValueShape(graphics, node.x, node.y, node.x + node.headerWidth,
					node.y + node.headerHeight, color, accent);
		} else {
			// A C-block is one shape, so its header, arms, dividers and foot are
			// outlined together — outlining each piece drew a line across every
			// join and made the parts look like separate blocks.
			if (node.branches.isEmpty()) {
				fillPanel(graphics, node.x, node.y, node.x + node.headerWidth,
						node.y + node.headerHeight - BlockLayout.NOTCH_HEIGHT, color, border);
			} else {
				drawContainer(graphics, node, color, border);
			}

			// The groove along the top edge that makes stacked blocks read as joined.
			graphics.fill(node.x + BlockLayout.NOTCH_X, node.y,
					node.x + BlockLayout.NOTCH_X + BlockLayout.NOTCH_WIDTH,
					node.y + BlockLayout.NOTCH_HEIGHT, border);
			// Restore the preceding block's tongue inside this block's socket.
			if (node.parentList != null && node.indexInParent > 0) {
				BlockType previous = node.parentList.get(node.indexInParent - 1).type();
				if (previous != null && !previous.isReporter()) {
					int left = node.x + BlockLayout.NOTCH_X;
					int right = left + BlockLayout.NOTCH_WIDTH;
					graphics.fill(left + 1, node.y, right - 1, node.y + BlockLayout.NOTCH_HEIGHT - 1,
							previous.category().color());
				}
			}
			// A subtle top bevel and a stepped bottom connector, within the
			// measured silhouette so stacking and hit-testing stay aligned.
			int notchLeft = node.x + BlockLayout.NOTCH_X;
			int notchRight = notchLeft + BlockLayout.NOTCH_WIDTH;
			graphics.fill(node.x + 1, node.y + 1, notchLeft, node.y + 2, accent);
			graphics.fill(notchRight, node.y + 1, node.x + node.headerWidth - 1, node.y + 2, accent);
			drawConnector(graphics, node, color, border);
		}

		for (int i = 0; i < node.dividerYs.size(); i++) {
			String label = type.branchLabel(i + 1);
			if (!label.isEmpty()) {
				int dividerY = node.dividerYs.get(i);
				graphics.text(font, label, node.x + BlockLayout.PAD_X,
						BlockLayout.textTop(dividerY, BlockLayout.DIVIDER_HEIGHT, font),
						Theme.TEXT_ON_BLOCK);
			}
		}

		if (node == hovered) {
			outlineShape(graphics, node, type, Theme.BLOCK_HOVER);
		}

		for (BlockLayout.TextPart part : node.textParts) {
			graphics.text(font, part.text(), part.x(), part.y(), Theme.TEXT_ON_BLOCK);
		}
		for (BlockLayout.ParamBox param : node.params) {
			if (param.nested != null) {
				draw(graphics, font, param.nested, hovered, edit);
			} else {
				drawParam(graphics, font, node.block, param, edit);
			}
		}

		for (BlockLayout.Branch branch : node.branches) {
			drawStack(graphics, font, branch.children(), hovered, edit);
			if (!branch.children().isEmpty()) {
				BlockLayout.Node first = branch.children().getFirst();
				BlockType childType = first.block.type();
				if (childType != null && !childType.isReporter()) {
					int left = first.x + BlockLayout.NOTCH_X;
					graphics.fill(left + 1, first.y, left + BlockLayout.NOTCH_WIDTH - 1,
							first.y + BlockLayout.NOTCH_HEIGHT - 1, color);
				}
			}
		}
	}

	private static void drawParam(GuiGraphicsExtractor graphics, Font font, BlockInstance block,
			BlockLayout.ParamBox param, EditTarget edit) {
		int x = param.x;
		int y = param.y;
		int right = x + param.width;
		int bottom = y + param.height;

		boolean editing = edit != null && edit.matches(block, param.spec);
		boolean slot = param.acceptsBlock();

		if (param.spec.acceptsOnlyBoolean()) {
			// Same fill as every other empty slot — only the shape says what fits.
			drawHexagon(graphics, x, y, right, bottom, Theme.PARAM_FILL, Theme.SLOT_BORDER);
			return;
		}
		if (slot) {
			drawValueShape(graphics, x, y, right, bottom, Theme.PARAM_FILL, Theme.SLOT_BORDER);
		} else {
			graphics.fill(x, y, right, bottom, Theme.PARAM_FILL);
			graphics.outline(x, y, param.width, param.height, Theme.SLOT_BORDER);
		}
		if (editing) {
			graphics.outline(x, y, param.width, param.height, Theme.PARAM_FOCUSED);
		}

		boolean capturing = editing && edit.capturing();
		String text = editing
				? (capturing ? KEY_PROMPT : edit.field().value())
				: block.arg(param.spec.id());

		// Same inset as the label text uses, on the same line — the field is
		// measured to this padding, so the text sits centred in it.
		int textX = x + BlockLayout.PARAM_PAD_X;
		int textY = BlockLayout.textTop(y, param.height, font);
		int available = param.width - BlockLayout.PARAM_PAD_X * 2;
		String shown = font.plainSubstrByWidth(text, available);

		graphics.text(font, shown, textX, textY, capturing ? Theme.TEXT_MUTED : Theme.PARAM_TEXT);
		if (param.spec.kind() == ParamSpec.Kind.CHOICE) {
			graphics.text(font, "v", right - BlockLayout.PARAM_PAD_X - 6, textY, Theme.TEXT_MUTED);
		}

		if (editing && !capturing && edit.field().caretVisible()) {
			String beforeCaret = text.substring(0, Math.min(edit.field().cursor(), text.length()));
			int caretX = Math.min(textX + font.width(beforeCaret), right - 2);
			graphics.fill(caretX, y + 2, caretX + 1, bottom - 2, Theme.PARAM_TEXT);
		}
	}

	private static void drawUnknown(GuiGraphicsExtractor graphics, Font font, BlockLayout.Node node) {
		fillPanel(graphics, node.x, node.y, node.x + node.headerWidth,
				node.y + node.headerHeight, 0xFF555560, 0xFF33333A);
		graphics.text(font, "unknown block: " + node.block.typeId(), node.x + BlockLayout.PAD_X,
				BlockLayout.textTop(node.y, node.headerHeight, font), Theme.TEXT_MUTED);
	}

	private static void fillPanel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2,
			int fill, int border) {
		graphics.fill(x1, y1, x2, y2, fill);
		graphics.outline(x1, y1, x2 - x1, y2 - y1, border);
	}

	private static void drawEvent(GuiGraphicsExtractor graphics, BlockLayout.Node node, int fill, int border) {
		int bottom = node.y + node.headerHeight - BlockLayout.NOTCH_HEIGHT;
		graphics.fill(node.x, node.y + 6, node.x + node.headerWidth, bottom, fill);
		graphics.fill(node.x + 6, node.y + 3, node.x + 46, node.y + 6, fill);
		graphics.fill(node.x + 10, node.y, node.x + 42, node.y + 3, fill);
		outlineEvent(graphics, node, border);
	}

	private static void outlineEvent(GuiGraphicsExtractor graphics, BlockLayout.Node node, int border) {
		int x = node.x;
		int y = node.y;
		int right = x + node.headerWidth;
		int bottom = y + node.headerHeight - BlockLayout.NOTCH_HEIGHT;
		graphics.fill(x + 10, y, x + 42, y + 1, border);
		graphics.fill(x + 9, y, x + 10, y + 3, border);
		graphics.fill(x + 42, y, x + 43, y + 3, border);
		graphics.fill(x + 6, y + 3, x + 10, y + 4, border);
		graphics.fill(x + 42, y + 3, x + 46, y + 4, border);
		graphics.fill(x + 5, y + 3, x + 6, y + 6, border);
		graphics.fill(x + 46, y + 3, x + 47, y + 6, border);
		graphics.fill(x, y + 6, x + 6, y + 7, border);
		graphics.fill(x + 46, y + 6, right, y + 7, border);
		graphics.fill(x, y + 6, x + 1, bottom, border);
		graphics.fill(right - 1, y + 6, right, bottom, border);
		graphics.fill(x, bottom - 1, right, bottom, border);
	}

	/**
	 * Traces the block's own silhouette in {@code color}, on top of whatever has
	 * already been drawn.
	 *
	 * <p>Used for the hover highlight. That used to be a plain rectangle around the
	 * header, which on a C-block closed a line straight across the mouth and left
	 * the arm and the foot unmarked — the highlight outlined a block that wasn't
	 * the shape on screen.
	 */
	private static void outlineShape(GuiGraphicsExtractor graphics, BlockLayout.Node node,
			BlockType type, int color) {
		int right = node.x + node.headerWidth;
		int bottom = node.y + node.headerHeight;

		if (type.isEvent()) {
			outlineEvent(graphics, node, color);
			outlineConnector(graphics, node, color);
		} else if (type.isBoolean()) {
			outlineHexagon(graphics, node.x, node.y, right, bottom, color);
		} else if (type.isReporter()) {
			outlineValueShape(graphics, node.x, node.y, right, bottom, color);
		} else {
			if (node.branches.isEmpty()) {
				graphics.outline(node.x, node.y, node.headerWidth,
						node.headerHeight - BlockLayout.NOTCH_HEIGHT, color);
			} else {
				outlineContainer(graphics, node.x, bands(node), color);
			}
			outlineConnector(graphics, node, color);
		}
	}

	private static void drawConnector(GuiGraphicsExtractor graphics, BlockLayout.Node node, int fill, int border) {
		int left = node.x + BlockLayout.NOTCH_X;
		int top = node.y + node.height - BlockLayout.NOTCH_HEIGHT;
		graphics.fill(left, top - 1, left + BlockLayout.NOTCH_WIDTH, node.y + node.height, fill);
		outlineConnector(graphics, node, border);
	}

	private static void outlineConnector(GuiGraphicsExtractor graphics, BlockLayout.Node node, int border) {
		int left = node.x + BlockLayout.NOTCH_X;
		int right = left + BlockLayout.NOTCH_WIDTH;
		int bottom = node.y + node.height;
		int top = bottom - BlockLayout.NOTCH_HEIGHT;
		graphics.fill(left, top, left + 1, bottom, border);
		graphics.fill(right - 1, top, right, bottom, border);
		graphics.fill(left, bottom - 1, right, bottom, border);
	}

	/**
	 * Draws a C-block as one outlined shape.
	 *
	 * <p>Filling every band first and only then tracing the outside means no line
	 * is ever drawn across a join, so the whole thing reads as one block with a
	 * single border around it.
	 */
	private static void drawContainer(GuiGraphicsExtractor graphics, BlockLayout.Node node,
			int fill, int border) {
		List<int[]> bands = bands(node);
		for (int[] band : bands) {
			graphics.fill(node.x, band[0], band[2], band[1], fill);
		}
		outlineContainer(graphics, node.x, bands, border);
	}

	/**
	 * The C-block silhouette, as a run of bands stacked from the header down to the
	 * foot. All are flush against the same left edge and differ only in how far
	 * right they reach — full width for the header, dividers and foot, and just the
	 * arm's width beside each branch. Each entry is {@code {top, bottom, right}}.
	 */
	private static List<int[]> bands(BlockLayout.Node node) {
		int fullRight = node.x + node.headerWidth;
		int armRight = node.x + BlockLayout.BRANCH_INDENT;

		List<int[]> bands = new ArrayList<>();
		bands.add(new int[]{node.y, node.y + node.headerHeight - BlockLayout.NOTCH_HEIGHT, fullRight});
		for (int i = 0; i < node.branches.size(); i++) {
			BlockLayout.Branch branch = node.branches.get(i);
			bands.add(new int[]{branch.y(), branch.y() + branch.height(), armRight});
			if (i < node.dividerYs.size()) {
				int dividerY = node.dividerYs.get(i);
				bands.add(new int[]{dividerY, dividerY + BlockLayout.DIVIDER_HEIGHT - BlockLayout.NOTCH_HEIGHT, fullRight});
			}
		}
		bands.add(new int[]{node.footerY, node.footerY + BlockLayout.FOOTER_HEIGHT - BlockLayout.NOTCH_HEIGHT, fullRight});
		return bands;
	}

	private static void outlineContainer(GuiGraphicsExtractor graphics, int left, List<int[]> bands,
			int border) {
		int[] first = bands.getFirst();
		int[] last = bands.getLast();

		// The left edge runs the whole height uninterrupted; every band contributes
		// its own right edge, and the top and bottom cap the ends.
		graphics.fill(left, first[0], left + 1, last[1], border);
		graphics.fill(left, first[0], first[2], first[0] + 1, border);
		graphics.fill(left, last[1] - 1, last[2], last[1], border);
		for (int[] band : bands) {
			graphics.fill(band[2] - 1, band[0], band[2], band[1], border);
		}

		// Where one band is wider than the next, the overhang's edge is exposed:
		// along the bottom of the wider band, or the top of the wider one below.
		for (int i = 0; i + 1 < bands.size(); i++) {
			int above = bands.get(i)[2];
			int below = bands.get(i + 1)[2];
			int boundary = bands.get(i)[1];
			if (above > below) {
				graphics.fill(below - 1, boundary - 1, above, boundary, border);
			} else if (below > above) {
				graphics.fill(above - 1, boundary, below, boundary + 1, border);
			}
		}
	}

	/**
	 * The pointed shape that marks a condition: boolean blocks and the holes they
	 * drop into. Each row is inset by its distance from the middle, which gives
	 * flat top and bottom edges and a point at each end.
	 */
	private static void drawHexagon(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2,
			int fill, int border) {
		int height = y2 - y1;
		if (height <= 0) {
			return;
		}
		double middle = (height - 1) / 2.0;

		for (int row = 0; row < height; row++) {
			int inset = (int) Math.round(Math.abs(row - middle));
			int left = Math.min(x1 + inset, x2);
			int right = Math.max(x2 - inset, left);
			graphics.fill(left, y1 + row, right, y1 + row + 1, fill);
		}

		if (border != fill) {
			outlineHexagon(graphics, x1, y1, x2, y2, border);
		}
	}

	private static void outlineHexagon(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2,
			int border) {
		int height = y2 - y1;
		if (height <= 0) {
			return;
		}
		double middle = (height - 1) / 2.0;

		for (int row = 0; row < height; row++) {
			int inset = (int) Math.round(Math.abs(row - middle));
			int left = Math.min(x1 + inset, x2);
			int right = Math.max(x2 - inset, left);
			graphics.fill(left, y1 + row, left + 1, y1 + row + 1, border);
			graphics.fill(right - 1, y1 + row, right, y1 + row + 1, border);
		}

		int cap = (int) Math.round(middle);
		graphics.fill(x1 + cap, y1, x2 - cap, y1 + 1, border);
		graphics.fill(x1 + cap, y2 - 1, x2 - cap, y2, border);
	}

	/** Reporters and value slots share the same clipped corners. */
	private static void drawValueShape(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2,
			int fill, int border) {
		int height = y2 - y1;
		if (height <= 0) {
			return;
		}

		for (int row = 0; row < height; row++) {
			int inset = valueInset(height, x2 - x1, row);
			graphics.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, fill);
		}

		if (border != fill) {
			outlineValueShape(graphics, x1, y1, x2, y2, border);
		}
	}

	private static void outlineValueShape(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2,
			int border) {
		int height = y2 - y1;
		int width = x2 - x1;
		if (height <= 0) {
			return;
		}

		for (int row = 0; row < height; row++) {
			int inset = valueInset(height, width, row);
			int outer = Math.max(inset + 1, Math.max(
					valueInset(height, width, Math.max(0, row - 1)),
					valueInset(height, width, Math.min(height - 1, row + 1))));
			graphics.fill(x1 + inset, y1 + row, x1 + outer, y1 + row + 1, border);
			graphics.fill(x2 - outer, y1 + row, x2 - inset, y1 + row + 1, border);
		}

		int cap = valueInset(height, width, 0);
		graphics.fill(x1 + cap, y1, x2 - cap, y1 + 1, border);
		graphics.fill(x1 + cap, y2 - 1, x2 - cap, y2, border);
	}

	/** Shared by the fill and outline, capped for narrow slots. */
	private static int valueInset(int height, int width, int row) {
		int corner = Math.min(3, Math.min(height / 2, width / 2));
		return Math.max(0, corner - Math.min(row, height - 1 - row));
	}
}
