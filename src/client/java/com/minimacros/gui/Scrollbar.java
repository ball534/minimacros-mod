package com.minimacros.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A vanilla scrollbar that also owns the scroll position it stands for.
 *
 * <p>Keeping the two together is what lets the bar be dragged: the thumb's
 * geometry and the value under it are worked out from the same numbers, so a
 * drag can be turned straight back into a scroll position without the bar and
 * the rows it moves ever disagreeing.
 */
public final class Scrollbar {
	/** Shortest the thumb is allowed to get, so it stays grabbable on long lists. */
	private static final int MIN_THUMB_HEIGHT = 32;

	private double amount;
	private boolean dragging;
	/** Where inside the thumb it was taken hold of, so it doesn't jump on grab. */
	private double grabOffset;

	private int trackX;
	private int trackY;
	private int trackHeight;
	private int contentHeight;

	/**
	 * Points the bar down the right edge of a panel. Called before drawing and
	 * before every hit test, so the geometry is never a frame out of date.
	 */
	public void setTrack(int panelX, int panelY, int panelWidth, int panelHeight, int contentHeight) {
		this.trackX = panelX + panelWidth - Panels.SCROLLBAR_WIDTH;
		this.trackY = panelY;
		this.trackHeight = panelHeight;
		this.contentHeight = contentHeight;
		this.amount = Math.clamp(amount, 0, maxScroll());
	}

	public double amount() {
		return amount;
	}

	public void reset() {
		amount = 0;
	}

	public boolean dragging() {
		return dragging;
	}

	public int maxScroll() {
		return Math.max(0, contentHeight - trackHeight);
	}

	/** Vanilla hides the bar entirely when everything already fits. */
	public boolean scrollable() {
		return trackHeight > 0 && maxScroll() > 0;
	}

	private int thumbHeight() {
		int longest = Math.max(1, trackHeight - 8);
		return Math.clamp((long) trackHeight * trackHeight / Math.max(1, contentHeight),
				Math.min(MIN_THUMB_HEIGHT, longest), longest);
	}

	private int thumbY() {
		return trackY + (int) (amount * (trackHeight - thumbHeight()) / maxScroll());
	}

	public boolean isOver(double mouseX, double mouseY) {
		return scrollable()
				&& mouseX >= trackX && mouseX < trackX + Panels.SCROLLBAR_WIDTH
				&& mouseY >= trackY && mouseY < trackY + trackHeight;
	}

	/**
	 * Takes hold of the bar. Pressing the track rather than the thumb brings the
	 * thumb to the cursor first, so one press both jumps and starts dragging.
	 *
	 * @return true if the press landed on the bar and was taken.
	 */
	public boolean mouseClicked(double mouseX, double mouseY) {
		if (!isOver(mouseX, mouseY)) {
			return false;
		}
		int height = thumbHeight();
		int top = thumbY();
		dragging = true;

		if (mouseY >= top && mouseY < top + height) {
			grabOffset = mouseY - top;
		} else {
			grabOffset = height / 2.0;
			mouseDragged(mouseY);
		}
		return true;
	}

	public void mouseDragged(double mouseY) {
		int travel = trackHeight - thumbHeight();
		if (!dragging || !scrollable() || travel <= 0) {
			return;
		}
		double top = mouseY - grabOffset - trackY;
		amount = Math.clamp(top * maxScroll() / travel, 0, maxScroll());
	}

	public void mouseReleased() {
		dragging = false;
	}

	/** One notch of the wheel, in whatever step the panel scrolls by. */
	public void scrollBy(double notches, double step) {
		amount = Math.clamp(amount - notches * step, 0, maxScroll());
	}

	public void render(GuiGraphicsExtractor graphics) {
		if (!scrollable()) {
			return;
		}
		Panels.scrollbar(graphics, trackX, trackY, trackHeight, thumbY(), thumbHeight());
	}
}
