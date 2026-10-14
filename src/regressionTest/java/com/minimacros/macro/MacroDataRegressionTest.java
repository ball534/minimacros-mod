package com.minimacros.macro;

import com.google.gson.Gson;

/** Headless regression checks for macro data loading and copying. */
public final class MacroDataRegressionTest {
	private static final Gson GSON = new Gson();

	private MacroDataRegressionTest() {
	}

	public static void main(String[] args) {
		normalizesNullMacroDataAndPreservesBranches();
		deepCopiesAreIndependent();
		migratesLegacyBlocks();
		migratesLegacyBindingsToEvents();
		eventAndChoiceSpecsAreRegistered();
	}

	private static void normalizesNullMacroDataAndPreservesBranches() {
		MacroDefinition macro = GSON.fromJson("""
				{
				  "modifierKeys": [null, 340],
				  "stacks": [null, {
				    "blocks": [null, {
				      "type": "control.if_else",
				      "branches": [null, [null, {
				        "type": "input.key_down",
				        "args": {"key": "A"},
				        "inputs": {"unused": null}
				      }]]
				    }]
				  }]
				}
				""", MacroDefinition.class);

		macro.normalize();
		macro.migrateLegacyKeybind();
		macro.normalize();

		check(macro.modifierKeys().size() == 1, "null modifier key was retained");
		check(macro.stacks().size() == 1, "null stack was retained");
		ScriptStack stack = macro.stacks().getFirst();
		check(stack.blocks().size() == 1, "null stack block was retained");

		BlockInstance conditional = stack.blocks().getFirst();
		check(conditional.branch(0).isEmpty(), "null first branch was not replaced in place");
		check(conditional.branch(1).size() == 1, "else branch shifted or retained a null block");
		BlockInstance migratedButton = conditional.branch(1).getFirst();
		check(BlockRegistry.BUTTON_DOWN.equals(migratedButton.typeId()), "legacy key block was not migrated");
		check("A".equals(migratedButton.arg("button")), "legacy key argument was not renamed");
		check(migratedButton.input("unused") == null, "null plugged input was retained");
	}

	private static void deepCopiesAreIndependent() {
		BlockInstance conditional = BlockInstance.create(BlockRegistry.get(BlockRegistry.IF_ELSE));
		conditional.setArg("condition", "typed condition");
		BlockInstance reporter = BlockInstance.create(BlockRegistry.get("vars.true"));
		conditional.setInput("condition", reporter);
		BlockInstance child = BlockInstance.create(BlockRegistry.get(BlockRegistry.BUTTON_DOWN));
		child.setArg("button", "A");
		conditional.branch(0).add(child);

		MacroDefinition original = new MacroDefinition("original");
		original.stacks().add(new ScriptStack(12, 34, java.util.List.of(conditional)));
		MacroDefinition copy = original.copy();

		BlockInstance copiedConditional = copy.stacks().getFirst().blocks().getFirst();
		BlockInstance copiedChild = copiedConditional.branch(0).getFirst();
		copiedChild.setArg("button", "B");
		copiedConditional.input("condition").setArg("changed", "copy");
		check("A".equals(child.arg("button")), "editing a copied branch changed the original");
		check("".equals(reporter.arg("changed")), "editing a copied input changed the original");

		child.setArg("button", "C");
		check("B".equals(copiedChild.arg("button")), "editing the original changed the copy");
	}

	private static void migratesLegacyBlocks() {
		BlockInstance operation = GSON.fromJson("""
				{"type":"vars.operation","args":{"op":"*","a":"6","b":"7"}}
				""", BlockInstance.class);
		operation.normalize();

		check("vars.op.multiply".equals(operation.typeId()), "legacy operation block was not migrated");
		check("6".equals(operation.arg("a")) && "7".equals(operation.arg("b")),
				"legacy operation operands were not preserved");
		check(operation.arg("op").isEmpty(), "legacy operation selector was not removed");
	}

	private static void migratesLegacyBindingsToEvents() {
		MacroDefinition glfwMacro = GSON.fromJson("""
				{
				  "keyCode": 82,
				  "modifierKeys": [341],
				  "stacks": [
				    {"blocks": [{"type":"messages.print","args":{"text":"one"}}]},
				    {"blocks": [{"type":"messages.print","args":{"text":"two"}}]}
				  ]
				}
				""", MacroDefinition.class);
		glfwMacro.normalize();
		glfwMacro.migrateLegacyKeybind();
		glfwMacro.migrateLegacyKeybindToEvents();
		assertMigratedTrigger(glfwMacro);

		int firstStackSize = glfwMacro.stacks().getFirst().blocks().size();
		glfwMacro.migrateLegacyKeybindToEvents();
		check(glfwMacro.stacks().getFirst().blocks().size() == firstStackSize,
				"event migration duplicated triggers");

		MacroDefinition sdlMacro = GSON.fromJson("""
				{
				  "keyCode": 21,
				  "modifierKeys": [224],
				  "stacks": [{"blocks": [{"type":"messages.print"}] }]
				}
				""", MacroDefinition.class);
		sdlMacro.normalize();
		sdlMacro.migrateLegacyKeybindToEvents();
		check("left.control + r".equals(sdlMacro.stacks().getFirst().blocks().getFirst().arg("button")),
				"SDL binding migration changed key or modifier names");

		MacroDefinition eventMacro = GSON.fromJson("""
				{
				  "keyCode": 21,
				  "modifierKeys": [224],
				  "stacks": [{"blocks": [
				    {"type":"events.when_released","args":{"button":"q"}},
				    {"type":"messages.print","args":{"text":"body"}}
				  ]}]
				}
				""", MacroDefinition.class);
		eventMacro.normalize();
		eventMacro.migrateLegacyKeybindToEvents();
		check(eventMacro.stacks().getFirst().blocks().size() == 2,
				"legacy migration prepended a second event to an already-triggered stack");
		check(BlockRegistry.WHEN_RELEASED.equals(
				eventMacro.stacks().getFirst().blocks().getFirst().typeId()),
				"existing event trigger was replaced");
	}

	private static void assertMigratedTrigger(MacroDefinition macro) {
		check(macro.stacks().size() == 2, "legacy macro stacks were changed");
		for (ScriptStack stack : macro.stacks()) {
			BlockInstance trigger = stack.blocks().getFirst();
			check(BlockRegistry.WHEN_PRESSED.equals(trigger.typeId()), "legacy binding did not become an event");
			check("left.control + r".equals(trigger.arg("button")), "GLFW binding names were not migrated");
			check("restart".equals(trigger.arg("mode")), "legacy binding did not use restart mode");
		}
		check(macro.keyCode() == MacroDefinition.UNBOUND, "legacy keycode was not cleared");
		check(macro.modifierKeys().isEmpty(), "legacy modifiers were not cleared");
	}

	private static void eventAndChoiceSpecsAreRegistered() {
		BlockType pressed = BlockRegistry.get(BlockRegistry.WHEN_PRESSED);
		check(pressed.isEvent(), "pressed trigger is not an event shape");
		check(!pressed.isReporter(), "event shape was marked as a reporter");
		ParamSpec mode = pressed.params().get(1);
		check(mode.kind() == ParamSpec.Kind.CHOICE, "event mode is not a choice parameter");
		check(mode.choices().equals(java.util.List.of("run once", "restart", "toggle")),
				"event mode choices changed");
		ParamSpec period = BlockRegistry.get(BlockRegistry.EVERY).params().getFirst();
		check(period.kind() == ParamSpec.Kind.NUMBER && "5".equals(period.defaultValue()),
				"period event lacks a direct five-second default");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
