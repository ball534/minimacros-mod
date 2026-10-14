package com.minimacros.gui;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.BlockCategory;
import com.minimacros.macro.ScriptStack;
import com.minimacros.macro.ParamSpec;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.KeyEvent;

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
		eventHatsStayAtTheTop();
		eventHatsAttachOnlyAboveOrdinaryRoots();
		choiceDropdownUsesScaledCanvasCoordinates();
		physicalCombinationNamesStayCanonical();
		standaloneModifierCaptureFinishesOnRelease();
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
		for (int i = 0; i < BlockCategory.values().length; i++) palette.mouseScrolled(-1);
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

	private static void eventHatsStayAtTheTop() throws Exception {
		BlockInstance event = BlockInstance.create(BlockRegistry.get(BlockRegistry.WHEN_ENABLED));
		ScriptStack stack = new ScriptStack(40, 60, List.of(event, action()));
		BlockLayout.Layout layout = BlockLayout.compute(stack.blocks(), 40, 60, FONT);
		check(layout.targets.stream().noneMatch(target -> target.index == 0), "event hat accepts a block above it");
		check(layout.targets.stream().anyMatch(target -> target.index == 1), "action cannot attach below an event");
		ScriptCanvasWidget canvas = canvas();
		canvas.setStacks(new ArrayList<>(List.of(stack)));
		DragState hat = DragState.fromPalette(BlockInstance.create(BlockRegistry.get(BlockRegistry.WHEN_ENABLED)), 0, 0);
		hat.moveTo(140, 110);
		canvas.drop(hat, 145, 125);
		check(canvas.stacks().size() == 1 && stack.blocks().size() == 2, "event inserted into an existing stack");
		hat.moveTo(500, 300);
		canvas.drop(hat, 500, 300);
		check(canvas.stacks().size() == 2 && canvas.stacks().getLast().blocks().getFirst() == hat.blocks().getFirst(),
				"event did not start a separate stack in open space");
		List<DragState> pickedUp = new ArrayList<>();
		ScriptCanvasWidget mover = new ScriptCanvasWidget(FONT, pickedUp::add);
		mover.setBounds(100, 50, 640, 400);
		mover.setStacks(new ArrayList<>(List.of(stack)));
		mover.mouseClicked(new MouseButtonEvent(143, 130, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
		check(pickedUp.size() == 1 && pickedUp.getFirst().blocks().size() == 2,
				"moving an event hat did not carry its action stack");
	}

	private static void eventHatsAttachOnlyAboveOrdinaryRoots() throws Exception {
		for (double wheel : new double[]{-100, 0, 100}) {
			ScriptCanvasWidget canvas = canvas();
			BlockInstance first = action();
			ScriptStack root = new ScriptStack(40, 120, List.of(first, action(), action(), action(), action(), action()));
			canvas.setStacks(new ArrayList<>(List.of(root)));
			canvas.mouseScrolled(100, 50, wheel);
			DragState hat = DragState.fromPalette(BlockInstance.create(BlockRegistry.get(BlockRegistry.WHEN_ENABLED)), 12, 8);
			int advance = BlockLayout.stackHeight(BlockLayout.compute(hat.blocks(), 0, 0, FONT).nodes);
			double mx = 100 + 52 * canvas.zoom();
			double my = 50 + (120 - advance + 8) * canvas.zoom();
			hat.moveTo(mx, my, canvas.zoom());
			canvas.drop(hat, mx, my);
			check(canvas.stacks().size() == 1 && canvas.stacks().getFirst().blocks().size() == 7,
					"event failed to attach above ordinary root at zoom " + canvas.zoom());
			ScriptStack joined = canvas.stacks().getFirst();
			check(joined.x() == root.x() && joined.y() == root.y() - advance, "hat moved the original root body");
			check(BlockLayout.compute(joined.blocks(), joined.x(), joined.y(), FONT).nodes.get(1).y == root.y(),
					"first action shifted when hat was attached");

			ScriptStack middleRoot = new ScriptStack(40, 120, List.of(action(), action(), action(), action(), action(), action()));
			checkEventRejected(canvas, middleRoot, wheel, 50, 235, "middle of ordinary stack");
			BlockInstance container = BlockInstance.create(BlockRegistry.get(BlockRegistry.IF_ELSE));
			container.branch(0).add(action());
			ScriptStack branchRoot = new ScriptStack(40, 120, List.of(container));
			BlockLayout.Node conditional = BlockLayout.compute(branchRoot.blocks(), 40, 120, FONT).nodes.getFirst();
			BlockLayout.Node child = conditional.branches.getFirst().children().getFirst();
			checkEventRejected(canvas, branchRoot, wheel, child.x + 3, child.y + 8, "nested branch");
			checkEventRejected(canvas, new ScriptStack(40, 120, List.of(
					BlockInstance.create(BlockRegistry.get(BlockRegistry.WHEN_ENABLED)), action())), wheel, 52, 130,
					"existing event hat");
		}
	}

	private static void checkEventRejected(ScriptCanvasWidget canvas, ScriptStack target, double wheel,
			int contentX, int contentY, String location) {
		canvas.setStacks(new ArrayList<>(List.of(target)));
		canvas.mouseScrolled(100, 50, wheel);
		int count = target.blocks().size();
		List<BlockInstance> origin = new ArrayList<>();
		BlockInstance event = BlockInstance.create(BlockRegistry.get(BlockRegistry.WHEN_ENABLED));
		DragState drag = DragState.fromScript(new ArrayList<>(List.of(event)), origin, 0, 0, 0);
		double mx = 100 + contentX * canvas.zoom();
		double my = 50 + contentY * canvas.zoom();
		drag.moveTo(mx, my, canvas.zoom());
		canvas.drop(drag, mx, my);
		check(canvas.stacks().size() == 1 && target.blocks().size() == count && origin.contains(event),
				"event attached to " + location + " at zoom " + canvas.zoom());
	}

	private static void choiceDropdownUsesScaledCanvasCoordinates() throws Exception {
		for (double wheel : new double[]{-100, 0, 100}) {
			ScriptCanvasWidget canvas = canvas();
			canvas.mouseScrolled(100, 50, wheel);
			BlockInstance event = BlockInstance.create(BlockRegistry.get(BlockRegistry.WHEN_PRESSED));
			BlockLayout.ParamBox mode = BlockLayout.compute(List.of(event), 0, 0, FONT).nodes.getFirst().params.stream()
					.filter(param -> param.spec.kind() == ParamSpec.Kind.CHOICE).findFirst().orElseThrow();
			Method open = ScriptCanvasWidget.class.getDeclaredMethod("openParam", BlockInstance.class, BlockLayout.ParamBox.class);
			open.setAccessible(true);
			open.invoke(canvas, event, mode);
			check(canvas.choiceOpen(), "choice dropdown did not open");
			Method boundsMethod = ScriptCanvasWidget.class.getDeclaredMethod("choiceBounds");
			boundsMethod.setAccessible(true);
			Object bounds = boundsMethod.invoke(canvas);
			int bx = recordInt(bounds, "x");
			int by = recordInt(bounds, "y");
			int rh = recordInt(bounds, "rowHeight");
			canvas.mouseClicked(new MouseButtonEvent(100 + (bx + 3) * canvas.zoom(),
					50 + (by + rh + 3) * canvas.zoom(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
			check(event.arg(mode.spec.id()).equals(mode.spec.choices().get(1)), "scaled dropdown selected the wrong option");
			check(!canvas.choiceOpen(), "choice dropdown stayed open after selection");
			open.invoke(canvas, event, mode);
			canvas.mouseClicked(click(0, 0));
			check(!canvas.choiceOpen(), "outside click did not dismiss choice dropdown");
			open.invoke(canvas, event, mode);
			canvas.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
			check(!canvas.choiceOpen(), "Escape did not dismiss choice dropdown");
		}
	}

	private static int recordInt(Object record, String name) throws Exception {
		Method method = record.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		return (int) method.invoke(record);
	}

	private static void physicalCombinationNamesStayCanonical() {
		check(ScriptCanvasWidget.combinationName(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_R),
				List.of(InputConstants.KEY_LCONTROL)).equals("left.control + r"), "keyboard combination was not canonical");
		check(ScriptCanvasWidget.combinationName(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT),
				List.of(InputConstants.KEY_LSHIFT)).equals("left.shift + mouse.left"), "mouse combination was not canonical");
		check(ScriptCanvasWidget.isModifier(InputConstants.KEY_RCONTROL), "right modifier ended combination capture");
		check(!ScriptCanvasWidget.supportsCombination(BlockInstance.create(BlockRegistry.get(BlockRegistry.BUTTON_DOWN))),
				"simulated button-down unexpectedly captures combinations");
	}

	private static void standaloneModifierCaptureFinishesOnRelease() throws Exception {
		ScriptCanvasWidget canvas = canvas();
		BlockInstance event = BlockInstance.create(BlockRegistry.get(BlockRegistry.WHEN_PRESSED));
		BlockLayout.ParamBox key = BlockLayout.compute(List.of(event), 0, 0, FONT).nodes.getFirst().params.stream()
				.filter(param -> param.spec.capturesKey()).findFirst().orElseThrow();
		Method open = ScriptCanvasWidget.class.getDeclaredMethod("openParam", BlockInstance.class, BlockLayout.ParamBox.class);
		open.setAccessible(true);
		open.invoke(canvas, event, key);
		canvas.keyPressed(new KeyEvent(InputConstants.KEY_LSHIFT, 0, InputConstants.MOD_SHIFT));
		check(event.arg(key.spec.id()).isEmpty(), "modifier down prematurely ended combination capture");
		check(canvas.keyReleased(new KeyEvent(InputConstants.KEY_LSHIFT, 0, 0)), "modifier release was not consumed");
		check(event.arg(key.spec.id()).equals("left.shift"), "modifier-only capture did not save on release");
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
