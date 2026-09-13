package com.knightfinder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KnightFinderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KnightFinderPlugin.class);
		RuneLite.main(args);
	}
}
