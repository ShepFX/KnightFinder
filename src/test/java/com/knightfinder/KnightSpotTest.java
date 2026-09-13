package com.knightfinder;

import static org.junit.Assert.assertEquals;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class KnightSpotTest
{
	@Test
	public void namesTheKnownSpots()
	{
		assertEquals(KnightSpot.SOUTH_BANK, KnightSpot.forPoint(new WorldPoint(2655, 3283, 0)));
		assertEquals(KnightSpot.NORTH_HOUSE, KnightSpot.forPoint(new WorldPoint(2665, 3322, 0)));
		assertEquals(KnightSpot.MARKET, KnightSpot.forPoint(new WorldPoint(2660, 3305, 0)));
		assertEquals(KnightSpot.WEST_ARDOUGNE, KnightSpot.forPoint(new WorldPoint(2530, 3310, 0)));
		assertEquals(KnightSpot.ELSEWHERE, KnightSpot.forPoint(new WorldPoint(3200, 3200, 0)));
	}
}
