package com.minimacros.runtime;

import com.minimacros.macro.BlockInstance;
import com.minimacros.macro.BlockRegistry;
import com.minimacros.macro.MacroDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Exercises Minecraft's real screen-to-EditBox character input without a native window. */
public final class TextInputRegressionTest {
	private static final Font FONT = new Font(null) {
		@Override public int width(String value) { return value.codePointCount(0, value.length()) * 6; }
		@Override public String plainSubstrByWidth(String value, int width) {
			return plainSubstrByWidth(value, width, false);
		}
		@Override public String plainSubstrByWidth(String value, int width, boolean reverse) {
			int count = value.codePointCount(0, value.length());
			int shown = Math.min(count, Math.max(0, width) / 6);
			return reverse ? value.substring(value.offsetByCodePoints(0, count - shown))
					: value.substring(0, value.offsetByCodePoints(0, shown));
		}
	};

	public static void main(String[] args) {
		InputSimulator input = new InputSimulator();
		TextScreen screen = new TextScreen();
		int[] opened = {0};
		check(input.typeText(null, "  Hello, 世界 😀!  ", () -> {
			opened[0]++;
			return screen;
		}), "typing failed after opening a destination");
		check(opened[0] == 1 && screen.field.getValue().equals("  Hello, 世界 😀!  "),
				"text was lost, trimmed, reordered or split into invalid characters");
		check(screen.keyPresses == 0, "typing submitted text or generated a key press");

		screen.field.setValue("abcXYZdef");
		screen.field.setCursorPosition(3);
		screen.field.setHighlightPos(6);
		check(input.typeText(screen, "new", () -> { throw new AssertionError("replaced the existing screen"); }),
				"typing into an existing field failed");
		check(screen.field.getValue().equals("abcnewdef"), "typing ignored the cursor or selection");

		screen.focused = false;
		check(!input.typeIntoScreen(screen, "ignored") && screen.field.getValue().equals("abcnewdef"),
				"typing changed an unfocused field or silently reported success");
		screen.focused = true;
		screen.field.active = false;
		check(!input.typeIntoScreen(screen, "ignored"), "disabled field accepted typing");
		screen.field.active = true;
		// Avoid native IME focus notifications while setting up this headless field.
		screen.focused = false;
		screen.field.setEditable(false);
		screen.focused = true;
		check(!input.typeIntoScreen(screen, "ignored"), "read-only field accepted typing");
		screen.focused = false;
		screen.field.setEditable(true);
		screen.focused = true;
		screen.field.setValue("");
		screen.field.setMaxLength(4);
		input.typeIntoScreen(screen, "abcdef");
		check(screen.field.getValue().equals("abcd"), "typing bypassed the field length limit");
		check(!input.typeText(null, "hello", () -> null), "missing destination reported success");
		check(input.typeText(null, "", () -> { throw new AssertionError("empty text opened chat"); }),
				"empty text was not a harmless no-op");

		textValuesAndSavedBlocks();
		System.out.println("Text input regression checks passed");
	}

	private static void textValuesAndSavedBlocks() {
		check(BlockRegistry.get("input.echo").label().equals("type text %s"), "saved echo blocks lost compatibility");
		ExecutionContext context = new ExecutionContext(new MacroExecution(new MacroDefinition("Text test")), new InputSimulator());
		context.assign("name", "Alex");
		BlockInstance block = BlockInstance.create(BlockRegistry.get(BlockRegistry.TYPE_TEXT));
		block.setArg("text", "  Hi ${name}!  ");
		check(context.textArgument(block, "text").equals("  Hi Alex!  "), "literal whitespace or interpolation was lost");
		block.setArg("text", "name");
		check(context.textArgument(block, "text").equals("Alex"), "variable text stopped resolving");
		BlockInstance reporter = BlockInstance.create(BlockRegistry.get(BlockRegistry.VARIABLE));
		reporter.setArg("name", "name");
		block.setInput("text", reporter);
		check(context.textArgument(block, "text").equals("Alex"), "reporter text stopped resolving");
	}

	private static final class TextScreen extends Screen {
		private boolean focused = true;
		private int keyPresses;
		private final EditBox field;

		private TextScreen() {
			super(null, FONT, Component.literal("Text input test"));
			field = new EditBox(FONT, 0, 0, 240, 20, Component.literal("Input")) {
				@Override public boolean isFocused() { return focused; }
			};
			field.setMaxLength(256);
		}

		@Override public GuiEventListener getFocused() { return field; }
		@Override public boolean keyPressed(KeyEvent event) { keyPresses++; return false; }
	}

	private static void check(boolean passed, String message) {
		if (!passed) throw new AssertionError(message);
	}
}
