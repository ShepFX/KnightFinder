package com.knightfinder;

import net.runelite.api.coords.WorldPoint;

/**
 * Names the place a knight is pinned so a listing reads as somewhere to walk to rather than a
 * coordinate. The bands are deliberately broad: the label is a hint, and the coordinates travel
 * with the report for anything that needs to be exact.
 */
final class KnightSpot
{
	static final String SOUTH_BANK = "South bank";
	static final String NORTH_HOUSE = "House north of the market";
	static final String MARKET = "Market";
	static final String WEST_ARDOUGNE = "West Ardougne";
	static final String ELSEWHERE = "Elsewhere";

	private KnightSpot()
	{
	}

	static String forPoint(WorldPoint point)
	{
		int x = point.getX();
		int y = point.getY();
		if (x >= 2624 && x <= 2687 && y >= 3264 && y <= 3391)
		{
			if (y <= 3294) return SOUTH_BANK;
			if (y >= 3319) return NORTH_HOUSE;
			return MARKET;
		}
		if (x >= 2440 && x < 2624 && y >= 3264 && y <= 3391) return WEST_ARDOUGNE;
		return ELSEWHERE;
	}
}
