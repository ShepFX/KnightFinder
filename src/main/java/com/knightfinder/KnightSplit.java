package com.knightfinder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One pinned knight as the server tells it: one per world. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KnightSplit
{
	private int world;
	private int x;
	private int y;
	private int plane;
	private String spot;
	/** "held" when a player keeps the knight in combat, "trapped" when walls do. */
	private String method;
	private int pickpockets;
	private long startedAt;
	private long updatedAt;

	static final String HELD = "held";
	static final String TRAPPED = "trapped";

	boolean isHeld()
	{
		return HELD.equals(method);
	}
}
