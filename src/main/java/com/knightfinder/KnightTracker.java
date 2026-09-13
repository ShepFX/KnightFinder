package com.knightfinder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Decides whether a Knight of Ardougne is being kept in one place for pickpocketing, from nothing
 * more than what the client can see: where the knight has been, whether it is fighting someone,
 * and who has been picking its pocket.
 *
 * A trapped knight shuffles between two tiles of a house; a knight held by a splasher stands still
 * in combat. A free knight wanders the market, and only pauses for a few seconds at a time.
 */
class KnightTracker
{
	/** Ticks of movement that must fit in a two tile box before a knight counts as pinned. */
	static final int PINNED_TICKS = 50;
	/** A pickpocket older than this no longer counts towards the number of people on the knight. */
	static final int PICKPOCKET_TICKS = 25;
	/** Ticks of the window a knight must spend fighting to count as held by a splasher. */
	static final int HELD_TICKS = 20;
	private static final int MAX_SPREAD = 1;

	private final Map<Integer, Knight> knights = new HashMap<>();

	/** Records where a knight is this tick, and whether it is fighting a player. */
	void knightSeen(int index, WorldPoint tile, boolean fighting, int tick)
	{
		Knight knight = knights.computeIfAbsent(index, ignored -> new Knight());
		knight.observe(tile, fighting);
		knight.expirePickpockets(tick);
	}

	void knightGone(int index)
	{
		knights.remove(index);
	}

	/**
	 * Records a pickpocket by the named player standing at {@code from}. It counts towards every
	 * knight within reach, which in practice is the one they are stood next to.
	 */
	void pickpocket(String player, WorldPoint from, int tick)
	{
		if (player == null || from == null) return;
		for (Knight knight : knights.values())
		{
			WorldPoint tile = knight.latest();
			if (tile != null && tile.getPlane() == from.getPlane() && tile.distanceTo(from) <= 1)
			{
				knight.pickpockets.put(player, tick);
			}
		}
	}

	/** The pinned knight worth telling people about, or null when nothing here is pinned. */
	Split bestSplit(int tick)
	{
		Split best = null;
		for (Map.Entry<Integer, Knight> entry : knights.entrySet())
		{
			Split split = entry.getValue().split(entry.getKey(), tick);
			if (split != null && (best == null || split.outranks(best))) best = split;
		}
		return best;
	}

	void clear()
	{
		knights.clear();
	}

	private static final class Knight
	{
		private final Deque<Observation> recent = new ArrayDeque<>(PINNED_TICKS);
		private final Map<String, Integer> pickpockets = new HashMap<>();

		void observe(WorldPoint tile, boolean fighting)
		{
			if (recent.size() == PINNED_TICKS) recent.removeFirst();
			recent.addLast(new Observation(tile, fighting));
		}

		void expirePickpockets(int tick)
		{
			pickpockets.values().removeIf(at -> tick - at > PICKPOCKET_TICKS);
		}

		WorldPoint latest()
		{
			return recent.isEmpty() ? null : recent.getLast().tile;
		}

		Split split(int index, int tick)
		{
			if (recent.size() < PINNED_TICKS) return null;
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
			int plane = recent.getFirst().tile.getPlane();
			int fightingTicks = 0;
			for (Observation observation : recent)
			{
				WorldPoint tile = observation.tile;
				if (tile.getPlane() != plane) return null;
				minX = Math.min(minX, tile.getX());
				maxX = Math.max(maxX, tile.getX());
				minY = Math.min(minY, tile.getY());
				maxY = Math.max(maxY, tile.getY());
				if (observation.fighting) fightingTicks++;
			}
			if (maxX - minX > MAX_SPREAD || maxY - minY > MAX_SPREAD) return null;
			expirePickpockets(tick);
			boolean held = fightingTicks >= HELD_TICKS;
			// A knight that merely paused in the street is neither being fought nor robbed.
			if (!held && pickpockets.isEmpty()) return null;
			return new Split(index, latest(), held, pickpockets.size());
		}
	}

	@Value
	private static class Observation
	{
		WorldPoint tile;
		boolean fighting;
	}

	@Value
	static class Split
	{
		int npcIndex;
		WorldPoint tile;
		/** True when a player is keeping the knight in combat, false when it is trapped by walls. */
		boolean held;
		int pickpockets;

		boolean outranks(Split other)
		{
			if (pickpockets != other.pickpockets) return pickpockets > other.pickpockets;
			return held && !other.held;
		}
	}
}
