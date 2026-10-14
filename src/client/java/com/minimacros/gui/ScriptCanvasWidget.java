package com.minimacros.gui;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.ParamSpec;
import com.minimacros.macro.ScriptStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The script editing area. Owns the block tree's on-screen geometry, the
 * drag-and-drop snapping, inline param editing and the right-click menu.
 *
 * <p>A macro is a set of free-standing stacks laid out anywhere on the canvas.
 * Blocks dropped near the top or bottom of an existing stack join it; a block
 * dropped in open space starts a stack of its own, right where it was let go,
 * rather than being pulled into a stack it was never aimed at. Every stack in the
 * macro runs at the same time as the others.
 */
public final class ScriptCanvasWidget {
	/** How far a right-click copy lands from the run it was taken from. */
	private static final int DUPLICATE_OFFSET = 16;
	/** How close a dragged reporter must be to a slot before it snaps in. */
	private static final double SLOT_SNAP_RADIUS = 34;
	/**
	 * How close a dragged stack must be to a join before it snaps on. Past this it
	 * becomes its own stack where it was dropped — without a limit a single block
	 * dropped anywhere on an empty canvas would fly off to join nothing.
	 */
	private static final double STACK_SNAP_RADIUS = 36;

	private final Font font;
	private final Consumer<DragState> dragStarter;

	private int x;
	private int y;
	private int width;
	private int height;
	private double scrollX;
	private double scrollY;
	private double zoom = 1.0;

	private List<ScriptStack> stacks = new ArrayList<>();
	private BlockLayout.Layout layout;

	private boolean panning;
	private double lastPanX;
	private double lastPanY;

	private BlockRenderer.EditTarget edit;
	private ChoiceEdit choice;
	private int choiceSelected;
	private int choiceScroll;
	private final List<Integer> pendingModifiers = new ArrayList<>();
	private record ChoiceEdit(BlockInstance block, BlockLayout.ParamBox anchor) {}
	private record ChoiceBounds(int x, int y, int width, int rows, int rowHeight) {}
	private static final int[] MODIFIER_KEYS = {
			InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL,
			InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT,
			InputConstants.KEY_LALT, InputConstants.KEY_RALT,
			InputConstants.KEY_LGUI, InputConstants.KEY_RGUI
	};

