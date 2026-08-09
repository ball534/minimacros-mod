package com.minimacros.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One placed block. Pure data — Gson round-trips this directly, and both the
 * editor and the interpreter read it without any extra bookkeeping.
 *
 * <p>{@link #branches} is empty for simple blocks, has one entry for
 * {@code repeat}/{@code forever}/{@code if}, and two for {@code if/else}.
 */
public class BlockInstance {
	private String type;
	private Map<String, String> args = new LinkedHashMap<>();
	private List<List<BlockInstance>> branches = new ArrayList<>();
	/** Reporter blocks plugged into SLOT params, keyed by param id. */
	private Map<String, BlockInstance> inputs = new LinkedHashMap<>();

	@SuppressWarnings("unused") // Gson
	BlockInstance() {
	}

	public BlockInstance(String type) {
		this.type = type;
	}

	/** Builds an instance with every param seeded from its spec's default. */
	public static BlockInstance create(BlockType blockType) {
		BlockInstance block = new BlockInstance(blockType.id());
		for (ParamSpec param : blockType.params()) {
			block.args.put(param.id(), param.defaultValue());
		}
		for (int i = 0; i < blockType.branchCount(); i++) {
			block.branches.add(new ArrayList<>());
		}
		return block;
	}

	public String typeId() {
		return type;
	}

	/** May be null if a macro references a block this version no longer knows. */
	public BlockType type() {
		return BlockRegistry.get(type);
	}

	public String arg(String id) {
		String value = args.get(id);
		return value == null ? "" : value;
	}

	public void setArg(String id, String value) {
		args.put(id, value);
	}

	/** The reporter plugged into {@code paramId}, or null if the slot is typed in. */
	public BlockInstance input(String paramId) {
		return inputs.get(paramId);
	}

	public void setInput(String paramId, BlockInstance block) {
		inputs.put(paramId, block);
	}

	public void removeInput(String paramId) {
		inputs.remove(paramId);
	}

	public List<BlockInstance> branch(int index) {
		return branches.get(index);
	}

	public BlockInstance copy() {
		BlockInstance clone = new BlockInstance(type);
		clone.args.putAll(args);
		for (Map.Entry<String, BlockInstance> input : inputs.entrySet()) {
			clone.inputs.put(input.getKey(), input.getValue().copy());
		}
		for (List<BlockInstance> branch : branches) {
			List<BlockInstance> branchCopy = new ArrayList<>(branch.size());
			for (BlockInstance child : branch) {
				branchCopy.add(child.copy());
			}
			clone.branches.add(branchCopy);
		}
		return clone;
	}

	/**
	 * Repairs a freshly deserialised tree: Gson leaves absent collections null,
	 * and a block type may have gained params since the file was written.
	 */
	public void normalize() {
		if (args == null) {
			args = new LinkedHashMap<>();
		}
		if (branches == null) {
			branches = new ArrayList<>();
		}
		if (inputs == null) {
			inputs = new LinkedHashMap<>();
		}

		migrateReplacedBlocks();

		BlockType blockType = type();
		if (blockType != null) {
			for (ParamSpec param : blockType.params()) {
				args.putIfAbsent(param.id(), param.defaultValue());
			}
			while (branches.size() < blockType.branchCount()) {
				branches.add(new ArrayList<>());
			}
		}

		for (BlockInstance plugged : inputs.values()) {
			plugged.normalize();
		}
		for (List<BlockInstance> branch : branches) {
			for (BlockInstance child : branch) {
				child.normalize();
			}
		}
	}

	/**
	 * Rewrites blocks that were replaced by later versions, so an old script
	 * keeps working instead of loading as a row of "unknown block".
	 *
	 * <p>Runs before the params are seeded, so the rewritten type is the one
	 * whose defaults get filled in.
	 */
	private void migrateReplacedBlocks() {
		if (type == null) {
			return;
		}
		switch (type) {
			// keyDown/keyUp and clickDown/clickUp merged into buttonDown/buttonUp.
			case "input.key_down" -> {
				type = BlockRegistry.BUTTON_DOWN;
				renameArg("key", "button");
			}
			case "input.key_up" -> {
				type = BlockRegistry.BUTTON_UP;
				renameArg("key", "button");
			}
			case "input.click_down" -> {
				type = BlockRegistry.BUTTON_DOWN;
				args.put("button", mouseNameOf(arg("button")));
			}
			case "input.click_up" -> {
				type = BlockRegistry.BUTTON_UP;
				args.put("button", mouseNameOf(arg("button")));
			}
			// The operator dropdown became one block per operator.
			case "vars.operation" -> migrateOperation();
			default -> {
			}
		}
	}

	private void migrateOperation() {
		String operator = arg("op").trim();
		args.remove("op");
		if ("=".equals(operator)) {
			type = BlockRegistry.ASSIGN;
			return;
		}
		String replacement = BlockRegistry.operatorBlockId(operator);
		if (replacement != null) {
			type = replacement;
		}
	}

	private void renameArg(String from, String to) {
		String value = args.remove(from);
		if (value != null) {
			args.put(to, value);
		}
	}

	/** The old dropdown stored "Left"/"Right"/"Middle"; buttons are now key names. */
	private static String mouseNameOf(String dropdownValue) {
		return switch (dropdownValue.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "right" -> "mouse.right";
			case "middle" -> "mouse.middle";
			default -> "mouse.left";
		};
	}
}
