package rFurFlipper;

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
import org.powerbot.script.methods.Game;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.util.Timer;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for profit.", version = 0.5, hidden = true, instances = 35)
public class rFurFlipper extends PollingScript implements PaintListener,
		MessageListener {

	private static Timer timeRan = new Timer(0);
	private static JobContainer container;
	private static String status = "Starting...";
	private static long scriptTimer = 0;
	private static int furPrice, furBought, furStored;
	private static int baraekID = 547, furID = 948;
	private static int bankerID [] = { 553, 2759 };
	private static boolean path1 = false;
	private static boolean path2 = false;
	private static boolean path3 = false;
	private static boolean path4 = false;

	@Override
	public void start() {
		path1 = true;
		System.out.println("Script started");
		scriptTimer = System.currentTimeMillis();
		status = "Getting G.E. Fur Price";
		furPrice = getGuidePrice(furID);
		rFurFlipper.container = new JobContainer(new Job[] { new BuyFur(ctx),
				new Banking(ctx) });
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
		System.out.println("[rFurFlipper]: -Total Time: "
				+ timeRan.toElapsedString());
		System.out.println("[rFurFlipper]: -Total Fur Purchased: " + furBought);
		System.out.println("[rFurFlipper]: -Total Profit Gained: " + profit());
		System.out.println("Script stopped");
	}

	public abstract class Job extends MethodProvider {
		public Job(MethodContext ctx) {
			super(ctx);
		}

		public int delay() {
			return 250;
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

		return 50;
	}

	private class BuyFur extends Job {
		public BuyFur(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			final Component pressOne = ctx.widgets.get(1188, 2);
			final Npc baraek = ctx.npcs.select().id(baraekID).first().isEmpty() ? null
					: ctx.npcs.iterator().next();
			if (nearBaraek()) {
				if (baraek != null) {
					if (ctx.backpack.getMoneyPouch() < 20) {
						System.out
								.println("[rFurFlipper]: -Not enough gold left to continue, stopping script.. .");
						getController().stop();
					} else if (!baraek.isOnScreen()
							&& !ctx.players.local().isInMotion()) {
						ctx.movement.stepTowards(ctx.movement
								.getClosestOnMap(baraek.getLocation()));
					} else if (canContinue()) {
						status = "Press Spacebar";
						ctx.keyboard.send(" ");
						final Timer pressTimer = new Timer(Random.nextInt(1500, 1800));
						while (pressTimer.isRunning() && canContinue()) {
							sleep(50, 200);
						}
					} else if (pressOne.isValid()) {
						status = "Press 1";
						ctx.keyboard.send("1");
						final Timer pressTimer = new Timer(Random.nextInt(1500, 1800));
						while (pressTimer.isRunning() && pressOne.isVisible()) {
							sleep(50, 200);
						}
					} else {
						status = "Talk to Baraek";
						if (Random.nextInt(0, 10) == 5) {
							mouseMoveSlightly();
						}
						if (baraek.isOnScreen()) {
							baraek.interact("Talk-to", "Baraek");
						}
						if (Random.nextInt(0, 8) == 4) {
							mouseMoveSlightly();
						}
						final Timer talkTimer = new Timer(Random.nextInt(1800, 2000));
						while (talkTimer.isRunning() && !pressOne.isVisible()) {
							sleep(5, 250);
						}
						while (ctx.players.local().isInMotion()) {
							sleep(25, 300);
						}
					}
				}
			} else {
				status = "Walk to Baraek";
				if (ctx.bank.isOpen()) {
					ctx.bank.close();
				} else {
					WalkingPath();
				}
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
			if (nearBank()) {
					if (ctx.bank.isOpen()) {
						status = "Deposit Inventory";
						ctx.bank.depositInventory();
						final Timer depositTimer = new Timer(1800);
						while (depositTimer.isRunning()
								&& ctx.backpack.select().count() == 28) {
							sleep(Random.nextInt(100, 650));
						}
						furStored = ctx.bank.select().id(furID).count(true);
						status = "Bank Close";
						ctx.bank.close();
						SwitchPath();
					} else {
						if (!ctx.players.local().isInMotion()) {
							ctx.bank.open();
							status = "Bank Open";
						}
					}
			} else {
				status = "Walk to Banker";
				WalkingPath();
			}
		}
	}

	private void SwitchPath() {
		if (path1) {
			path1 = false;
			path2 = true;
		} else if (path2) {
			path2 = false;
			path3 = true;
		} else if (path3) {
			path3 = false;
			path4 = true;
		} else if (path4) {
			path4 = false;
			path1 = true;

		}
	}

	private void WalkingPath() {
		final Tile[] pathToNpc1 = { new Tile(3189, 3434, 0),
				new Tile(3198, 3429, 0), new Tile(3207, 3429, 0),
				new Tile(3217, 3434, 0) };
		final Tile[] pathToNpc2 = { new Tile(3189, 3435, 0),
				new Tile(3197, 3430, 0), new Tile(3206, 3429, 0),
				new Tile(3216, 3433, 0) };
		final Tile[] pathToNpc3 = { new Tile(3189, 3438, 0),
				new Tile(3202, 3440, 0), new Tile(3209, 3436, 0),
				new Tile(3216, 3432, 0) };
		final Tile[] pathToNpc4 = { new Tile(3189, 3439, 0),
				new Tile(3198, 3435, 0), new Tile(3208, 3432, 0),
				new Tile(3215, 3434, 0) };
		if (path1) {
			log.info("Path1");
			if (ctx.backpack.select().count() == 28) {
				ctx.movement.newTilePath(pathToNpc1).reverse().traverse();
			} else {
				ctx.movement.newTilePath(pathToNpc1).traverse();
			}
		} else if (path2) {
			log.info("Path2");
			if (ctx.backpack.select().count() == 28) {
				ctx.movement.newTilePath(pathToNpc2).reverse().traverse();
			} else {
				ctx.movement.newTilePath(pathToNpc2).traverse();
			}
		} else if (path3) {
			log.info("Path3");
			if (ctx.backpack.select().count() == 28) {
				ctx.movement.newTilePath(pathToNpc3).reverse().traverse();
			} else {
				ctx.movement.newTilePath(pathToNpc3).traverse();
			}
		} else if (path4) {
			log.info("Path4");
			if (ctx.backpack.select().count() == 28) {
				ctx.movement.newTilePath(pathToNpc4).reverse().traverse();
			} else {
				ctx.movement.newTilePath(pathToNpc4).traverse();
			}

		}
	}

	private static final int[][] CONTINUES = { { 1189, 11 }, { 1184, 13 },
			{ 1186, 6 }, { 1191, 12 } };

	public Component getContinue() {
		for (int[] i : CONTINUES) {
			Component c = ctx.widgets.get(i[0], i[1]);
			if (c != null && c.isValid())
				return c;
		}
		return null;
	}

	public boolean canContinue() {
		return getContinue() != null;
	}

	public boolean nearBaraek() {
		for (Npc baraek : ctx.npcs.select().id(baraekID).nearest()) {
			if (baraek != null
					&& ctx.players.local().getLocation()
							.distanceTo(baraek.getLocation()) < 7) {
				return true;
			}
		}
		return false;
	}

	public boolean nearBank() {
		for (Npc banker : ctx.npcs.select().id(bankerID).nearest()) {
			if (banker != null
					&& ctx.players.local().getLocation()
							.distanceTo(banker.getLocation()) < 6) {
				return true;
			}
		}
		return false;
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

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message
				.contains("20 coins have been removed from your money pouch."))
			furBought++;

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

		long millis = System.currentTimeMillis() - scriptTimer;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setColor(black);
		g.fillRect(6, 210, 200, 145);
		g.setColor(Color.RED);
		g.drawRect(6, 210, 200, 145);
		g.setFont(fontTwo);
		g.drawString("rFurFlipper", 75, 222);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13,
				245);
		g.drawString("Fur Bought: " + nf.format(furBought) + "("
				+ PerHour(furBought) + "/h)", 13, 265);
		g.drawString("Fur In Bank: " + nf.format(furStored), 13, 285);
		g.drawString("Fur Price: " + furPrice, 13, 305);
		g.drawString("Profit: " + nf.format(profit()) + "(" + PerHour(profit())
				+ "/h)", 13, 325);
		g.drawString("Status: " + (status), 13, 345);
		drawCross(g);
	}

	public String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - scriptTimer)));
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

	private int profit() {
		int cost = 0;
		if (furBought != 0)
			cost = -20;
		return furBought * furPrice - cost;
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
