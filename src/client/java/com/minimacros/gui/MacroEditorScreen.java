package com.minimacros.gui;

import com.minimacros.config.MacroStorage;
import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.Keybind;
import com.minimacros.macro.MacroDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mini Macros editor.
 *
 * <p>Edits a working copy of the macro list, so Cancel really does discard.
 * Save is the only thing that writes a macro's name, keybind and blocks —
 * nothing is created or rewritten just by looking at it. Done is deliberately
 * narrower: it writes only which macros are ticked on or off, so arming a macro
 * from the list does not need the editor to save anything else.
 *
 * <p>The furniture is vanilla's: a titled header and a footer closed off by
 * separator strips, with the three panels drawn as list panels between them.
 * See {@link Panels}.
 */
public class MacroEditorScreen extends Screen {
	private static final int MARGIN = 8;
	/** Vanilla's header and footer bands, each closed off by a separator strip. */
	private static final int HEADER_HEIGHT = 32;
	private static final int FOOTER_HEIGHT = 32;
	/** A narrow gutter: the toggle in it runs the full height of the panels. */
	private static final int SPLITTER_WIDTH = 10;
	/** What Save falls back to when the name box is empty. */
	private static final String DEFAULT_NAME = "unnamed macro";
	/** The canvas keeps this much room; the palette gives way before it does. */
	private static final int MIN_CANVAS_WIDTH = 150;

	private final Screen parent;
	private final List<MacroDefinition> macros = new ArrayList<>();

	private MacroListWidget macroList;
	private ScriptCanvasWidget canvas;
	private PaletteWidget palette;

	private EditBox searchBox;
	private EditBox nameBox;
	private Button keybindButton;
	private Button paletteToggle;

	private MacroDefinition draft = new MacroDefinition("");
	private DragState drag;
	private boolean paletteOpen = true;
	private boolean capturingKeybind;

	// Panel bounds, recomputed whenever the palette opens or closes.
	private int leftWidth;
	private int contentX;
	private int toolbarY;
	private int contentTop;
	private int contentBottom;
	private int splitterX;

	public MacroEditorScreen(Screen parent) {
		super(Component.literal("Mini Macros"));
		this.parent = parent;
		for (MacroDefinition macro : MacroStorage.macros()) {
			macros.add(macro.copy());
		}
	}

	@Override
	protected void init() {
		leftWidth = Math.clamp(this.width * 22 / 100, 100, 220);
		contentX = MARGIN + leftWidth + MARGIN;
		// A row of controls under the header, then the panels, then the footer —
		// the same three bands vanilla lays its own option screens out in.
		toolbarY = HEADER_HEIGHT + 4;
		contentTop = toolbarY + Panels.WIDGET_HEIGHT + 4 + Panels.SEPARATOR_HEIGHT;
		contentBottom = this.height - FOOTER_HEIGHT - 4;

		macroList = new MacroListWidget(this.font, macros);
		macroList.setOnSelect(this::onMacroSelected);
		canvas = new ScriptCanvasWidget(this.font, this::beginDrag);
		palette = new PaletteWidget(this.font);
		addRenderableWidget(palette.searchField());

		buildLeftColumn();
		buildTopBar();
		buildBottomBar();
		buildPaletteToggle();

		layoutPanels();

		// Start on a blank draft: typing a name and hitting Save is what creates
		// a macro, so there is nothing to preselect.
		nameBox.setValue(draft.name());
		canvas.setStacks(draft.stacks());
		updateKeybindLabel();
	}

	private void buildLeftColumn() {
		searchBox = new EditBox(this.font, MARGIN, toolbarY, leftWidth, Panels.WIDGET_HEIGHT,
				Component.literal("Search"));
		searchBox.setHint(Component.literal("Search macros"));
		searchBox.setResponder(text -> macroList.setFilter(text));
		addRenderableWidget(searchBox);
	}

