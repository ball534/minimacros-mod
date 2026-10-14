package com.minimacros.gui;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.BlockCategory;
import com.minimacros.macro.ScriptStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Checks real canvas coordinates, joined geometry and snapping without a window. */
public final class CanvasRegressionTest {
	private static final Font FONT = new Font(null) {
		@Override
		public int width(String text) { return text.length() * 6; }
	};

	public static void main(String[] args) throws Exception {
		zoomKeepsCursorAnchored();
		panningTracksThePointerAtZoom();
		dropdownSelectsEveryCategory();
		joinedBlocksShareTheirBorders();
		snapsAcrossWideHeadersAtEveryZoom();
		System.out.println("Canvas regression checks passed");
	}

	private static void panningTracksThePointerAtZoom() throws Exception {
		ScriptCanvasWidget canvas = canvas();
		canvas.mouseScrolled(100, 50, 100);
		int beforeX = content(canvas, "toContentX", 200);
		int beforeY = content(canvas, "toContentY", 150);
		canvas.mouseClicked(click(200, 150));
		canvas.mouseDragged(240, 190);
		check(content(canvas, "toContentX", 240) == beforeX && content(canvas, "toContentY", 190) == beforeY,
				"panning did not track the mouse at zoom");
		canvas.mouseReleased();
	}

	private static void dropdownSelectsEveryCategory() throws Exception {
		PaletteWidget palette = new PaletteWidget(FONT);
		palette.setBounds(20, 20, 240, 180);
		palette.mouseClicked(click(30, 55));
		check(palette.categoryMenuOpen(), "category dropdown did not open");
		palette.mouseScrolled(-1);
		palette.mouseClicked(click(40, 170));
		java.lang.reflect.Field category = PaletteWidget.class.getDeclaredField("category");
		category.setAccessible(true);
		check(category.get(palette) == BlockCategory.COMPARATORS,
				"last category was unreachable in a short dropdown");
		check(!palette.categoryMenuOpen(), "dropdown did not close after selecting");
		palette.mouseClicked(click(30, 55));
		palette.mouseClicked(click(0, 0));
		check(!palette.categoryMenuOpen(), "outside click did not dismiss dropdown");
	}

	private static MouseButtonEvent click(double x, double y) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
	}

	private static void zoomKeepsCursorAnchored() throws Exception {
		ScriptCanvasWidget canvas = canvas();
		int beforeX = content(canvas, "toContentX", 421);
		int beforeY = content(canvas, "toContentY", 239);
		canvas.mouseScrolled(421, 239, 5);
		check(canvas.zoom() > 1, "scroll up did not zoom in");
		check(content(canvas, "toContentX", 421) == beforeX && content(canvas, "toContentY", 239) == beforeY,
				"zoom moved the content beneath the cursor");
		canvas.mouseScrolled(421, 239, -5);
		check(Math.abs(canvas.zoom() - 1) < 0.000001, "opposite wheel movement did not restore zoom");
		canvas.mouseScrolled(421, 239, 10000);
		check(canvas.zoom() == 2, "maximum zoom was not clamped");
		canvas.mouseScrolled(421, 239, -10000);
		check(canvas.zoom() == 0.5, "minimum zoom was not clamped");
		canvas.mouseScrolled(421, 239, Double.NaN);
		check(canvas.zoom() == 0.5, "nonfinite scrolling changed zoom");
	}

	private static void joinedBlocksShareTheirBorders() {
		List<BlockInstance> blocks = List.of(action(), action());
		BlockLayout.Layout layout = BlockLayout.compute(blocks, 20, 30, FONT);
		BlockLayout.Node first = layout.nodes.getFirst();
		BlockLayout.Node second = layout.nodes.getLast();
		check(second.y == first.y + first.height - BlockLayout.STACK_OVERLAP,
				"stacked blocks left a gap instead of overlapping connectors");
		check(layout.targets.getLast().y == second.y + second.height - BlockLayout.STACK_OVERLAP,
				"append target did not match the joined geometry");
		check(layout.targets.getLast().width == second.headerWidth, "snap target lost the full header width");

		BlockInstance conditional = BlockInstance.create(BlockRegistry.get(BlockRegistry.IF_ELSE));
		conditional.branch(0).addAll(blocks);
		BlockLayout.Node container = BlockLayout.compute(List.of(conditional), 0, 0, FONT).nodes.getFirst();
		BlockLayout.Branch branch = container.branches.getFirst();
		check(branch.y() == container.headerHeight - BlockLayout.STACK_OVERLAP,
				"nested stack did not join the container header");
		check(container.dividerYs.getFirst() == branch.children().getLast().y
				+ branch.children().getLast().height - BlockLayout.STACK_OVERLAP,
				"nested stack did not join the else divider");
	}

	private static void snapsAcrossWideHeadersAtEveryZoom() throws Exception {
		for (double wheel : new double[]{-100, 0, 100}) {
			ScriptCanvasWidget canvas = canvas();
			ScriptStack stack = new ScriptStack(40, 60, List.of(action()));
			List<BlockInstance> existing = stack.blocks();
			canvas.setStacks(new ArrayList<>(List.of(stack)));
			canvas.mouseScrolled(100, 50, wheel);
			BlockLayout.Node target = BlockLayout.compute(existing, 40, 60, FONT).nodes.getFirst();
			int seam = target.y + target.height - BlockLayout.STACK_OVERLAP;
			int grabX = target.headerWidth - 4;
			DragState drag = DragState.fromPalette(action(), grabX, 10);
			double mouseX = 100 + (40 + grabX) * canvas.zoom();
			double mouseY = 50 + (seam + 10) * canvas.zoom();
			drag.moveTo(mouseX, mouseY, canvas.zoom());
			canvas.drop(drag, mouseX, mouseY);
			check(existing.size() == 2 && canvas.stacks().size() == 1,
					"block grabbed near its right edge failed to append at zoom " + canvas.zoom());
			check(existing.getLast() == drag.blocks().getFirst(), "block snapped to the wrong insertion point");
		}
	}

	private static ScriptCanvasWidget canvas() {
		ScriptCanvasWidget canvas = new ScriptCanvasWidget(FONT, drag -> {});
		canvas.setBounds(100, 50, 640, 400);
		return canvas;
	}

	private static BlockInstance action() {
		BlockInstance block = BlockInstance.create(BlockRegistry.get(BlockRegistry.ASSIGN));
		block.setArg("a", "a long variable name");
		block.setArg("b", "42");
		return block;
	}

	private static int content(ScriptCanvasWidget canvas, String methodName, double screen) throws Exception {
		Method method = ScriptCanvasWidget.class.getDeclaredMethod(methodName, double.class);
		method.setAccessible(true);
		return (int) method.invoke(canvas, screen);
	}

	private static void check(boolean passed, String message) {
		if (!passed) throw new AssertionError(message);
	}
}