	public ScriptCanvasWidget(Font font, Consumer<DragState> dragStarter) {
		this.font = font;
		this.dragStarter = dragStarter;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void setStacks(List<ScriptStack> stacks) {
		commitEdit();
		this.stacks = stacks;
		this.scrollX = 0;
		this.scrollY = 0;
		this.zoom = 1.0;
	}

	public List<ScriptStack> stacks() {
		return stacks;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	// --- coordinate helpers ---------------------------------------------

	private int toContentX(double screenX) {
		return (int) Math.round((screenX - x) / zoom + scrollX);
	}

	private int toContentY(double screenY) {
		return (int) Math.round((screenY - y) / zoom + scrollY);
	}

	public double zoom() { return zoom; }

	private void rebuild() {
		// The open editor goes in so a field grows while it is being typed into
		// rather than only once the value is committed back to the block.
		layout = BlockLayout.compute(stacks, font, edit);
	}

	/**
	 * Drops stacks that have had their last block taken away.
	 *
	 * <p>Only safe once a drag has been resolved: a stack emptied by picking its
	 * top block up has to stay until the drop lands, because cancelling the drag
	 * puts those blocks back into that very list.
	 */
	public void pruneEmptyStacks() {
		stacks.removeIf(ScriptStack::isEmpty);
	}

	// --- rendering ------------------------------------------------------

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, DragState drag) {
		if (drag == null) {
			// Between drags is the one moment a stack left empty by a deletion or a
			// completed drag can be cleared away.
			pruneEmptyStacks();
		}
		rebuild();

		graphics.fill(x, y, x + width, y + height, Theme.WORKSPACE_BACKGROUND);

		graphics.enableScissor(x, y, x + width, y + height);
		graphics.pose().pushMatrix();
		graphics.pose().translate((float) x, (float) y);
		graphics.pose().scale((float) zoom, (float) zoom);
		graphics.pose().translate((float) -scrollX, (float) -scrollY);
		// Grid and blocks share the transform so both follow panning and zoom.
		int firstGridX = (int) Math.floor((scrollX - 12) / 24) * 24 + 12;
		int firstGridY = (int) Math.floor((scrollY - 12) / 24) * 24 + 12;
		for (int gridY = firstGridY; gridY < scrollY + height / zoom; gridY += 24) {
			for (int gridX = firstGridX; gridX < scrollX + width / zoom; gridX += 24) {
				graphics.fill(gridX, gridY, gridX + 1, gridY + 1, Theme.WORKSPACE_GRID);
			}
		}
		BlockLayout.Node hovered = contains(mouseX, mouseY) && drag == null
				? BlockLayout.hitTest(layout.nodes, toContentX(mouseX), toContentY(mouseY))
				: null;
		BlockRenderer.drawStack(graphics, font, layout.nodes, hovered, edit);
		if (choice != null) renderChoice(graphics, toContentX(mouseX), toContentY(mouseY));

		if (drag != null && contains(mouseX, mouseY)) {
			int aimX = toContentX(mouseX);
			int aimY = toContentY(mouseY);
			BlockLayout.SlotTarget slot = nearestSlot(drag, aimX, aimY);
			if (slot != null) {
				graphics.outline(slot.x - 1, slot.y - 1, slot.width + 2, slot.height + 2,
						Theme.DROP_INDICATOR);
			} else if (!drag.isReporter()) {
				// No stack indicator for input and compare blocks — they cannot
				// join a stack, so offering the gap would be a false promise.
				// Nothing shown when nothing is in range either: the block is about
				// to become a stack of its own exactly where the cursor is.
				BlockLayout.DropTarget target = nearestTarget(drag, aimX);
				if (target != null) {
					graphics.fill(target.x, target.y - 1, target.x + Math.max(target.width, 60),
							target.y + 3, Theme.DROP_INDICATOR);
				}
			}
		}

		graphics.pose().popMatrix();
		graphics.disableScissor();
		graphics.outline(x, y, width, height, Theme.WORKSPACE_BORDER);

		if (stacks.isEmpty() && drag == null) {
			graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
			graphics.centeredText(font, "Drag blocks here from the library",
					x + width / 2, y + (height - font.lineHeight) / 2, Theme.WORKSPACE_MUTED);
			graphics.disableScissor();
		}
	}

	// --- input ----------------------------------------------------------

	/** @return true if the click was consumed. */
	public boolean mouseClicked(MouseButtonEvent event) {
		rebuild();
		if (choice != null) {
			ChoiceBounds bounds = choiceBounds();
			int cx = toContentX(event.x());
			int cy = toContentY(event.y());
			if (contains(event.x(), event.y()) && cx >= bounds.x() && cx < bounds.x() + bounds.width()
					&& cy >= bounds.y() && cy < bounds.y() + bounds.rows() * bounds.rowHeight()
					&& event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				int index = choiceScroll + (cy - bounds.y()) / bounds.rowHeight();
				choice.block().setArg(choice.anchor().spec.id(), choice.anchor().spec.choices().get(index));
			}
			choice = null;
			return true;
		}

		// An open capture takes the click as its value, the same way it takes a
		// keypress — this is what lets one block cover the mouse and the keyboard.
		if (edit != null && edit.capturing()) {
			edit.block().setArg(edit.paramId(), capturedName(InputConstants.Type.MOUSE.getOrCreate(event.button())));
			edit = null;
			return true;
		}

		int contentX = toContentX(event.x());
		int contentY = toContentY(event.y());
		BlockLayout.Node node = BlockLayout.hitTest(layout.nodes, contentX, contentY);

		if (node == null) {
			commitEdit();
			panning = true;
			lastPanX = event.x();
			lastPanY = event.y();
			return true;
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			commitEdit();
			duplicateFrom(node);
			return true;
		}

		BlockLayout.ParamBox param = BlockLayout.paramAt(node, contentX, contentY);
		if (param != null) {
			commitEdit();
			openParam(node.block, param);
			return true;
		}

		commitEdit();
		startDrag(node, event);
		return true;
	}

	private void openParam(BlockInstance block, BlockLayout.ParamBox param) {
		if (param.spec.kind() == ParamSpec.Kind.CHOICE) {
			if (param.spec.choices().isEmpty()) return;
			choice = new ChoiceEdit(block, param);
			choiceSelected = Math.max(0, param.spec.choices().indexOf(block.arg(param.spec.id())));
			choiceScroll = 0;
			keepChoiceVisible();
			return;
		}
		if (param.spec.acceptsOnlyBoolean()) {
			// Nothing to type into a condition — it is filled by dropping a hexagon.
			return;
		}
		if (param.spec.capturesKey()) {
			// A null field marks the param as waiting for a keypress. Typing a key
			// name by hand is what made "space" and "left.shift" so easy to get wrong.
			edit = new BlockRenderer.EditTarget(block, param.spec.id(), null);
			pendingModifiers.clear();
		} else {
			edit = new BlockRenderer.EditTarget(block, param.spec.id(),
					new InlineTextField(param.spec, block.arg(param.spec.id())));
			Minecraft.getInstance().textInputManager().onTextInputFocusChange(this, true);
		}
	}

	/**
	 * Starts a drag. A reporter in a slot comes out on its own; a block in a
	 * stack brings everything below it with it.
	 */
	private void startDrag(BlockLayout.Node node, MouseButtonEvent event) {
		int grabX = toContentX(event.x()) - node.x;
		int grabY = toContentY(event.y()) - node.y;

		if (node.isNestedInSlot()) {
			node.slotOwner.removeInput(node.slotParamId);
			dragStarter.accept(DragState.fromSlot(node.block, node.slotOwner, node.slotParamId,
					grabX, grabY));
			return;
		}

		List<BlockInstance> parent = node.parentList;
		int index = node.indexInParent;
		List<BlockInstance> taken = new ArrayList<>(parent.subList(index, parent.size()));
		parent.subList(index, parent.size()).clear();

		dragStarter.accept(DragState.fromScript(taken, parent, index, grabX, grabY));
	}

	/**
	 * Copies the block under the cursor and everything below it into a stack of
	 * its own, set down a little way off the run it came from so it is plainly a
	 * second copy and can be picked straight back up.
	 *
	 * <p>The original is left exactly as it was — nothing is inserted into it, so
	 * duplicating a run does not quietly make that run twice as long.
	 */
	private void duplicateFrom(BlockLayout.Node node) {
		List<BlockInstance> copies = new ArrayList<>();
		if (node.isNestedInSlot()) {
			// A reporter in a slot has nothing below it; the copy is just itself.
			copies.add(node.block.copy());
		} else {
			for (BlockInstance block : node.parentList.subList(node.indexInParent, node.parentList.size())) {
				copies.add(block.copy());
			}
		}

		stacks.add(new ScriptStack(node.x + DUPLICATE_OFFSET, node.y + DUPLICATE_OFFSET, copies));
	}

	public void mouseDragged(double mouseX, double mouseY) {
		if (!panning) {
			return;
		}
		scrollX -= (mouseX - lastPanX) / zoom;
		scrollY -= (mouseY - lastPanY) / zoom;
		lastPanX = mouseX;
		lastPanY = mouseY;
	}

	public void mouseReleased() {
		panning = false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (choice != null) {
			choiceScroll = Math.clamp(choiceScroll - (int) Math.signum(amount), 0,
					Math.max(0, choice.anchor().spec.choices().size() - choiceBounds().rows()));
			return true;
		}
		if (!Double.isFinite(amount) || amount == 0) return true;
		double nextZoom = Math.clamp(zoom * Math.pow(1.1, amount), 0.5, 2.0);
		// Keep the content under the cursor stationary while its scale changes.
		scrollX += (mouseX - x) / zoom - (mouseX - x) / nextZoom;
		scrollY += (mouseY - y) / zoom - (mouseY - y) / nextZoom;
		zoom = nextZoom;
		return true;
	}

	/**
	 * Drops a dragged stack: into a slot, onto the end of a stack it was let go
	 * near, or — failing both — down as a new stack of its own at the cursor.
	 *
	 * <p>Reporters follow the cursor into slots; action stacks align their header
	 * with a join. A stack that misses every join lands where its ghost was drawn.
	 */
	public void drop(DragState drag, double mouseX, double mouseY) {
		rebuild();

		int aimX = toContentX(mouseX);
		int aimY = toContentY(mouseY);
		BlockLayout.DropTarget target = nearestTarget(drag, aimX);
		if (drag.isEvent() && target != null) {
			ScriptStack root = stacks.stream().filter(stack -> stack.blocks() == target.list).findFirst().orElseThrow();
			List<BlockInstance> joined = new ArrayList<>(drag.blocks());
			joined.addAll(root.blocks());
			stacks.set(stacks.indexOf(root), new ScriptStack(root.x(), root.y() - eventAdvance(drag), joined));
			pruneEmptyStacks();
			return;
		}
		if (drag.isEvent() && overStackBody(aimX, aimY)) {
			drag.restore();
			pruneEmptyStacks();
			return;
		}

		BlockLayout.SlotTarget slot = nearestSlot(drag, aimX, aimY);
		if (slot != null) {
			slot.owner.setInput(slot.spec.id(), drag.blocks().getFirst());
			pruneEmptyStacks();
			return;
		}

		// Input and compare blocks live in slots and nowhere else, so one that
		// missed goes back where it came from rather than starting a stack.
		if (drag.isReporter()) {
			drag.restore();
			pruneEmptyStacks();
			return;
		}

		if (target != null) {
			target.list.addAll(Math.min(target.index, target.list.size()), drag.blocks());
		} else {
			stacks.add(new ScriptStack(toContentX(drag.screenX()), toContentY(drag.screenY()),
					drag.blocks()));
		}
		pruneEmptyStacks();
	}

	/**
	 * The join a dragged stack would snap onto, or null when it is not close
	 * enough to any of them and should stand on its own instead.
	 *
	 * <p>The dragged header's top edge finds a join across the target's full width.
	 * Either the cursor or the header's left edge can line up horizontally, so
	 * wide blocks remain easy to connect regardless of where they were grabbed.
	 */
	private BlockLayout.DropTarget nearestTarget(DragState drag, int contentX) {
		if (drag.isEvent()) return nearestEventTarget(drag, contentX);
		if (layout == null || layout.targets.isEmpty()) {
			return null;
		}

		BlockLayout.DropTarget best = null;
		double bestDistance = STACK_SNAP_RADIUS / zoom;
		int headerX = toContentX(drag.screenX());
		int headerY = toContentY(drag.screenY());
		for (BlockLayout.DropTarget target : layout.targets) {
			double distance = Math.min(target.distanceTo(contentX, headerY), target.distanceTo(headerX, headerY));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = target;
			}
		}
		return best;
	}

