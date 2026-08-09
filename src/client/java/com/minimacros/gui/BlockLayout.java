package com.minimacros.gui;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockType;
import com.minimacros.macro.ParamSpec;
import com.minimacros.macro.ScriptStack;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures a block tree into concrete rectangles, and — in the same pass —
 * records every place a dragged block could be dropped: gaps between stacked
 * blocks, and slots that accept a reporter.
 *
 * <p>Everything here works in canvas content coordinates. The canvas widget
 * translates to screen coordinates when it draws and when it tests the mouse.
 */
public final class BlockLayout {
	public static final int HEADER_HEIGHT = 20;
	public static final int PAD_X = 6;
	public static final int GAP = 4;
	public static final int BRANCH_INDENT = 14;
	public static final int BRANCH_MIN_HEIGHT = 18;
	public static final int DIVIDER_HEIGHT = 16;
	public static final int FOOTER_HEIGHT = 9;
	/**
	 * Kept an even number of pixels shorter than {@link #HEADER_HEIGHT} so a field
	 * centred in a header lands on a whole pixel — that is what puts the text
	 * inside a field on exactly the same line as the label text beside it.
	 */
	public static final int PARAM_HEIGHT = 14;
	/** Space either side of the text in a field, and the same at both ends. */
	public static final int PARAM_PAD_X = 4;
	/** Width the arrow on a dropdown field needs. */
	public static final int DROPDOWN_ARROW_WIDTH = 8;
	public static final int MIN_HEADER_WIDTH = 40;
	public static final int NOTCH_X = 8;
	public static final int NOTCH_WIDTH = 10;
	public static final int NOTCH_HEIGHT = 3;
	/** Vertical breathing room above and below a reporter nested in a slot. */
	private static final int SLOT_PADDING = 3;
	/** How far a boolean hexagon's point juts out past its contents. */
	private static final int BOOLEAN_POINT = 6;

	private BlockLayout() {
	}

	/**
	 * Top of a single line of text centred in a box of {@code boxHeight}.
	 *
	 * <p>Minecraft's line height leaves a row of descender space below the glyphs,
	 * so splitting the leftovers evenly sits the ink a pixel high. Rounding the
	 * half up drops it back onto the optical centre. Every label, field and
	 * divider goes through here, which is what keeps them all on one line.
	 */
	public static int textTop(int boxTop, int boxHeight, Font font) {
		return boxTop + (boxHeight - font.lineHeight + 1) / 2;
	}

	/** A run of literal label text, already positioned. */
	public record TextPart(String text, int x, int y) {
	}

	/**
	 * An editable slot inside a block header. When {@link #nested} is set the
	 * slot holds a reporter and the typed text is not shown.
	 */
	public static final class ParamBox {
		public final ParamSpec spec;
		public int x;
		public int y;
		public int width;
		public int height;
		public Node nested;

		ParamBox(ParamSpec spec, int x, int y, int width, int height) {
			this.spec = spec;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		public boolean contains(int px, int py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}

		public boolean acceptsBlock() {
			return spec.kind() == ParamSpec.Kind.SLOT;
		}
	}

	/** One C-block arm: the indented area its children sit in. */
	public record Branch(int y, int height, List<Node> children) {
	}

	/** A gap between stacked blocks that a dragged stack can snap into. */
	public static final class DropTarget {
		public final List<BlockInstance> list;
		public final int index;
		public int x;
		public int y;
		public final int width;

		DropTarget(List<BlockInstance> list, int index, int x, int y, int width) {
			this.list = list;
			this.index = index;
			this.x = x;
			this.y = y;
			this.width = width;
		}

		public double distanceTo(int px, int py) {
			double dx = px - x;
			double dy = py - y;
			return Math.sqrt(dx * dx + dy * dy);
		}
	}

	/** A slot that a dragged reporter can snap into. */
	public static final class SlotTarget {
		public final BlockInstance owner;
		public final ParamSpec spec;
		public int x;
		public int y;
		public final int width;
		public final int height;

