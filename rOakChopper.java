package rOakChopper;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
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
import org.powerbot.script.lang.Filter;
import org.powerbot.script.methods.Game.Crosshair;
import org.powerbot.script.methods.Menu.Entry;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Interactive;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rOakChopper", description = "Chops oak trees at the Grand Exchange", version = 0.2, hidden = true, instances = 30)
public class rOakChopper extends PollingScript implements PaintListener,
		MessageListener {

	private static long elapsedTime = 0;

	private static String status = "Starting...";

	private static int logsChopped, logPrice, profitGained, logsInBank;

	private static final int oakLogID = 1521;

	private static int[] oakID = { 38732, 38731 };

	private static final Area oakArea = new Area(new Tile(3197, 3468, 0),
			new Tile(3186, 3455, 0));
	private static final Tile[] pathToOak = new Tile[] {
			new Tile(3180, 3502, 0), new Tile(3183, 3498, 0),
			new Tile(3186, 3494, 0), new Tile(3192, 3491, 0),
			new Tile(3195, 3487, 0), new Tile(3197, 3483, 0),
			new Tile(3198, 3479, 0), new Tile(3197, 3475, 0),
			new Tile(3196, 3471, 0), new Tile(3196, 3467, 0),
			new Tile(3195, 3464, 0) };

	private static JobContainer container;

	@Override
	public void start() {
		System.out.println("Script started");
		elapsedTime = System.currentTimeMillis();
		logPrice = getGuidePrice(oakLogID);
		rOakChopper.container = new JobContainer(new Job[] {
				new CloseInterfaces(ctx), new Banking(ctx), new Chopping(ctx) });
	}

	@Override
	public void suspend() {
		System.out.println("Script suspended");
	}

	@Override
	public void resume() {
		System.out.println("Script resumed");
	}

	@Override
	public void stop() {
		System.out.println("Script stopped");
	}

	public abstract class Job extends MethodProvider {
		public Job(MethodContext ctx) {
			super(ctx);
		}

		public int delay() {
			return 300;
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

		return 200;
	}

	private class CloseInterfaces extends Job {
		public CloseInterfaces(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return canClose();
		}

		@Override
		public void execute() {
			final Component InfoWindow = ctx.widgets.get(1477).getComponent(72);
			if (InfoWindow.isVisible()) {
				InfoWindow.getChild(1).click(true);
				sleep(Random.nextInt(50, 350));
			} else {
				getClose().click(true);
				sleep(Random.nextInt(50, 350));
			}
		}
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
					final Timer depositBackpackTimer = new Timer(
							Random.nextInt(2000, 2200));
					while (depositBackpackTimer.isRunning()
							&& ctx.backpack.select().count() > 28)
						sleep(Random.nextInt(50, 200));
					logsInBank = ctx.bank.select().id(oakLogID).count(true);
				} else {
					status = "Bank Open";
					ctx.bank.open();
				}
			} else {
				status = "Walk to Bank";
				if (!ctx.players.local().isInMotion()
						|| ctx.players.local().getLocation()
								.distanceTo(ctx.movement.getDestination()) < Random
								.nextInt(5, 6))
					ctx.movement.newTilePath(pathToOak).reverse().traverse();
			}
		}
	}

	public boolean atBanker() {
		return ctx.bank.isOnScreen()
				&& ctx.players.local().getLocation()
						.distanceTo(ctx.bank.getNearest()) < 6;
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
			if (oakArea.contains(ctx.players.local().getLocation())) {
				for (GameObject oak : ctx.objects.select().id(oakID).nearest().first()) {
					if (ctx.players.local().getAnimation() == -1) {
						if (oakArea.contains(oak.getLocation())) {
							if (ctx.players.local().getLocation()
									.distanceTo(oak.getLocation()) < 3) {
								if (oak.isOnScreen()) {
									status = "Chop";
									if (interact(oak, "Chop down", "Oak")) {
										if (Random.nextInt(1, 5) == 3)
											mouseMoveSlightly();
										final Timer chopTimer = new Timer(
												Random.nextInt(3000, 3500));
										while (chopTimer.isRunning()
												&& ctx.players.local()
														.getAnimation() == -1)
											sleep(Random.nextInt(150, 250));
									}
									status = "Chopping oak...";
								}
							} else {
								status = "Walk to tree";
								if (Random.nextInt(1, 10) == 5) {
									if (Random.nextInt(1, 5) == 3)
										ctx.camera.setPitch(Random.nextInt(30,
												65));
									ctx.camera.turnTo(oak.getLocation());
								}
								ctx.movement.stepTowards(ctx.movement
										.getClosestOnMap(oak.getLocation()));
								final Timer walkTimer = new Timer(
										Random.nextInt(5000, 6000));
								while (walkTimer.isRunning()
										&& ctx.players.local().getLocation()
												.distanceTo(oak.getLocation()) > 2)
									;
								while (ctx.players.local().isInMotion())
									sleep(500, 600);
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
					if (!ctx.players.local().isInMotion()
							|| ctx.players.local().getLocation()
									.distanceTo(ctx.movement.getDestination()) < Random
									.nextInt(5, 6))
						ctx.movement.newTilePath(pathToOak).traverse();
				}
			}
		}
	}

	public void mouseMoveSlightly() {
		Point p = new Point(
				(int) (ctx.mouse.getLocation().getX() + (Math.random() * 50 > 25 ? 1
						: -1)
						* (20 + Math.random() * 70)), (int) (ctx.mouse
						.getLocation().getY() + (Math.random() * 50 > 25 ? 1
						: -1) * (20 + Math.random() * 85)));
		if (p.getX() < 1 || p.getY() < 1 || p.getX() > 761 || p.getY() > 499) {
			mouseMoveSlightly();
			return;
		}
		ctx.mouse.move(p);
	}

	private static final int[][] CLOSE = { { 109, 12 }, { 1422, 18 },
			{ 1265, 89 }, { 1477, 72 } };

	public Component getClose() {
		for (int[] i : CLOSE) {
			Component c = ctx.widgets.get(i[0], i[1]);
			if (c != null && c.isVisible())
				return c;
		}
		return null;
	}

	public boolean canClose() {
		return getClose() != null;
	}

	public boolean didInteract() {
		return ctx.game.getCrosshair() == Crosshair.ACTION;
	}

	public boolean interact(Interactive interactive, final String action,
			final String option) {
		if (interactive != null && interactive.isOnScreen()) {
			final Filter<Entry> filter = new Filter<Entry>() {

				@Override
				public boolean accept(Entry arg0) {
					return arg0.action.equalsIgnoreCase(action)
							&& arg0.option.equalsIgnoreCase(option);
				}

			};
			if (ctx.menu.click(filter)) {
				return didInteract();
			} else {
				ctx.mouse.move(interactive);
				return interact(interactive, action, option);
			}
		}
		return false;
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

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message.contains("You get some oak logs.")) {
			logsChopped++;
		}
	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font font = new Font("Times New Roman", 0, 13);
	final static Font fontTwo = new Font("Arial", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {

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

	private void drawCross(Graphics g) {
		g.setColor(Color.RED);
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
