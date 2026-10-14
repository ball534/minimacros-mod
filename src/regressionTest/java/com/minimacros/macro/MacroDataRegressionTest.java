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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