		SlotTarget(BlockInstance owner, ParamSpec spec, int x, int y, int width, int height) {
			this.owner = owner;
			this.spec = spec;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		public double distanceTo(int px, int py) {
			double dx = px - (x + width / 2.0);
			double dy = py - (y + height / 2.0);
			return Math.sqrt(dx * dx + dy * dy);
		}
	}

	/** One laid-out block, including everything nested inside it. */
	public static final class Node {
		public final BlockInstance block;
		public final List<BlockInstance> parentList;
		public final int indexInParent;
		/** Set when this node sits in a slot rather than a stack. */
		public final BlockInstance slotOwner;
		public final String slotParamId;

		public int x;
		public int y;
		public int headerWidth = MIN_HEADER_WIDTH;
		public int headerHeight = HEADER_HEIGHT;
		/** Header plus every branch, divider and footer. */
		public int height = HEADER_HEIGHT;
		public int footerY;

		public final List<TextPart> textParts = new ArrayList<>();
		public final List<ParamBox> params = new ArrayList<>();
		public final List<Branch> branches = new ArrayList<>();
		public final List<Integer> dividerYs = new ArrayList<>();

		Node(BlockInstance block, List<BlockInstance> parentList, int indexInParent,
				BlockInstance slotOwner, String slotParamId, int x, int y) {
			this.block = block;
			this.parentList = parentList;
			this.indexInParent = indexInParent;
			this.slotOwner = slotOwner;
			this.slotParamId = slotParamId;
			this.x = x;
			this.y = y;
		}

		public boolean isNestedInSlot() {
			return slotOwner != null;
		}

		public boolean headerContains(int px, int py) {
			return px >= x && px < x + headerWidth && py >= y && py < y + headerHeight;
		}

		/** Shifts this node and everything inside it. */
		void translate(int dx, int dy) {
			x += dx;
			y += dy;
			footerY += dy;

			textParts.replaceAll(part -> new TextPart(part.text(), part.x() + dx, part.y() + dy));
			for (ParamBox param : params) {
				param.x += dx;
				param.y += dy;
				if (param.nested != null) {
					param.nested.translate(dx, dy);
				}
			}
			dividerYs.replaceAll(value -> value + dy);
			for (int i = 0; i < branches.size(); i++) {
				Branch branch = branches.get(i);
				for (Node child : branch.children()) {
					child.translate(dx, dy);
				}
				branches.set(i, new Branch(branch.y() + dy, branch.height(), branch.children()));
			}
		}
	}

	/** The result of laying out one stack. */
	public static final class Layout {
		public final List<Node> nodes = new ArrayList<>();
		public final List<DropTarget> targets = new ArrayList<>();
		public final List<SlotTarget> slotTargets = new ArrayList<>();
		/** The param being edited, so its box can be measured from the live text. */
		BlockRenderer.EditTarget edit;
	}

	public static Layout compute(List<BlockInstance> script, int originX, int originY, Font font) {
		return compute(script, originX, originY, font, null);
	}

	/**
	 * Lays out one loose stack, used for the palette shelf and the drag ghost.
	 * Passing the open editor measures that param from what has been typed so far
	 * rather than from the value last committed to the block, which is what lets a
	 * field grow as it is typed into.
	 */
	public static Layout compute(List<BlockInstance> script, int originX, int originY, Font font,
			BlockRenderer.EditTarget edit) {
		Layout layout = new Layout();
		layout.edit = edit;
		layout.nodes.addAll(layoutStack(script, originX, originY, font, layout));
		collectSlotTargets(layout.nodes, layout);
		return layout;
	}