	private int eventAdvance(DragState drag) {
		return BlockLayout.stackHeight(BlockLayout.compute(drag.blocks(), 0, 0, font).nodes);
	}

	private BlockLayout.DropTarget nearestEventTarget(DragState drag, int contentX) {
		if (layout == null) return null;
		BlockLayout.DropTarget best = null;
		double bestDistance = STACK_SNAP_RADIUS / zoom;
		int headerX = toContentX(drag.screenX());
		int connectorY = toContentY(drag.screenY()) + eventAdvance(drag);
		int pointerY = toContentY(drag.screenY()) + drag.grabOffsetY();
		for (BlockLayout.DropTarget target : layout.targets) {
			if (target.index != 0 || target.list.isEmpty()) continue;
			var firstType = target.list.getFirst().type();
			if (firstType != null && (firstType.isEvent() || firstType.isReporter())) continue;
			if (stacks.stream().noneMatch(stack -> stack.blocks() == target.list)) continue;
			if (pointerY > target.y + BlockLayout.STACK_OVERLAP && overStackBody(contentX, pointerY)) continue;
			double distance = Math.min(target.distanceTo(contentX, connectorY), target.distanceTo(headerX, connectorY));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = target;
			}
		}
		return best;
	}

	private boolean overStackBody(int contentX, int contentY) {
		if (BlockLayout.hitTest(layout.nodes, contentX, contentY) != null) return true;
		for (BlockLayout.Node node : layout.nodes) {
			if (contentX >= node.x && contentX < node.x + node.headerWidth
					&& contentY >= node.y && contentY < node.y + node.height) return true;
		}
		return false;
	}

	/**
	 * The slot a dragged reporter would land in. Slots only win when the cursor
	 * is genuinely near one, so a reporter can still be dropped into the stack
	 * as an expression statement — which is how {@code =} assignments are written.
	 */
	private BlockLayout.SlotTarget nearestSlot(DragState drag, int contentX, int contentY) {
		if (layout == null || !drag.isReporter()) {
			return null;
		}

		BlockLayout.SlotTarget best = null;
		double bestDistance = SLOT_SNAP_RADIUS / zoom;
		for (BlockLayout.SlotTarget slot : layout.slotTargets) {
			// Never let a reporter be dropped into a slot on itself.
			if (contains(drag.blocks().getFirst(), slot.owner)) {
				continue;
			}
			// Shape decides: a compare block fits a hexagonal slot and nothing else,
			// and an input block fits a rounded slot and nothing else.
			if (slot.spec.acceptsOnlyBoolean() != drag.isBoolean()) {
				continue;
			}
			double distance = slot.distanceTo(contentX, contentY);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = slot;
			}
		}
		return best;
	}

	/** True if {@code candidate} is {@code root} or nested somewhere inside it. */
	private static boolean contains(BlockInstance root, BlockInstance candidate) {
		if (root == candidate) {
			return true;
		}
		var type = root.type();
		if (type == null) {
			return false;
		}
		for (var spec : type.params()) {
			BlockInstance plugged = root.input(spec.id());
			if (plugged != null && contains(plugged, candidate)) {
				return true;
			}
		}
		return false;
	}

	// --- inline editing -------------------------------------------------

	public boolean keyPressed(KeyEvent event) {
		if (choice != null) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				choice = null;
			} else if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				choice.block().setArg(choice.anchor().spec.id(), choice.anchor().spec.choices().get(choiceSelected));
				choice = null;
			} else if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN) {
				choiceSelected = Math.clamp(choiceSelected + (event.key() == InputConstants.KEY_UP ? -1 : 1),
						0, choice.anchor().spec.choices().size() - 1);
				keepChoiceVisible();
			}
			return true;
		}
		if (edit == null) {
			return false;
		}
		// A key slot takes the next press as its value — including enter, which
		// would otherwise close a text field.
		if (edit.capturing()) {
			if (supportsCombination(edit.block()) && isModifier(event.key())) {
				if (!pendingModifiers.contains(event.key())) pendingModifiers.add(event.key());
				return true;
			}
			if (event.key() != InputConstants.KEY_ESCAPE) {
				edit.block().setArg(edit.paramId(), capturedName(InputConstants.Type.KEYBOARD.getOrCreate(event.key())));
			}
			edit = null;
			pendingModifiers.clear();
			return true;
		}
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER
				|| event.key() == InputConstants.KEY_ESCAPE) {
			commitEdit();
			return true;
		}
		return edit.field().keyPressed(event);
	}

	/**
	 * The name a captured input is stored under, taken from Minecraft's own id:
	 * {@code key.keyboard.space} becomes "space" and {@code key.mouse.left}
	 * becomes "mouse.left", which is exactly what
	 * {@link com.minimacros.runtime.InputSimulator#resolveKey} reads back.
	 */
	private static String nameOf(InputConstants.Key key) {
		String name = key.getName();
		if (name.startsWith("key.keyboard.")) {
			return name.substring("key.keyboard.".length());
		}
		return name.startsWith("key.") ? name.substring("key.".length()) : name;
	}

	public boolean charTyped(CharacterEvent event) {
		return choice != null || edit != null && !edit.capturing() && edit.field().charTyped(event);
	}

	public boolean keyReleased(KeyEvent event) {
		if (edit == null || !edit.capturing() || !supportsCombination(edit.block())
				|| !pendingModifiers.contains(event.key())) return false;
		List<String> names = new ArrayList<>();
		for (int modifier : MODIFIER_KEYS) {
			if (pendingModifiers.contains(modifier)) names.add(nameOf(InputConstants.Type.KEYBOARD.getOrCreate(modifier)));
		}
		edit.block().setArg(edit.paramId(), String.join(" + ", names));
		edit = null;
		pendingModifiers.clear();
		return true;
	}

	/** Writes the open editor's text back into the block and closes it. */
	public void commitEdit() {
		choice = null;
		pendingModifiers.clear();
		if (edit != null) {
			if (!edit.capturing()) {
				Minecraft.getInstance().textInputManager().onTextInputFocusChange(this, false);
				edit.block().setArg(edit.paramId(), edit.field().value());
			}
			edit = null;
		}
	}

	public boolean choiceOpen() { return choice != null; }

	private ChoiceBounds choiceBounds() {
		int rowHeight = font.lineHeight + 6;
		int rows = Math.max(1, Math.min(choice.anchor().spec.choices().size(), (int) (height / zoom) / rowHeight));
		int menuWidth = choice.anchor().width;
		for (String option : choice.anchor().spec.choices()) menuWidth = Math.max(menuWidth, font.width(option) + 12);
		menuWidth = Math.max(1, Math.min(menuWidth, (int) (width / zoom)));
		int minX = (int) Math.ceil(scrollX);
		int maxX = (int) Math.floor(scrollX + width / zoom) - menuWidth;
		int minY = (int) Math.ceil(scrollY);
		int maxY = (int) Math.floor(scrollY + height / zoom) - rows * rowHeight;
		int menuY = choice.anchor().y + choice.anchor().height;
		if (menuY > maxY) menuY = choice.anchor().y - rows * rowHeight;
		return new ChoiceBounds(Math.clamp(choice.anchor().x, minX, Math.max(minX, maxX)),
				Math.clamp(menuY, minY, Math.max(minY, maxY)), menuWidth, rows, rowHeight);
	}

	private void keepChoiceVisible() {
		int rows = choiceBounds().rows();
		choiceScroll = Math.clamp(choiceScroll, Math.max(0, choiceSelected - rows + 1), choiceSelected);
	}

	private void renderChoice(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ChoiceBounds bounds = choiceBounds();
		graphics.nextStratum();
		graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
				bounds.y() + bounds.rows() * bounds.rowHeight(), Theme.PARAM_FILL);
		for (int row = 0; row < bounds.rows(); row++) {
			int option = choiceScroll + row;
			int top = bounds.y() + row * bounds.rowHeight();
			if (option == choiceSelected || mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
					&& mouseY >= top && mouseY < top + bounds.rowHeight()) {
				graphics.fill(bounds.x(), top, bounds.x() + bounds.width(), top + bounds.rowHeight(), Theme.ROW_SELECTED);
			}
			graphics.text(font, font.plainSubstrByWidth(choice.anchor().spec.choices().get(option), Math.max(1, bounds.width() - 8)),
					bounds.x() + 4, BlockLayout.textTop(top, bounds.rowHeight(), font), Theme.PARAM_TEXT);
		}
		graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.rows() * bounds.rowHeight(), Theme.PARAM_FOCUSED);
	}

	static boolean supportsCombination(BlockInstance block) {
		String id = block.typeId();
		return id.startsWith("events.") || id.startsWith("input.is_") || id.startsWith("input.was_")
				|| id.equals("control.wait_next_press");
	}

	static boolean isModifier(int key) {
		for (int modifier : MODIFIER_KEYS) if (key == modifier) return true;
		return false;
	}

	private String capturedName(InputConstants.Key key) {
		List<Integer> modifiers = new ArrayList<>();
		if (supportsCombination(edit.block())) {
			for (int modifier : MODIFIER_KEYS) {
				if (InputConstants.isKeyDown(modifier)) modifiers.add(modifier);
			}
		}
		return combinationName(key, modifiers);
	}

	static String combinationName(InputConstants.Key key, List<Integer> modifiers) {
		List<String> names = new ArrayList<>();
		for (int modifier : MODIFIER_KEYS) {
			if (modifiers.contains(modifier)) names.add(nameOf(InputConstants.Type.KEYBOARD.getOrCreate(modifier)));
		}
		names.add(nameOf(key));
		return String.join(" + ", names);
	}

}
