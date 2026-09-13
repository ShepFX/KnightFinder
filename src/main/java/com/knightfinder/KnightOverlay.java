package com.knightfinder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Outlines the pinned knight on this world, with how it is pinned and how many people are on it. */
class KnightOverlay extends Overlay
{
	private static final Color PINNED = new Color(90, 200, 120);
	private final KnightFinderPlugin plugin;
	private final KnightFinderConfig config;

	@Inject
	KnightOverlay(KnightFinderPlugin plugin, KnightFinderConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		KnightTracker.Split split = plugin.getCurrentSplit();
		if (split == null || !config.showOverlay()) return null;
		NPC knight = plugin.knight(split.getNpcIndex());
		if (knight == null) return null;
		Shape hull = knight.getConvexHull();
		if (hull != null) OverlayUtil.renderPolygon(graphics, hull, PINNED);
		String text = (split.isHeld() ? "Held" : "Trapped") + " · " + people(split.getPickpockets())
			+ " · " + duration(plugin.pinnedTicks());
		Point location = knight.getCanvasTextLocation(graphics, text, knight.getLogicalHeight() + 40);
		if (location != null) OverlayUtil.renderTextLocation(graphics, location, text, PINNED);
		return null;
	}

	static String people(int count)
	{
		return count == 1 ? "1 pickpocketing" : count + " pickpocketing";
	}

	static String duration(int ticks)
	{
		int seconds = (int) (ticks * 0.6);
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}
}