	/**
	 * Lays out every stack in a macro, each at its own recorded position. Stacks
	 * are independent, so this is just each one laid out where it sits — the only
	 * thing they share is the one list of drop targets a drag is matched against.
	 */
	public static Layout compute(List<ScriptStack> stacks, Font font, BlockRenderer.EditTarget edit) {
		Layout layout = new Layout();
		layout.edit = edit;
		for (ScriptStack stack : stacks) {
			// A stack emptied by picking every one of its blocks up is only still
			// here so a cancelled drag can put them back. Laying it out would leave
			// a drop target hanging in open space where the blocks used to be, and
			// a short drag would snap straight back onto it instead of moving.
			if (stack.isEmpty()) {
				continue;
			}
			layout.nodes.addAll(layoutStack(stack.blocks(), stack.x(), stack.y(), font, layout));
		}
		collectSlotTargets(layout.nodes, layout);
		return layout;
	}

	private static List<Node> layoutStack(List<BlockInstance> list, int x, int y, Font font, Layout out) {
		List<Node> nodes = new ArrayList<>();
		int cursorY = y;

		for (int i = 0; i < list.size(); i++) {
			out.targets.add(new DropTarget(list, i, x, cursorY, MIN_HEADER_WIDTH));
			Node node = layoutBlock(list.get(i), list, i, null, null, x, cursorY, font, out);
			nodes.add(node);
			cursorY += node.height;
		}

		out.targets.add(new DropTarget(list, list.size(), x, cursorY, MIN_HEADER_WIDTH));
		return nodes;
	}

	private static Node layoutBlock(BlockInstance block, List<BlockInstance> parentList, int index,
			BlockInstance slotOwner, String slotParamId, int x, int y, Font font, Layout out) {
		Node node = new Node(block, parentList, index, slotOwner, slotParamId, x, y);
		BlockType type = block.type();

		if (type == null) {
			node.headerWidth = font.width("unknown block: " + block.typeId()) + PAD_X * 2;
			node.height = HEADER_HEIGHT;
			return node;
		}

		layoutHeader(node, block, type, font, out);

		if (type.branchCount() == 0) {
			node.height = node.headerHeight;
			node.footerY = y + node.headerHeight;
			return node;
		}

		int cursorY = y + node.headerHeight;
		for (int branchIndex = 0; branchIndex < type.branchCount(); branchIndex++) {
			List<BlockInstance> childList = block.branch(branchIndex);
			List<Node> children = layoutStack(childList, x + BRANCH_INDENT, cursorY, font, out);
			int branchHeight = Math.max(BRANCH_MIN_HEIGHT, stackHeight(children));

			node.branches.add(new Branch(cursorY, branchHeight, children));
			cursorY += branchHeight;

			if (branchIndex < type.branchCount() - 1) {
				node.dividerYs.add(cursorY);
				cursorY += DIVIDER_HEIGHT;
			}
		}

		node.footerY = cursorY;
		node.height = cursorY + FOOTER_HEIGHT - y;
		return node;
	}

	/**
	 * Lays out the label and its params. A slot holding a reporter is laid out
	 * inline, which can make the header taller than a plain one — so the row is
	 * measured first and its contents are re-centred afterwards.
	 */
	private static void layoutHeader(Node node, BlockInstance block, BlockType type, Font font, Layout out) {
		List<String> segments = type.labelSegments();
		List<ParamSpec> params = type.params();
		// A hexagon loses width to its points, so its contents start further in.
		int point = type.isBoolean() ? BOOLEAN_POINT : 0;
		int cursorX = node.x + PAD_X + point;
		int tallest = HEADER_HEIGHT;

		// First pass: place everything against the top of the header.
		int contentTop = node.y + SLOT_PADDING;

		for (int i = 0; i < segments.size(); i++) {
			String text = segments.get(i).trim();
			if (!text.isEmpty()) {
				node.textParts.add(new TextPart(text, cursorX, contentTop));
				cursorX += font.width(text) + GAP;
			}

			if (i >= params.size()) {
				continue;
			}
			ParamSpec spec = params.get(i);
			BlockInstance plugged = spec.kind() == ParamSpec.Kind.SLOT ? block.input(spec.id()) : null;
			if (plugged != null) {
				Node nestedNode = layoutBlock(plugged, null, 0, block, spec.id(),
						cursorX, contentTop, font, out);

				ParamBox box = new ParamBox(spec, cursorX, contentTop,
						nestedNode.headerWidth, nestedNode.height);
				box.nested = nestedNode;
				node.params.add(box);

				tallest = Math.max(tallest, nestedNode.height + SLOT_PADDING * 2);
				cursorX += nestedNode.headerWidth + GAP;
			} else {
				int width = paramWidth(font, spec, displayedText(out, block, spec));
				ParamBox box = new ParamBox(spec, cursorX, contentTop, width, PARAM_HEIGHT);
				node.params.add(box);
				cursorX += width + GAP;
			}
		}

		node.headerHeight = Math.max(HEADER_HEIGHT, tallest);
		node.headerWidth = Math.max(MIN_HEADER_WIDTH, cursorX - node.x - GAP + PAD_X + point);

		// Second pass: centre each piece vertically in the finished header.
		int textY = textTop(node.y, node.headerHeight, font);
		node.textParts.replaceAll(part -> new TextPart(part.text(), part.x(), textY));

		for (ParamBox param : node.params) {
			int centredY = node.y + (node.headerHeight - param.height) / 2;
			int delta = centredY - param.y;
			if (param.nested != null) {
				param.nested.translate(0, delta);
			}
			param.y = centredY;
		}
	}

