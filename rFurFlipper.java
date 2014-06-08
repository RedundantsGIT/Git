package rFurFlipper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import org.powerbot.script.Condition;
import org.powerbot.script.MessageEvent;
import org.powerbot.script.MessageListener;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.Tile;
import org.powerbot.script.rt6.ClientAccessor;
import org.powerbot.script.rt6.ClientContext;
import org.powerbot.script.rt6.Component;
import org.powerbot.script.rt6.Game.Crosshair;
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.Npc;

@Manifest(name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for money", properties = "topic=1135335")
public class rFurFlipper extends
		PollingScript<org.powerbot.script.rt6.ClientContext> implements
		PaintListener, MessageListener {
	private static JobContainer container;
	private static RenderingHints antialiasing = new RenderingHints(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String status = "Starting...";
	private static long scriptTimer = 0;
	private static int furPrice, furBought, furStored;
	private static int baraekID = 547, furID = 948;
	private final Component pressOne = ctx.widgets.widget(1188).component(3);
	private static final Tile[] pathToNpc = { new Tile(3189, 3435, 0),
			new Tile(3200, 3429, 0), new Tile(3208, 3431, 0),
			new Tile(3215, 3433, 0) };
	private static final Tile[] pathToBank = { new Tile(3215, 3433, 0),
			new Tile(3208, 3431, 0), new Tile(3200, 3429, 0),
			new Tile(3189, 3435, 0) };

	@Override
	public void start() {
		scriptTimer = System.currentTimeMillis();
		status = "Get prices..";
		furPrice = getGuidePrice(furID) - 20;
		container = new JobContainer(new Job[] { new Camera(ctx), new Fix(ctx),
				new CloseBank(ctx), new PressOne(ctx), new Continue(ctx),
				new Talking(ctx), new Banking(ctx) });
	}

	@Override
	public void suspend() {
		log.info("Script suspended");
	}

	@Override
	public void resume() {
		log.info("Script resumed");
	}

	@Override
	public void stop() {
		log.info("[rFurFlipper]: -Total Fur Purchased: " + furBought);
		log.info("[rFurFlipper]: -Total Profit Gained: " + profit());
		log.info("Script stopped");
	}

	public abstract class Job extends ClientAccessor {
		public Job(ClientContext ctx) {
			super(ctx);
		}

		public int delay() {
			return 50;
		}

		public int priority() {
			return 0;
		}

		public abstract boolean activate();

		public abstract void execute();
	}

	public class JobContainer {
		private List<Job> jobList = new ArrayList<Job>();

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
	public void poll() {
		if (!ctx.game.loggedIn())
			return;

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return;
		}
	}

	private class Camera extends Job {
		public Camera(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.pitch() < 45;
		}

		@Override
		public void execute() {
			status = "Set Pitch";
			ctx.camera.pitch(Random.nextInt(50, 55));
		}
	}

	private class Fix extends Job {
		public Fix(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.widgets.widget(1191).component(10).text()
					.contains("Can I have a newspaper, please?")
					|| ctx.widgets.widget(1184).component(9).text()
							.contains("Hiya. I'm giving out free books");
		}

		@Override
		public void execute() {
			status = "Close";
			close();
		}
	}

	private class CloseBank extends Job {
		public CloseBank(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.bank.opened() && ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			status = "Close Bank";
			furStored = ctx.bank.select().id(furID).count(true);
			if (Random.nextInt(1, 15) == 10)
				ctx.bank.close();
			else
				close();
		}
	}

	private class PressOne extends Job {
		public PressOne(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return pressOne.valid();
		}

		@Override
		public void execute() {
			status = "Select Option";
			if (Random.nextInt(1, 120) == 100)
				pressOne.click(true);
			else
				ctx.input.send("1");
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return !pressOne.valid() && ctx.chat.queryContinue();
				}
			}, 250, 20);
		}
	}

	private class Continue extends Job {
		public Continue(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.widgets.widget(1191).component(10).valid()
					|| ctx.widgets.widget(1184).component(10).valid();
		}

		@Override
		public void execute() {
			status = "Select Continue";
			if (ctx.widgets.widget(1191).component(10).text()
					.contains("Can you sell me some furs?")
					|| ctx.widgets.widget(1191).component(10).text()
							.contains("Yeah, OK, here you go.")) {
				if (Random.nextInt(1, 100) == 50)
					ctx.chat.clickContinue();
				else
					ctx.input.send(" ");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return ctx.widgets
								.widget(1184)
								.component(9)
								.text()
								.contains(
										"Yeah, sure. They're 20 gold coins each.")
								|| ctx.widgets.widget(1189).component(2).text()
										.contains("Baraek sells you a fur.");
					}
				}, 250, 20);
			} else {
				if (Random.nextInt(1, 80) == 63)
					ctx.chat.clickContinue();
				else
					ctx.input.send(" ");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return pressOne.valid();
					}
				}, 250, 20);
			}
		}
	}

	private class Talking extends Job {
		public Talking(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			final Npc baraek = ctx.npcs.select().id(baraekID).nearest().poll();
			if (ctx.players.local().tile().distanceTo(baraek.tile()) < 8) {
				if (ctx.backpack.moneyPouchCount() < 20) {
					logOut();
				} else {
					if (baraek.inViewport()) {
						if (Random.nextInt(1, 20) == 10)
							mouseMoveSlightly();
						status = "Talk to Baraek";
						if (baraek.interact("Talk-to", "Baraek")) {
							if (Random.nextInt(1, 15) == 10)
								mouseMoveSlightly();
							if (didInteract()) {
								Condition.wait(new Callable<Boolean>() {
									@Override
									public Boolean call() throws Exception {
										return pressOne.valid();
									}
								}, 250, 20);
							}
						}
					} else {
						status = "Walk to Npc";
						ctx.movement.step(ctx.movement.closestOnMap(baraek
								.tile()));
						ctx.camera.turnTo(baraek.tile());
						while (ctx.players.local().inMotion()
								&& !baraek.inViewport())
							;
					}
				}
			} else {
				status = "Walk to Npc";
				if (!ctx.players.local().inMotion()
						|| ctx.players.local().tile()
								.distanceTo(ctx.movement.destination()) < Random
								.nextInt(6, 8)) {
					ctx.movement
							.step(getNextTile(randomizePath(pathToNpc, 3, 2)));
				}
			}
		}
	}

	private class Banking extends Job {
		public Banking(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() == 28;
		}

		@Override
		public void execute() {
			if (ctx.bank.inViewport()
					&& ctx.players.local().tile()
							.distanceTo(ctx.bank.nearest().tile()) < 6) {
				if (ctx.bank.opened()) {
					status = "Deposit";
					ctx.bank.depositInventory();
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.backpack.select().isEmpty();
						}
					}, 250, 20);
				} else {
					status = "Bank Open";
					ctx.camera.turnTo(ctx.bank.nearest());
					ctx.bank.open();
				}
			} else {
				status = "Walk to Bank";
				if (!ctx.players.local().inMotion()
						|| ctx.players.local().tile()
								.distanceTo(ctx.movement.destination()) < Random
								.nextInt(6, 8)) {
					ctx.movement.step(getNextTile(randomizePath(pathToBank, 3,
							2)));
					if (Random.nextInt(1, 10) == 5)
						ctx.camera.turnTo(ctx.bank.nearest());
				}
			}
		}
	}

	private boolean logOut() {
		status = "Logout";
		if (ctx.bank.opened() && !ctx.backpack.select().isEmpty()) {
			ctx.bank.depositInventory();
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return ctx.backpack.isEmpty();
				}
			}, 250, 20);
			ctx.bank.close();
		}
		if (ctx.game.logout(true)) {
			ctx.controller.stop();
			return true;
		}
		return false;
	}

	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}

	private void close() {
		ctx.input.send("{VK_ESCAPE down}");
		final Timer DelayTimer = new Timer(Random.nextInt(50, 1000));
		while (DelayTimer.isRunning())
			;
		ctx.input.send("{VK_ESCAPE up}");
	}

	public void mouseMoveSlightly() {
		Point p = new Point(
				(int) (ctx.input.getLocation().getX() + (Math.random() * 50 > 25 ? 1
						: -1)
						* (20 + Math.random() * 70)), (int) (ctx.input
						.getLocation().getY() + (Math.random() * 50 > 25 ? 1
						: -1) * (20 + Math.random() * 85)));
		if (p.getX() < 1 || p.getY() < 1 || p.getX() > 761 || p.getY() > 499) {
			mouseMoveSlightly();
			return;
		}
		ctx.input.move(p);
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

	public Tile[] randomizePath(Tile[] path, int maxXDeviation,
			int maxYDeviation) {
		Tile[] rez = new Tile[path.length];

		for (int i = 0; i < path.length; i++) {
			int x = path[i].x();
			int y = path[i].y();
			if (maxXDeviation > 0) {
				double d = Math.random() * 2 - 1.0;
				d *= maxXDeviation;
				x += (int) d;
			}
			if (maxYDeviation > 0) {
				double d = Math.random() * 2 - 1.0;
				d *= maxYDeviation;
				y += (int) d;
			}
			rez[i] = new Tile(x, y, path[i].floor());
		}

		return rez;
	}

	public Tile getNextTile(Tile[] path) {
		int dist = 99;
		int closest = -1;
		for (int i = path.length - 1; i >= 0; i--) {
			Tile tile = path[i];
			int d = distanceTo(tile);
			if (d < dist) {
				dist = d;
				closest = i;
			}
		}

		int feasibleTileIndex = -1;

		for (int i = closest; i < path.length; i++) {

			if (distanceTo(path[i]) <= 16) {
				feasibleTileIndex = i;
			} else {
				break;
			}
		}

		if (feasibleTileIndex == -1) {
			return null;
		} else {
			return path[feasibleTileIndex];
		}
	}

	public int distanceTo(Tile tile) {
		return (int) ctx.players.local().tile().distanceTo(tile);
	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();
		if (message
				.contains("20 coins have been removed from your money pouch."))
			furBought++;
	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font fontTwo = new Font("Comic Sans MS", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {

		final Graphics2D g = (Graphics2D) g1;

		long millis = System.currentTimeMillis() - scriptTimer;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setRenderingHints(antialiasing);
		g.setColor(black);
		g.fillRect(5, 5, 190, 145);
		g.setColor(Color.RED);
		g.drawRect(5, 5, 190, 145);
		g.setFont(fontTwo);
		g.drawString("rFurFlipper", 70, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10,
				40);
		g.drawString("Fur Bought: " + nf.format(furBought) + "("
				+ PerHour(furBought) + "/h)", 10, 60);
		g.drawString("Fur In Bank: " + nf.format(furStored), 10, 80);
		g.drawString("Fur Price: " + furPrice, 10, 100);
		g.drawString("Profit: " + nf.format(profit()) + "(" + PerHour(profit())
				+ "/h)", 13, 120);
		g.drawString("Status: " + (status), 10, 140);
		g.setColor(Color.RED);
		g.drawString("v1.4", 165, 140);
		drawMouse(g);
		drawTrail(g);
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

	private mouseTrail trail = new mouseTrail();

	private void drawTrail(final Graphics g) {
		final Point m = ctx.input.getLocation();
		trail.add(m);
		trail.draw(g);
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

	private static int profit() {
		return furBought * furPrice;
	}

	public void drawMouse(Graphics2D g) {
		Point p = ctx.input.getLocation();
		g.setColor(Color.RED);
		g.setStroke(new BasicStroke(2));
		g.fill(new Rectangle(p.x + 1, p.y - 4, 2, 15));
		g.fill(new Rectangle(p.x - 6, p.y + 2, 16, 2));
	}

	private static int getGuidePrice(final int id) {
		return GeItem.price(id);
	}

}
