package com.knightfinder;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Knight Finder",
	description = "Find worlds where a Knight of Ardougne is trapped or held for pickpocketing",
	tags = {"knight", "ardougne", "thieving", "pickpocket", "split", "world"}
)
public class KnightFinderPlugin extends Plugin
{
	private static final int REFRESH_SECONDS = 15;
	/** Slower polling when only the notification wants the list, since nobody is looking at it. */
	private static final int BACKGROUND_REFRESH_SECONDS = 30;
	/** Ticks between reports while nothing about the knight changes. The server forgets a knight not heard from. */
	private static final int HEARTBEAT_TICKS = 50;
	/** Ticks between reports when only the number of people on the knight changed. */
	private static final int COUNT_CHANGE_TICKS = 10;
	/** A dragon spear shove or a moment out of view should not end a report. */
	private static final int LOST_GRACE_TICKS = 10;
	private static final Set<Integer> KNIGHT_IDS = new HashSet<>(Arrays.asList(
		NpcID.KNIGHT_OF_ARDOUGNE, NpcID.KNIGHT_OF_ARDOUGNE2, NpcID.KNIGHT_OF_ARDOUGNE_F,
		NpcID.KNIGHT_OF_ARDOUGNE_WEST, NpcID.KNIGHT_OF_ARDOUGNE_WEST_VIS,
		NpcID.KNIGHT_OF_ARDOUGNE_WEST_NOOP, NpcID.KNIGHT_OF_ARDOUGNE_WEST_NOOP_VIS,
		NpcID.KNIGHT_OF_ARDOUGNE_F_WEST, NpcID.KNIGHT_OF_ARDOUGNE_F_WEST_VIS));
	/** Worlds nobody can usefully be sent to for a knight. Knights are members' content, so F2P is out as well. */
	private static final EnumSet<WorldType> UNLISTED_WORLDS = EnumSet.of(WorldType.PVP, WorldType.HIGH_RISK,
		WorldType.DEADMAN, WorldType.SEASONAL, WorldType.TOURNAMENT_WORLD, WorldType.BETA_WORLD,
		WorldType.FRESH_START_WORLD, WorldType.NOSAVE_MODE, WorldType.PVP_ARENA,
		WorldType.QUEST_SPEEDRUNNING, WorldType.LAST_MAN_STANDING, WorldType.BOUNTY);

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private KnightFinderConfig config;
	@Inject private KnightFinderClient reportClient;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ScheduledExecutorService executor;
	@Inject private OverlayManager overlayManager;
	@Inject private KnightOverlay overlay;
	@Inject private Notifier notifier;

	private final Map<Integer, NPC> knights = new HashMap<>();
	private final KnightTracker tracker = new KnightTracker();
	private final AtomicBoolean refreshInFlight = new AtomicBoolean();
	private final Set<Integer> knownWorlds = ConcurrentHashMap.newKeySet();
	/** The pinned knight on this world right now, for the overlay. Null when there is none. */
	@Getter private volatile KnightTracker.Split currentSplit;
	private int tick;
	private int pinnedSinceTick;
	private boolean reported;
	private int lastReportTick;
	private int lastReportedCount;
	private int lostTicks;
	private volatile boolean primed;
	private volatile int currentWorld;
	private volatile boolean panelActive;
	private KnightFinderPanel panel;
	private NavigationButton navigationButton;
	private ScheduledFuture<?> refreshTask;

	@Override
	protected void startUp()
	{
		panel = new KnightFinderPanel(this::requestRefresh, this::setPanelActive);
		navigationButton = NavigationButton.builder()
			.tooltip("Knight Finder")
			.icon(ImageUtil.loadImageResource(KnightFinderPlugin.class, "knight.png"))
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		overlayManager.add(overlay);
		// Enabling the plugin mid-session doesn't fire a game state change, so seed the world here.
		clientThread.invoke(() -> currentWorld = client.getGameState() == GameState.LOGGED_IN ? client.getWorld() : 0);
		restartRefreshTask();
	}

	@Override
	protected void shutDown()
	{
		stopRefreshTask();
		overlayManager.remove(overlay);
		panelActive = false;
		resetLocal();
		knownWorlds.clear();
		primed = false;
		refreshInFlight.set(false);
		currentWorld = 0;
		if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
		panel = null;
		navigationButton = null;
	}

	NPC knight(int npcIndex)
	{
		return knights.get(npcIndex);
	}