	/**
	 * Collects every slot once the tree has settled. Done as its own pass
	 * because nested reporters are re-centred after they are laid out, which
	 * would leave targets recorded mid-layout pointing at stale positions.
	 */
	private static void collectSlotTargets(List<Node> nodes, Layout out) {
		for (Node node : nodes) {
			for (ParamBox param : node.params) {
				if (param.acceptsBlock()) {
					out.slotTargets.add(new SlotTarget(node.block, param.spec,
							param.x, param.y, param.width, param.height));
				}
				if (param.nested != null) {
					collectSlotTargets(List.of(param.nested), out);
				}
			}
			for (Branch branch : node.branches) {
				collectSlotTargets(branch.children(), out);
			}
		}
	}

	/**
	 * Exactly what {@link BlockRenderer} will draw in this param, so the box is
	 * always measured against the text it ends up holding.
	 */
	private static String displayedText(Layout out, BlockInstance block, ParamSpec spec) {
		if (out.edit != null && out.edit.matches(block, spec)) {
			return out.edit.field() == null ? BlockRenderer.KEY_PROMPT : out.edit.field().value();
		}
		return block.arg(spec.id());
	}

	/** Same padding either side of the text, so it reads as centred in the field. */
	public static int paramWidth(Font font, ParamSpec spec, String value) {
		int textWidth = font.width(value == null ? "" : value);
		int arrow = spec.kind() == ParamSpec.Kind.DROPDOWN ? DROPDOWN_ARROW_WIDTH : 0;
		return Math.max(spec.minWidth(), textWidth + PARAM_PAD_X * 2 + arrow);
	}

	public static int stackHeight(List<Node> nodes) {
		int total = 0;
		for (Node node : nodes) {
			total += node.height;
		}
		return total;
	}

	/**
	 * Deepest block under the point, so clicking inside a C-block or a slot
	 * picks the nested block rather than its container.
	 */
	public static Node hitTest(List<Node> nodes, int px, int py) {
		for (Node node : nodes) {
			Node hit = hitTestNode(node, px, py);
			if (hit != null) {
				return hit;
			}
		}
		return null;
	}

	private static Node hitTestNode(Node node, int px, int py) {
		for (ParamBox param : node.params) {
			if (param.nested != null) {
				Node hit = hitTestNode(param.nested, px, py);
				if (hit != null) {
					return hit;
				}
			}
		}
		for (Branch branch : node.branches) {
			Node hit = hitTest(branch.children(), px, py);
			if (hit != null) {
				return hit;
			}
		}
		return node.headerContains(px, py) ? node : null;
	}

	/** The param box under the point on this node, or null. */
	public static ParamBox paramAt(Node node, int px, int py) {
		for (ParamBox param : node.params) {
			if (param.nested == null && param.contains(px, py)) {
				return param;
			}
		}
		return null;
	}

}