	private void buildTopBar() {
		int available = this.width - MARGIN - contentX;
		int gap = 6;
		int cell = (available - gap * 3) / 4;

		nameBox = new EditBox(this.font, contentX, toolbarY, cell, Panels.WIDGET_HEIGHT,
				Component.literal("Name"));
		nameBox.setHint(Component.literal("Macro name"));
		nameBox.setMaxLength(64);
		addRenderableWidget(nameBox);

		keybindButton = Button.builder(Component.literal("Keybind"), button -> {
			capturingKeybind = true;
			updateKeybindLabel();
		})
				.bounds(contentX + cell + gap, toolbarY, cell, Panels.WIDGET_HEIGHT)
				.build();
		addRenderableWidget(keybindButton);

		addRenderableWidget(Button.builder(
						Component.literal("Save").withStyle(ChatFormatting.GREEN), button -> commitDraft())
				.bounds(contentX + (cell + gap) * 2, toolbarY, cell, Panels.WIDGET_HEIGHT)
				.build());

		addRenderableWidget(Button.builder(
						Component.literal("Delete").withStyle(ChatFormatting.RED), button -> deleteSelected())
				.bounds(contentX + (cell + gap) * 3, toolbarY, cell, Panels.WIDGET_HEIGHT)
				.build());
	}

