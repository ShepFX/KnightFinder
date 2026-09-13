package com.knightfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class KnightTrackerTest
{
	private static final WorldPoint TILE = new WorldPoint(2655, 3283, 0);
	private static final WorldPoint NEXT_TILE = new WorldPoint(2656, 3283, 0);
	private static final WorldPoint BESIDE = new WorldPoint(2655, 3284, 0);
	private static final int KNIGHT = 7;

	private final KnightTracker tracker = new KnightTracker();

	@Test
	public void standingStillWithNobodyAroundIsNotPinned()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS, false);
		assertNull(tracker.bestSplit(tick));
	}

	@Test
	public void needsAFullWindowOfHistory()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS - 1, false);
		tracker.pickpocket("Alice", BESIDE, tick);
		assertNull(tracker.bestSplit(tick));
		tracker.knightSeen(KNIGHT, TILE, false, ++tick);
		assertNotNull(tracker.bestSplit(tick));
	}

	@Test
	public void shufflingBetweenTwoTilesWhileRobbedIsTrapped()
	{
		int tick = 0;
		for (int i = 0; i < KnightTracker.PINNED_TICKS; i++)
		{
			tracker.knightSeen(KNIGHT, i % 2 == 0 ? TILE : NEXT_TILE, false, ++tick);
		}
		tracker.pickpocket("Alice", BESIDE, tick);
		tracker.pickpocket("Bob", BESIDE, tick);
		KnightTracker.Split split = tracker.bestSplit(tick);
		assertNotNull(split);
		assertFalse(split.isHeld());
		assertEquals(2, split.getPickpockets());
		assertEquals(KNIGHT, split.getNpcIndex());
	}

	@Test
	public void wanderingKnightIsNeverPinned()
	{
		int tick = 0;
		for (int i = 0; i < KnightTracker.PINNED_TICKS; i++)
		{
			tracker.knightSeen(KNIGHT, new WorldPoint(2655 + i / 10, 3283, 0), false, ++tick);
		}
		tracker.pickpocket("Alice", new WorldPoint(2659, 3283, 0), tick);
		assertNull(tracker.bestSplit(tick));
	}

	@Test
	public void fightingKnightCountsAsHeldWithoutPickpockets()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS, true);
		KnightTracker.Split split = tracker.bestSplit(tick);
		assertNotNull(split);
		assertTrue(split.isHeld());
		assertEquals(0, split.getPickpockets());
	}

	@Test
	public void briefFightIsNotHeld()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS - KnightTracker.HELD_TICKS + 1, false);
		tick = stand(TILE, KnightTracker.HELD_TICKS - 1, true, tick);
		assertNull(tracker.bestSplit(tick));
	}

	@Test
	public void pickpocketsExpire()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS, false);
		tracker.pickpocket("Alice", BESIDE, tick);
		assertEquals(1, tracker.bestSplit(tick).getPickpockets());
		tick = stand(TILE, KnightTracker.PICKPOCKET_TICKS + 1, false, tick);
		assertNull(tracker.bestSplit(tick));
	}

	@Test
	public void samePlayerCountsOnce()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS, false);
		tracker.pickpocket("Alice", BESIDE, tick - 3);
		tracker.pickpocket("Alice", BESIDE, tick);
		assertEquals(1, tracker.bestSplit(tick).getPickpockets());
	}

	@Test
	public void pickpocketAwayFromTheKnightIsIgnored()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS, false);
		tracker.pickpocket("Alice", new WorldPoint(2657, 3283, 0), tick);
		tracker.pickpocket("Bob", new WorldPoint(2655, 3284, 1), tick);
		assertNull(tracker.bestSplit(tick));
	}

	@Test
	public void knightLeavingForgetsItsHistory()
	{
		int tick = stand(TILE, KnightTracker.PINNED_TICKS, true);
		tracker.knightGone(KNIGHT);
		assertNull(tracker.bestSplit(tick));
		tracker.knightSeen(KNIGHT, TILE, true, ++tick);
		assertNull(tracker.bestSplit(tick));
	}

	@Test
	public void busiestKnightWins()
	{
		int tick = 0;
		WorldPoint far = new WorldPoint(2665, 3322, 0);
		for (int i = 0; i < KnightTracker.PINNED_TICKS; i++)
		{
			tick++;
			tracker.knightSeen(KNIGHT, TILE, true, tick);
			tracker.knightSeen(KNIGHT + 1, far, false, tick);
		}
		tracker.pickpocket("Alice", new WorldPoint(2665, 3321, 0), tick);
		assertEquals(KNIGHT + 1, tracker.bestSplit(tick).getNpcIndex());
		tracker.pickpocket("Bob", BESIDE, tick);
		// Equal counts: the held knight is the surer bet.
		assertEquals(KNIGHT, tracker.bestSplit(tick).getNpcIndex());
	}

	private int stand(WorldPoint tile, int ticks, boolean fighting)
	{
		return stand(tile, ticks, fighting, 0);
	}

	private int stand(WorldPoint tile, int ticks, boolean fighting, int tick)
	{
		for (int i = 0; i < ticks; i++)
		{
			tracker.knightSeen(KNIGHT, tile, fighting, ++tick);
		}
		return tick;
	}
}
