package com.knightfinder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class KnightFinderPanel extends PluginPanel
{
	private static final int REFRESH_THROTTLE_MS = 5000;
	private static final int BODY_WRAP_WIDTH = 160;
	/** A knight not heard from for this long is probably gone; the server drops it soon after. */
	private static final long STALE_MILLIS = 45_000L;
	private static final String DISABLED = "Shared knights are disabled";
	private static final Color GREEN = new Color(90, 200, 120);
	private static final Color BLUE = new Color(80, 155, 255);

	private final JPanel list = new JPanel();
	private final JLabel status = new JLabel(DISABLED, SwingConstants.CENTER);
	private final JButton refresh = new JButton("Refresh");
	private final Runnable refreshHandler;
	private final Consumer<Boolean> activeHandler;
	private boolean showingSplits;

	KnightFinderPanel(Runnable refreshHandler, Consumer<Boolean> activeHandler)
	{
		this.refreshHandler = refreshHandler;
		this.activeHandler = activeHandler;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Knight Finder");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.PLAIN, 18f));
		title.setForeground(Color.WHITE);
		refresh.setFont(FontManager.getRunescapeSmallFont());
		refresh.setToolTipText("Refresh now");
		refresh.setFocusable(false);
		refresh.setMargin(new Insets(0, 0, 0, 0));
		refresh.setPreferredSize(new Dimension(56, 20));
		refresh.addActionListener(event -> requestRefresh());

		JPanel titleRow = new JPanel(new BorderLayout(6, 0));
		titleRow.setOpaque(false);
		titleRow.add(title, BorderLayout.CENTER);
		titleRow.add(refresh, BorderLayout.EAST);
		add(titleRow, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		// Anchoring the list to the top stops BoxLayout from centring the cards in the panel.
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(list, BorderLayout.NORTH);
		add(wrapper, BorderLayout.CENTER);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(Color.LIGHT_GRAY);
		status.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		add(status, BorderLayout.SOUTH);
	}

	@Override
	public void onActivate()
	{
		activeHandler.accept(true);
	}

	@Override
	public void onDeactivate()
	{
		activeHandler.accept(false);
	}

	void setDisabled()
	{
		list.removeAll();
		showingSplits = false;
		status.setText(DISABLED);
		revalidate();
		repaint();
	}

	void setLoading()
	{
		// Only announce a refresh when there is nothing on screen, otherwise the status flickers every cycle.
		if (!showingSplits) status.setText("Looking for knights...");
	}

	void setError(String message)
	{
		status.setText(message);
	}

	void setSplits(List<KnightSplit> incoming, int currentWorld)
	{
		long now = Instant.now().toEpochMilli();
		List<KnightSplit> sorted = incoming == null ? new ArrayList<>() : new ArrayList<>(incoming);
		// Fresh reports first, and among those the busiest knight, which is the one most surely still there.
		sorted.sort(Comparator.comparing((KnightSplit split) -> now - split.getUpdatedAt() >= STALE_MILLIS)
			.thenComparing(Comparator.comparingInt(KnightSplit::getPickpockets).reversed())
			.thenComparingInt(KnightSplit::getWorld));
		list.removeAll();
		if (sorted.isEmpty())
		{
			list.add(Box.createVerticalStrut(12));
			list.add(placeholder("No pinned knights right now"));
		}
		else
		{
			for (int i = 0; i < sorted.size(); i++)
			{
				if (i > 0) list.add(Box.createVerticalStrut(6));
				list.add(card(sorted.get(i), currentWorld, now));
			}
		}
		showingSplits = !sorted.isEmpty();
		status.setText(sorted.size() + (sorted.size() == 1 ? " pinned knight" : " pinned knights"));
		revalidate();
		repaint();
	}

	private JLabel placeholder(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(CENTER_ALIGNMENT);
		return label;
	}

	private void requestRefresh()
	{
		// Throttle manual refreshes so the button cannot be used to hammer the server.
		refresh.setEnabled(false);
		Timer unlock = new Timer(REFRESH_THROTTLE_MS, event -> refresh.setEnabled(true));
		unlock.setRepeats(false);
		unlock.start();
		refreshHandler.run();
	}

	private JPanel card(KnightSplit split, int currentWorld, long now)
	{
		boolean stale = now - split.getUpdatedAt() >= STALE_MILLIS;
		boolean here = currentWorld > 0 && currentWorld == split.getWorld();
		Color accent = split.isHeld() ? GREEN : BLUE;
		if (stale) accent = accent.darker().darker();

		JPanel card = new JPanel(new BorderLayout(0, 3));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(6, 7, 6, 7)));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JLabel world = new JLabel("World " + split.getWorld());
		world.setFont(FontManager.getRunescapeBoldFont());
		world.setForeground(stale ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);

		JLabel method = new JLabel(split.isHeld() ? "Held by splasher" : "Trapped");
		method.setFont(FontManager.getRunescapeSmallFont());
		method.setForeground(accent);
		method.setToolTipText(split.isHeld()
			? "A player is keeping the knight in combat - it stays as long as they do"
			: "The knight is boxed in by walls - it stays until someone lets it out");

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.add(world, BorderLayout.WEST);
		header.add(method, BorderLayout.CENTER);
		if (here) header.add(hereLabel(), BorderLayout.EAST);

		// Wrapped, so a long spot name and the count fit the sidebar instead of being clipped.
		JLabel body = new JLabel("<html><div width=" + BODY_WRAP_WIDTH + ">"
			+ escape(split.getSpot() == null ? "Unknown spot" : split.getSpot())
			+ " · " + KnightOverlay.people(split.getPickpockets()) + "</div></html>");
		body.setForeground(stale ? Color.GRAY : ColorScheme.LIGHT_GRAY_COLOR);

		JLabel pinned = new JLabel("pinned " + minutes(now - split.getStartedAt()));
		pinned.setFont(FontManager.getRunescapeSmallFont());
		pinned.setForeground(Color.GRAY);
		pinned.setToolTipText("How long this knight has been reported pinned");

		JLabel age = new JLabel(age(now - split.getUpdatedAt()));
		age.setFont(FontManager.getRunescapeSmallFont());
		age.setForeground(Color.GRAY);
		if (stale) age.setToolTipText("Nobody has seen this knight for a while - it may have got away");

		JPanel footer = new JPanel(new BorderLayout(6, 0));
		footer.setOpaque(false);
		footer.add(pinned, BorderLayout.WEST);
		footer.add(age, BorderLayout.EAST);

		card.add(header, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		card.add(footer, BorderLayout.SOUTH);
		// Cards size to their content instead of a hardcoded height, so nothing is clipped.
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JComponent hereLabel()
	{
		JLabel current = new JLabel("Here");
		current.setFont(FontManager.getRunescapeSmallFont());
		current.setForeground(GREEN);
		current.setToolTipText("You are on this world");
		return current;
	}

	static String minutes(long millis)
	{
		long minutes = Math.max(0, millis) / 60_000L;
		if (minutes < 1) return "under a minute";
		if (minutes < 60) return minutes + "m";
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	static String age(long millis)
	{
		long seconds = Math.max(0, millis) / 1000L;
		if (seconds < 60) return seconds + "s ago";
		return (seconds / 60) + "m ago";
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