	private void buildBottomBar() {
		int buttonWidth = Math.min(150, (this.width - MARGIN * 2) / 3);
		int gap = 8;
		int total = buttonWidth * 2 + gap;
		int startX = (this.width - total) / 2;
		int barY = this.height - FOOTER_HEIGHT + (FOOTER_HEIGHT - Panels.WIDGET_HEIGHT) / 2;

		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(startX, barY, buttonWidth, Panels.WIDGET_HEIGHT)
				.build());

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
			persistEnabledFlags();
			onClose();
		})
				.bounds(startX + buttonWidth + gap, barY, buttonWidth, Panels.WIDGET_HEIGHT)
				.build());
	}

	/** The tab down the gutter that folds the block store away. */
	private void buildPaletteToggle() {
		paletteToggle = Button.builder(Component.literal(">"), button -> {
			paletteOpen = !paletteOpen;
			layoutPanels();
		}).bounds(0, contentTop, SPLITTER_WIDTH, contentBottom - contentTop).build();
		addRenderableWidget(paletteToggle);
	}

	/** Positions the three panels; rerun whenever the palette opens or closes. */
	private void layoutPanels() {
		macroList.setBounds(MARGIN, contentTop, leftWidth, contentBottom - contentTop);

		int rightEdge = this.width - MARGIN;
		int paletteWidth = 0;
		if (paletteOpen) {
			int available = this.width - MARGIN * 3 - leftWidth - SPLITTER_WIDTH;
			// Wide enough for the longest block label ("lookX ... degrees over ...").
			paletteWidth = Math.min(Math.clamp(this.width * 26 / 100, 215, 280),
					Math.max(80, available - MIN_CANVAS_WIDTH));
		}
		splitterX = rightEdge - paletteWidth - SPLITTER_WIDTH;

		canvas.setBounds(contentX, contentTop, splitterX - contentX, contentBottom - contentTop);

		if (paletteOpen) {
			palette.setBounds(splitterX + SPLITTER_WIDTH, contentTop, paletteWidth,
					contentBottom - contentTop);
		}
		palette.searchField().visible = paletteOpen
				&& palette.searchField().getY() + palette.searchField().getHeight() < contentBottom;
		palette.searchField().active = palette.searchField().visible;
		if (!paletteOpen) palette.closeCategoryMenu();
		if (!palette.searchField().visible && getFocused() == palette.searchField()) setFocused(null);

		paletteToggle.setPosition(splitterX, contentTop);
		paletteToggle.setMessage(Component.literal(paletteOpen ? ">" : "<"));
	}

	// --- macro management -----------------------------------------------

	/** Loads a saved macro into the draft. Edits stay local until Save. */
	private void onMacroSelected(MacroDefinition macro) {
		canvas.commitEdit();
		capturingKeybind = false;
		draft = macro == null ? new MacroDefinition("") : macro.copy();

		nameBox.setValue(draft.name());
		canvas.setStacks(draft.stacks());
		updateKeybindLabel();
	}

	private void updateKeybindLabel() {
		if (capturingKeybind) {
			keybindButton.setMessage(
					Component.literal("Press keys...").withStyle(ChatFormatting.YELLOW));
		} else {
			keybindButton.setMessage(draft.keyDisplayName());
		}
	}

	private MacroDefinition findByName(String name) {
		for (MacroDefinition macro : macros) {
			if (macro.name().equalsIgnoreCase(name)) {
				return macro;
			}
		}
		return null;
	}

	/**
	 * Saving is what creates a macro: the name in the box names it, and a macro
	 * already using that name is replaced.
	 *
	 * <p>An empty box saves under {@link #DEFAULT_NAME} rather than refusing, so
	 * Save always puts something in the list. Two unnamed saves land on the same
	 * name and so replace each other instead of piling up.
	 */
	private void commitDraft() {
		canvas.commitEdit();
		canvas.pruneEmptyStacks();

		String name = nameBox.getValue().trim();
		if (name.isEmpty()) {
			name = DEFAULT_NAME;
		}
		draft.setName(name);

		MacroDefinition existing = findByName(name);
		if (existing == null) {
			macros.add(draft.copy());
		} else {
			macros.set(macros.indexOf(existing), draft.copy());
		}

		persist();
		macroList.refresh();
		macroList.highlight(findByName(name));
		nameBox.setValue(name);
	}

	private void deleteSelected() {
		MacroDefinition existing = findByName(nameBox.getValue().trim());
		if (existing == null) {
			return;
		}
		macros.remove(existing);
		persist();
		macroList.refresh();
		macroList.highlight(null);
		onMacroSelected(null);
	}

	/** Hands the runtime its own copies, so editing on after a save changes nothing. */
	private void persist() {
		List<MacroDefinition> snapshot = new ArrayList<>(macros.size());
		for (MacroDefinition macro : macros) {
			snapshot.add(macro.copy());
		}
		MacroStorage.replaceAll(snapshot);
		MacroStorage.save();
	}

	/**
	 * Writes back only the arm/disarm ticks from the list, matching macros by id.
	 * Everything else the editor is holding — the draft's name, keybind and blocks
	 * — stays unsaved, because Save is what commits those.
	 */
	private void persistEnabledFlags() {
		boolean changed = false;
		for (MacroDefinition stored : MacroStorage.macros()) {
			MacroDefinition edited = findById(stored.id());
			if (edited != null && edited.enabled() != stored.enabled()) {
				stored.setEnabled(edited.enabled());
				changed = true;
			}
		}
		if (changed) {
			MacroStorage.save();
		}
	}

	private MacroDefinition findById(String id) {
		for (MacroDefinition macro : macros) {
			if (macro.id().equals(id)) {
				return macro;
			}
		}
		return null;
	}

	@Override
	public void onClose() {
		canvas.commitEdit();
		Minecraft.getInstance().setScreenAndShow(parent);
	}

	// --- dragging -------------------------------------------------------

	private void beginDrag(DragState state) {
		this.drag = state;
		moveDrag(lastMouseX, lastMouseY);
	}

	private void moveDrag(double mouseX, double mouseY) {
		drag.moveTo(mouseX, mouseY, canvas.contains(mouseX, mouseY) ? canvas.zoom() : 1.0);
	}

	private double lastMouseX;
	private double lastMouseY;

	// --- rendering ------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (drag != null) moveDrag(mouseX, mouseY);

		graphics.centeredText(this.font, getTitle(), this.width / 2,
				(HEADER_HEIGHT - this.font.lineHeight) / 2, Theme.TEXT);
		Panels.horizontalSeparator(graphics, 0, HEADER_HEIGHT - Panels.SEPARATOR_HEIGHT, this.width, true);
		Panels.horizontalSeparator(graphics, 0, this.height - FOOTER_HEIGHT, this.width, false);

		macroList.render(graphics, mouseX, mouseY);
		canvas.render(graphics, mouseX, mouseY, drag);
		if (paletteOpen) {
			palette.render(graphics, mouseX, mouseY);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (paletteOpen) palette.renderCategoryMenu(graphics, mouseX, mouseY);

		renderDragGhost(graphics);
	}

	private void renderDragGhost(GuiGraphicsExtractor graphics) {
		if (drag == null) {
			return;
		}
		graphics.nextStratum();
		moveDrag(lastMouseX, lastMouseY);
		graphics.pose().pushMatrix();
		graphics.pose().translate((float) drag.screenX(), (float) drag.screenY());
		graphics.pose().scale((float) drag.scale(), (float) drag.scale());
		BlockLayout.Layout ghost = BlockLayout.compute(drag.blocks(), 0, 0, this.font);
		BlockRenderer.drawStack(graphics, this.font, ghost.nodes, null, null);
		graphics.pose().popMatrix();
	}

	// --- input ----------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		lastMouseX = event.x();
		lastMouseY = event.y();
		if (drag != null) {
			return true;
		}

		if (paletteOpen && palette.categoryMenuOpen()) return palette.mouseClicked(event);

		if (super.mouseClicked(event, doubleClick)) {
			canvas.commitEdit();
			return true;
		}

		if (paletteOpen && palette.contains(event.x(), event.y())) {
			setFocused(null);
			canvas.commitEdit();
			if (palette.mouseClicked(event)) {
				return true;
			}
			int[] grab = new int[2];
			BlockInstance block = palette.blockAt(event.x(), event.y(), grab);
			if (block != null) {
				beginDrag(DragState.fromPalette(block, grab[0], grab[1]));
			}
			return true;
		}

		if (macroList.contains(event.x(), event.y())) {
			setFocused(null);
			canvas.commitEdit();
			return macroList.mouseClicked(event);
		}

		if (canvas.contains(event.x(), event.y())) {
			setFocused(null);
			return canvas.mouseClicked(event);
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		lastMouseX = event.x();
		lastMouseY = event.y();

		if (drag != null) {
			moveDrag(event.x(), event.y());
			return true;
		}
		// A scrollbar keeps the drag once it has it, so the thumb goes on following
		// the cursor after it has been pulled off the panel.
		if (macroList.isScrolling()) {
			macroList.mouseDragged(event.y());
			return true;
		}
		if (palette.isScrolling()) {
			palette.mouseDragged(event.y());
			return true;
		}
		if (canvas.contains(event.x(), event.y())) {
			canvas.mouseDragged(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		canvas.mouseReleased();
		macroList.mouseReleased();
		palette.mouseReleased();

		if (drag != null) {
			dropDraggedStack(event);
			drag = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	/** Palette means discard, canvas means insert, anywhere else means put it back. */
	private void dropDraggedStack(MouseButtonEvent event) {
		moveDrag(event.x(), event.y());
		if (paletteOpen && palette.contains(event.x(), event.y())) {
			// Dragged onto the store, so it is thrown away — which can leave the
			// stack it came out of with nothing in it.
			canvas.pruneEmptyStacks();
			return;
		}
		if (canvas.contains(event.x(), event.y())) {
			canvas.drop(drag, event.x(), event.y());
		} else {
			drag.restore();
			canvas.pruneEmptyStacks();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (paletteOpen && palette.contains(mouseX, mouseY)) {
			return palette.mouseScrolled(scrollY);
		}
		if (macroList.contains(mouseX, mouseY)) {
			return macroList.mouseScrolled(scrollY);
		}
		if (canvas.contains(mouseX, mouseY)) {
			return canvas.mouseScrolled(mouseX, mouseY, scrollY);
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (paletteOpen && palette.categoryMenuOpen() && event.key() == InputConstants.KEY_ESCAPE) {
			palette.closeCategoryMenu();
			return true;
		}
		if (capturingKeybind) {
			captureKeybind(event);
			return true;
		}
		if (canvas.keyPressed(event)) {
			return true;
		}
		if ((event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
			if (event.keycode() == InputConstants.KEYCODE_C && copySelectedToClipboard()) {
				return true;
			}
			if (event.keycode() == InputConstants.KEYCODE_V && pasteFromClipboard()) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	/**
	 * Builds a combination like "Left Shift + L". Modifier keys on their own do
	 * not finish the capture — it waits for the key they are modifying.
	 */
	private void captureKeybind(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			draft.clearKeybind();
		} else if (Keybind.isModifier(event.key())) {
			updateKeybindLabel();
			return;
		} else {
			draft.setKeybind(event.key(), Keybind.heldModifiers());
		}
		capturingKeybind = false;
		updateKeybindLabel();
	}

	private boolean copySelectedToClipboard() {
		if (getFocused() != null) {
			return false;
		}
		Minecraft.getInstance().keyboardHandler.setClipboard(MacroStorage.toJson(draft));
		return true;
	}

	private boolean pasteFromClipboard() {
		if (getFocused() != null) {
			return false;
		}
		MacroDefinition pasted = MacroStorage.fromJson(
				Minecraft.getInstance().keyboardHandler.getClipboard());
		if (pasted == null) {
			return false;
		}
		onMacroSelected(pasted);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return canvas.charTyped(event) || super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		canvas.commitEdit();
		super.removed();
	}
}
