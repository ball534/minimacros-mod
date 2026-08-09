package com.minimacros.gui;

import com.minimacros.macro.BlockInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * A stack of blocks currently following the cursor.
 *
 * <p>Dragged blocks are removed from the script the moment the drag starts, so
 * the layout never has to special-case them and a stack can't be dropped inside
 * itself. {@link #originList} remembers where they came from so a cancelled
 * drag can put them back.
 */
public final class DragState {
	private final List<BlockInstance> blocks;
	private final List<BlockInstance> originList;
	private final int originIndex;
	/** Set when the drag was pulled out of a slot, so cancelling can put it back. */
	private BlockInstance originSlotOwner;
	private String originSlotParam;

	/** Where inside the dragged stack the cursor grabbed it. */
	private final int grabOffsetX;
	private final int grabOffsetY;

	private int screenX;
	private int screenY;

	private DragState(List<BlockInstance> blocks, List<BlockInstance> originList, int originIndex,
			int grabOffsetX, int grabOffsetY) {
		this.blocks = blocks;
		this.originList = originList;
		this.originIndex = originIndex;
		this.grabOffsetX = grabOffsetX;
		this.grabOffsetY = grabOffsetY;
	}

	/** A brand new block pulled out of the palette; nothing to restore on cancel. */
	public static DragState fromPalette(BlockInstance block, int grabOffsetX, int grabOffsetY) {
		List<BlockInstance> single = new ArrayList<>();
		single.add(block);
		return new DragState(single, null, 0, grabOffsetX, grabOffsetY);
	}

	/** Blocks detached from an existing script. */
	public static DragState fromScript(List<BlockInstance> blocks, List<BlockInstance> originList,
			int originIndex, int grabOffsetX, int grabOffsetY) {
		return new DragState(blocks, originList, originIndex, grabOffsetX, grabOffsetY);
	}

	/** A reporter pulled out of a slot. */
	public static DragState fromSlot(BlockInstance block, BlockInstance owner, String paramId,
			int grabOffsetX, int grabOffsetY) {
		List<BlockInstance> single = new ArrayList<>();
		single.add(block);
		DragState state = new DragState(single, null, 0, grabOffsetX, grabOffsetY);
		state.originSlotOwner = owner;
		state.originSlotParam = paramId;
		return state;
	}

	/** True when this drag is a single reporter, which can go into a slot. */
	public boolean isReporter() {
		if (blocks.size() != 1) {
			return false;
		}
		var type = blocks.getFirst().type();
		return type != null && type.isReporter();
	}

	/** True when this drag is a boolean hexagon, the only thing a condition takes. */
	public boolean isBoolean() {
		if (blocks.size() != 1) {
			return false;
		}
		var type = blocks.getFirst().type();
		return type != null && type.isBoolean();
	}

	public List<BlockInstance> blocks() {
		return blocks;
	}

	public int grabOffsetX() {
		return grabOffsetX;
	}

	public int grabOffsetY() {
		return grabOffsetY;
	}

	public int screenX() {
		return screenX;
	}

	public int screenY() {
		return screenY;
	}

	public void moveTo(double mouseX, double mouseY) {
		this.screenX = (int) Math.round(mouseX) - grabOffsetX;
		this.screenY = (int) Math.round(mouseY) - grabOffsetY;
	}

	/** Puts the blocks back where they were picked up. */
	public void restore() {
		if (originSlotOwner != null) {
			originSlotOwner.setInput(originSlotParam, blocks.getFirst());
		} else if (originList != null) {
			originList.addAll(Math.min(originIndex, originList.size()), blocks);
		}
	}
}
