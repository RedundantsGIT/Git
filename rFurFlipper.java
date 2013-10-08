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
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for profit.", version = 1.0, hidden = true, instances = 35)
public class rFurFlipper extends PollingScript implements PaintListener,
		MessageListener {

	private static JobContainer container;
	private mouseTrail trail = new mouseTrail();
	private static String status = "Starting...";
	private static long scriptTimer = 0;
	private static int furPrice, furBought, furStored;
	private static int baraekID = 547, furID = 948;
	static final Tile[] pathToNpc = { new Tile(3189, 3435, 0),
			new Tile(3197, 3430, 0), new Tile(3206, 3430, 0),
			new Tile(3216, 3433, 0) };

	@Override
	public void start() {
		System.out.println("Script started");
		scriptTimer = System.currentTimeMillis();
		status = "Getting G.E. Fur Price";
		furPrice = getGuidePrice(furID);
		rFurFlipper.container = new JobContainer(new Job[] { new Fix(ctx),
				new BuyFur(ctx), new Banking(ctx) });
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

	private class Fix extends Job {
		public Fix(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			final Component InfoWindow = ctx.widgets.get(1477).getComponent(72);
			final Component CollectionBox = ctx.widgets.get(109).getComponent(
					12);
			return InfoWindow.isVisible() || CollectionBox.isVisible();
		}

		@Override
		public void execute() {
			final Component InfoWindow = ctx.widgets.get(1477).getComponent(72);
			final Component CollectionBox = ctx.widgets.get(109).getComponent(
					12);
			if (InfoWindow.isVisible()
					&& InfoWindow.getChild(1).interact("Close Window")) {
				sleep(200, 400);
			} else if (CollectionBox.isVisible()
					&& CollectionBox.interact("Close")) {
				sleep(50, 200);
			}
		}
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
			if (nearBaraek()) {
				if (ctx.backpack.getMoneyPouch() < 20) {
					System.out
							.println("[rFurFlipper]: -Not enough gold left to continue, stopping script.. .");
					getController().stop();
				} else if (canContinue()) {
					status = "Press Spacebar";
					ctx.keyboard.send(" ");
					final Timer pressTimer = new Timer(Random.nextInt(1600,
							1800));
					while (pressTimer.isRunning() && canContinue()) {
						sleep(10, 35);
					}
				} else if (pressOne.isValid()) {
					status = "Press 1";
					ctx.keyboard.send("1");
					final Timer pressTimer = new Timer(Random.nextInt(1600,
							1800));
					while (pressTimer.isRunning() && pressOne.isVisible()) {
						sleep(10, 35);
					}
				} else {
					for (Npc baraek : ctx.npcs.select().id(baraekID).nearest()) {
						status = "Talk to Baraek";
						if (baraek.isOnScreen()) {
							baraek.interact("Talk-to", "Baraek");
							final Timer talkTimer = new Timer(Random.nextInt(
									2300, 2600));
							while (talkTimer.isRunning()
									&& !pressOne.isVisible()) {
								sleep(15, 50);
							}
							while (ctx.players.local().isInMotion()
									&& !pressOne.isValid()) {
								sleep(25, 100);
							}
							break;
						} else if (!baraek.isOnScreen()
								&& !ctx.players.local().isInMotion()) {
							ctx.movement.stepTowards(ctx.movement
									.getClosestOnMap(baraek.getLocation()));

						}
					}
				}
			} else {
				status = "Walk to Baraek";
				if (ctx.bank.isOpen()) {
					ctx.bank.close();
				} else {
					if (!ctx.players.local().isInMotion()
							|| ctx.players.local().getLocation()
									.distanceTo(ctx.movement.getDestination()) < Random
									.nextInt(7, 9)) {
						ctx.movement.newTilePath(pathToNpc).traverse();
					}
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
					final Timer depositTimer = new Timer(Random.nextInt(1700,
							1900));
					while (depositTimer.isRunning()
							&& ctx.backpack.select().count() == 28) {
						sleep(Random.nextInt(50, 550));
					}
					furStored = ctx.bank.select().id(furID).count(true);
				} else {
					if (!ctx.players.local().isInMotion()) {
						ctx.bank.open();
						status = "Bank Open";
					}
				}
			} else {
				status = "Walk to Banker";
				if (!ctx.players.local().isInMotion()
						|| ctx.players.local().getLocation()
								.distanceTo(ctx.movement.getDestination()) < Random
								.nextInt(7, 9)) {
					ctx.movement.newTilePath(pathToNpc).reverse().traverse();
				}
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

	public boolean nearBaraek() {
		for (Npc baraek : ctx.npcs.select().id(baraekID).nearest()) {
			if (ctx.players.local().getLocation()
					.distanceTo(baraek.getLocation()) < 7) {
				return true;
			}
		}
		return false;
	}

	public boolean nearBank() {
		return ctx.bank.isOnScreen()
				&& ctx.players.local().getLocation()
						.distanceTo(ctx.bank.getNearest()) < 7;
	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message
				.contains("20 coins have been removed from your money pouch.")) {
			furBought++;
		}

	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font font = new Font("Times New Roman", 0, 13);
	final static Font fontTwo = new Font("Arial", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {

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
		g.drawString("rFurFlipper", 70, 222);
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
		drawTrail(g);
		drawMouse(g);
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

	private void drawTrail(final Graphics g) {
		final Point m = ctx.mouse.getLocation();
		trail.add(m);
		trail.draw(g);
	}

	private void drawMouse(final Graphics g) {
		final Point m = ctx.mouse.getLocation();
		g.setColor(ctx.mouse.isPressed() ? Color.GREEN : Color.RED);
		g.drawLine(m.x - 5, m.y + 5, m.x + 5, m.y - 5);
		g.drawLine(m.x - 5, m.y - 5, m.x + 5, m.y + 5);
	}

	private final class mouseTrail {
		private final int SIZE = 50;
		private final double ALPHA_STEP = (255.0 / SIZE);
		private final Point[] points;
		private int index;

		public mouseTrail() {
			points = new Point[SIZE];
			index = 0;
		}

		public void add(final Point p) {
			points[index++] = p;
			index %= SIZE;
		}

		public void draw(final Graphics g) {
			double alpha = 0;
			for (int i = index; i != (index == 0 ? SIZE - 1 : index - 1); i = (i + 1)
					% SIZE) {
				if (points[i] != null && points[(i + 1) % SIZE] != null) {
					Color rainbow = Color.getHSBColor((float) (alpha / 255), 1,
							1);
					g.setColor(new Color(rainbow.getRed(), rainbow.getGreen(),
							rainbow.getBlue(), (int) alpha));
					g.drawLine(points[i].x, points[i].y,
							points[(i + 1) % SIZE].x, points[(i + 1) % SIZE].y);
					alpha += ALPHA_STEP;
				}
			}
		}
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
