package rOakChopper;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.powerbot.event.MessageEvent;
import org.powerbot.event.MessageListener;
import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.methods.Game;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rOakChopper", description = "Chops oak trees at the Grand Exchange", version = 0.1, hidden = true, instances = 30)
public class rOakChopper extends PollingScript implements PaintListener,
		MessageListener {
	public long elapsedTime = 0;
	public Timer wait;
	public final JobContainer container;
	public String status = "Starting...";
	private int logsChopped, logPrice, profitGained, logsInBank;
	private int oakLogID = 1521;
	private int[] bankerID = { 3293, 3416 }, oakID = { 38732, 38731 };
	Tile[] pathToOak = new Tile[] { new Tile(3180, 3502, 0),
			new Tile(3183, 3498, 0), new Tile(3186, 3494, 0),
			new Tile(3192, 3491, 0), new Tile(3195, 3487, 0),
			new Tile(3197, 3483, 0), new Tile(3198, 3479, 0),
			new Tile(3197, 3475, 0), new Tile(3196, 3471, 0),
			new Tile(3196, 3467, 0), new Tile(3195, 3464, 0) };

	final Area oakArea = new Area(new Tile(3197, 3468, 0), new Tile(3186, 3455,
			0));

	public rOakChopper() {
		elapsedTime = System.currentTimeMillis();
		logPrice = getGuidePrice(oakLogID);

		this.container = new JobContainer(new Job[] { new Banking(ctx),
				new Chopping(ctx) });
	}

	public abstract class Job extends MethodProvider {
		public Job(MethodContext ctx) {
			super(ctx);
		}

		public int delay() {
			return Random.nextInt(50, 100);
		}

		public int priority() {
			return 0;
		}

		public abstract boolean activate();

		public abstract void execute();
	}

	public class JobContainer {
		private List<Job> jobList = new ArrayList<>();

		public JobContainer(Job[] jobs) {
			submit(jobs);
		}

		public void submit(final Job... jobs) {
			for (Job j : jobs) {
				if (!jobList.contains(j)) {
					jobList.add(j);
				}
			}
			Collections.sort(jobList, new Comparator<Job>() {
				@Override
				public int compare(Job o1, Job o2) {
					return o2.priority() - o1.priority();
				}
			});
		}

		public void revoke(Job j) {
			if (jobList.contains(j)) {
				jobList.remove(j);
			}
		}

		public Job get() {
			for (Job j : jobList) {
				if (j.activate()) {
					return j;
				}
			}
			return null;
		}
	}

	@Override
	public int poll() {
		if (!ctx.game.isLoggedIn()
				|| ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) {
			return 1000;
		}

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return job.delay();
		}

		return Random.nextInt(50, 100);
	}

	private class Banking extends Job {
		public Banking(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() == 28;
		}

		@Override
		public void execute() {
			if (atBanker()) {
				if (ctx.bank.isOpen()) {
					status = "Banking";
					ctx.bank.depositInventory();
					sTimer(ctx.backpack.select().count() > 0, 1650, 1750);
					logsInBank = ctx.bank.select().id(oakLogID).count(true);
				} else {
					status = "Bank Open";
					ctx.bank.open();
				}
			} else {
				status = "Walk to Bank";
				ctx.movement.newTilePath(pathToOak).reverse().traverse();
			}
		}
	}

	public boolean atBanker() {
		for (Npc banker : ctx.npcs.select().id(bankerID).nearest()) {
			if (banker != null
					&& banker.isOnScreen()) {
				return true;
			}
		}
		return false;
	}

	private class Chopping extends Job {
		public Chopping(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			if (atOaks()) {
				for (GameObject oak : ctx.objects.select().id(oakID).nearest()) {
					if (ctx.players.local().getAnimation() == -1) {
						if (oak != null) {
							if (oak.isOnScreen()
									&& oakArea.contains(oak.getLocation())) {
								status = "Chop";
								oak.interact("Chop down");
								final Timer chopTimer = new Timer(
										Random.nextInt(3000, 3500));
								while (chopTimer.isRunning()
										&& ctx.players.local().getAnimation() == -1)
									sleep(Random.nextInt(150, 250));
								status = "Chopping...";
							} else {
								if (oakArea.contains(oak.getLocation())) {
									status = "Turn Camera";
									ctx.movement.stepTowards(ctx.movement.getClosestOnMap(oak.getLocation()));
								} else {
									sleep(Random.nextInt(100, 300));
								}
							}
						}
					}
				}
			} else {
				if (ctx.bank.isOpen()) {
					status = "Bank Close";
					ctx.bank.close();
				} else {
					status = "Walk to Oaks";
					ctx.movement.newTilePath(pathToOak).traverse();
				}
			}
		}
	}

	public boolean atOaks() {
		return oakArea.contains(ctx.players.local().getLocation());
	}
	
	public class Timer {
		private long end;
		private final long start;

		public Timer(final long period) {
			start = System.currentTimeMillis();
			end = start + period;
		}
		public boolean isRunning() {
			return System.currentTimeMillis() < end;
		}
	}
	
	private void sTimer(boolean wait4, int int1, int int2) {
		if (int1 == 0 || int2 == 0) {
			int1 = 1300;
			int2 = 1500;
		}
		wait = new Timer(Random.nextInt(int1, int2));
		while (wait.isRunning() && wait4) {
			sleep(Random.nextInt(5, 10));
		}
	}

	public String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - elapsedTime)));
	}

	public String formatNumber(int start) {
		DecimalFormat nf = new DecimalFormat("0.0");
		double i = start;
		if (i >= 1000000) {
			return nf.format((i / 1000000)) + "m";
		}
		if (i >= 1000) {
			return nf.format((i / 1000)) + "k";
		}
		return "" + start;
	}
	
	final static Color black = new Color(25, 0, 0, 200);
	final static Font font = new Font("Times New Roman", 0, 13);
	final static Font fontTwo = new Font("Arial", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {
		if (ctx.game.getClientState() != Game.INDEX_MAP_LOADED) {
			return;
		}

		long millis = System.currentTimeMillis() - elapsedTime;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;
		profitGained = logsChopped * logPrice;

		g.setColor(black);
		g.fillRect(6, 210, 200, 145);
		g.setColor(Color.RED);
		g.drawRect(6, 210, 200, 145);
		g.setFont(fontTwo);
		g.drawString("rOakChopper", 75, 222);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13,
				245);
		g.drawString("Chopped: " + nf.format(logsChopped) + "("
				+ PerHour(logsChopped) + "/h)", 13, 265);
		g.drawString("Profit: " + nf.format(profitGained) + "("
				+ PerHour(profitGained) + "/h)", 13, 285);
		g.drawString("Log Price: " + (logPrice), 13, 305);
		g.drawString("Logs In Bank: " + (logsInBank), 13, 325);
		g.drawString("Status: " + (status), 13, 345);
		drawCross(g);

	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message.contains("You get some oak logs.")) {
			logsChopped++;
		}
	}

	private void drawCross(Graphics g) {
		g.setColor(ctx.mouse.isPressed() ? Color.GREEN : Color.RED);
		g.drawLine(0, (int) (ctx.mouse.getLocation().getY()), 800,
				(int) (ctx.mouse.getLocation().getY()));
		g.drawLine((int) (ctx.mouse.getLocation().getX()), 0,
				(int) (ctx.mouse.getLocation().getX()), 800);

	}
	
	public int getGuidePrice(int itemId) {
		try {
			final URL website = new URL(
					"http://www.tip.it/runescape/json/ge_single_item?item="
							+ itemId);

			final URLConnection conn = website.openConnection();
			conn.addRequestProperty(
					"User-Agent",
					"Mozilla/5.0 (Windows NT 6.2; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.57 Safari/537.36");
			conn.setRequestProperty("Connection", "close");

			final BufferedReader br = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			final String json = br.readLine();

			return Integer.parseInt(json.substring(
					json.indexOf("mark_price") + 13,
					json.indexOf(",\"daily_gp") - 1).replaceAll(",", ""));
		} catch (Exception a) {
			System.out.println("Error looking up price for item: " + itemId);
			return -1;
		}
	}
}
