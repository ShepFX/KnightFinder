package com.knightfinder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(KnightFinderConfig.GROUP)
public interface KnightFinderConfig extends Config
{
	String GROUP = "knight-finder";

	@ConfigItem(
		keyName = "sharingEnabled",
		name = "Enable shared knights",
		description = "Shares knights pinned on your world and lists the ones other plugin users have found",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean sharingEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Highlight pinned knight",
		description = "Outlines a knight on your world once it counts as pinned, with how many people are on it",
		position = 1
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyNewSplit",
		name = "Notify on new knight",
		description = "Sends a notification when a pinned knight appears on a world you are not on",
		position = 2
	)
	default boolean notifyNewSplit()
	{
		return false;
	}
}