	/** How long the knight on this world has been pinned, in ticks. */
	int pinnedTicks()
	{
		return currentSplit == null ? 0 : tick - pinnedSinceTick;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (KNIGHT_IDS.contains(npc.getId())) knights.put(npc.getIndex(), npc);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		int index = event.getNpc().getIndex();
		if (knights.remove(index) != null) tracker.knightGone(index);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		// This fires for every actor in the scene, so bail before touching anything else.
		Actor actor = event.getActor();
		if (knights.isEmpty() || actor.getAnimation() != AnimationID.HUMAN_PICKPOCKET || !(actor instanceof Player)) return;
		tracker.pickpocket(actor.getName(), actor.getWorldLocation(), tick);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		for (Map.Entry<Integer, NPC> entry : knights.entrySet())
		{
			NPC npc = entry.getValue();
			tracker.knightSeen(entry.getKey(), npc.getWorldLocation(), npc.getInteracting() instanceof Player, tick);
		}
		KnightTracker.Split split = tracker.bestSplit(tick);
		if (split != null && (currentSplit == null || currentSplit.getNpcIndex() != split.getNpcIndex()))
		{
			// Pinned for at least the history the tracker needed to decide so; count from there.
			pinnedSinceTick = tick - KnightTracker.PINNED_TICKS;
		}
		currentSplit = split;
		share(split);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			currentWorld = client.getWorld();
			return;
		}
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Whatever was pinned here carries on without us; the server forgets it if nobody else is watching.
			currentWorld = 0;
			resetLocal();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!KnightFinderConfig.GROUP.equals(event.getGroup())) return;
		if (!config.sharingEnabled()) reported = false;
		restartRefreshTask();
	}

	private void resetLocal()
	{
		knights.clear();
		tracker.clear();
		currentSplit = null;
		reported = false;
		lostTicks = 0;
		lastReportedCount = 0;
	}

	private void share(KnightTracker.Split split)
	{
		if (!config.sharingEnabled() || currentWorld == 0 || !listableWorld()) return;
		if (split == null)
		{
			if (reported && ++lostTicks >= LOST_GRACE_TICKS)
			{
				reported = false;
				lostTicks = 0;
				reportClient.delete(currentWorld, () -> refresh(true), error -> { });
			}
			return;
		}
		lostTicks = 0;
		int since = tick - lastReportTick;
		boolean countChanged = split.getPickpockets() != lastReportedCount;
		if (reported && since < HEARTBEAT_TICKS && !(countChanged && since >= COUNT_CHANGE_TICKS)) return;
		reported = true;
		lastReportTick = tick;
		lastReportedCount = split.getPickpockets();
		KnightSplit report = new KnightSplit(currentWorld, split.getTile().getX(), split.getTile().getY(),
			split.getTile().getPlane(), KnightSpot.forPoint(split.getTile()),
			split.isHeld() ? KnightSplit.HELD : KnightSplit.TRAPPED, split.getPickpockets(), 0,
			Instant.now().toEpochMilli());
		reportClient.report(report, () -> refresh(true), error -> { });
	}

	private boolean listableWorld()
	{
		EnumSet<WorldType> types = client.getWorldType();
		return types.contains(WorldType.MEMBERS) && Collections.disjoint(types, UNLISTED_WORLDS);
	}

	private void setPanelActive(boolean active)
	{
		panelActive = active;
		if (active) restartRefreshTask();
		else stopRefreshTask();
	}

	private void requestRefresh()
	{
		executor.execute(() -> refresh(false));
	}

	private synchronized void restartRefreshTask()
	{
		stopRefreshTask();
		if (!config.sharingEnabled())
		{
			if (panel != null) SwingUtilities.invokeLater(panel::setDisabled);
			return;
		}
		// Poll while the sidebar is open, or while somebody wants to be told about new knights.
		if (!panelActive && !config.notifyNewSplit()) return;
		int interval = panelActive ? REFRESH_SECONDS : BACKGROUND_REFRESH_SECONDS;
		refreshTask = executor.scheduleWithFixedDelay(() -> refresh(false), 0, interval, TimeUnit.SECONDS);
	}

	private synchronized void stopRefreshTask()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
	}

	/**
	 * @param fresh true after this client changed something. Such a refresh is never dropped for a
	 *              refresh already running, and asks any cache in front of the server to stand aside.
	 */
	private void refresh(boolean fresh)
	{
		if (!config.sharingEnabled() || panel == null) return;
		// Never stack requests - a slow or unreachable server would otherwise queue one every refresh.
		if (!refreshInFlight.compareAndSet(false, true) && !fresh) return;
		int world = currentWorld;
		SwingUtilities.invokeLater(panel::setLoading);
		reportClient.list(fresh,
			splits ->
			{
				refreshInFlight.set(false);
				announce(splits, world);
				SwingUtilities.invokeLater(() -> { if (panel != null) panel.setSplits(splits, world); });
			},
			error ->
			{
				refreshInFlight.set(false);
				SwingUtilities.invokeLater(() -> { if (panel != null) panel.setError(error); });
			});
	}

	private void announce(List<KnightSplit> splits, int world)
	{
		Set<Integer> seen = new HashSet<>();
		for (KnightSplit split : splits)
		{
			seen.add(split.getWorld());
			// The first list after enabling is not news, and neither is the knight we are stood next to.
			if (primed && config.notifyNewSplit() && split.getWorld() != world && !knownWorlds.contains(split.getWorld()))
			{
				notifier.notify("A knight is pinned on World " + split.getWorld() + " (" + split.getSpot() + ")");
			}
		}
		knownWorlds.clear();
		knownWorlds.addAll(seen);
		primed = true;
	}

	@Provides
	KnightFinderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KnightFinderConfig.class);
	}
}
