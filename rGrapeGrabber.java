package rGrapeGrabber;

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

import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.lang.Filter;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.methods.Menu.Entry;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.GroundItem;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rGrapeGrabber", description = "Loots grape from the cooking guild for money.", hidden = true)
public class rGrapeGrabber extends PollingScript implements PaintListener {

	private static String status = "Starting...";

	private static long TIMER_SCRIPT = 0;
	private static Timer TIMER_WAIT;

	private static final int ID_BANKER[] = { 553, 2759 };
	private static final int ID_STAIRS1[] = { 24073, 24074, 24075 };
	private static final int ID_STAIRS2[] = { 24074, 24075 };
	private static final Tile TILE_LOOT = new Tile(3143, 3450, 2);
	private static int GRAPES_GAINED, GRAPE_PRICE, PROFIT_GAINED, TRIES;
	private static final int ID_SHOOT1 = 24068, ID_SHOOT2 = 24067,
			ID_DOOR = 2712, ID_GRAPE = 1987;

	private static final Area AREA_IN_GUILD = new Area(new Tile[] {
			new Tile(3147, 3446, 0), new Tile(3145, 3444, 0),
			new Tile(3141, 3444, 0), new Tile(3138, 3448, 0),
			new Tile(3140, 3453, 0), new Tile(3148, 3451) });

	private static final Tile[] PATH_GUILD = { new Tile(3182, 3443, 0),
			new Tile(3183, 3450, 0), new Tile(3176, 3450, 0),
			new Tile(3172, 3450, 0), new Tile(3166, 3451, 0),
			new Tile(3160, 3450, 0), new Tile(3155, 3449, 0),
			new Tile(3152, 3446, 0), new Tile(3148, 3443, 0),
			new Tile(3143, 3441, 0) };

	private static JobContainer container;

	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
		GRAPE_PRICE = getGuidePrice(ID_GRAPE);
		log.info("G.E. Grape Price : " + GRAPE_PRICE);
		rGrapeGrabber.container = new JobContainer(new Job[] { new Pitch(ctx),
				new CloseInterfaces(ctx), new Grapes(ctx), new Banking(ctx) });
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
			return 100;
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

	private class Pitch extends Job {
		public Pitch(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.getPitch() < 35;
		}

		@Override
		public void execute() {
			ctx.camera.setPitch(Random.nextInt(37, 40));
		}
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
				sleep(Random.nextInt(15, 25));
			} else {
				getClose().click(true);
				sleep(Random.nextInt(10, 20));
			}
		}
	}

	private static final int[][] CLOSE = { { 109, 12 }, { 1422, 18 },
			{ 1265, 89 }, { 1401, 37 }, { 1477, 72 } };

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

	private class Grapes extends Job {
		public Grapes(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			if (atGuildEntranceDoor() && !atLevelOne() && !atLevelTwo()
					&& !atLevelThree()) {
				openDoor();
				status = "Open door";
				sTimer(!atLevelOne(), 0, 0);
				while (ctx.players.local().isInMotion())
					sleep(Random.nextInt(50, 100));
			} else if (atLevelOne() || atLevelTwo()) {
				status = "Go up";
				goUp();
			} else if (atLevelThree()) {
				if (ctx.players.local().getLocation().distanceTo(TILE_LOOT) > 2) {
					status = "Walk to grapes";
					ctx.movement.stepTowards(ctx.movement
							.getClosestOnMap(TILE_LOOT.getLocation()));
					ctx.camera.turnTo(TILE_LOOT);
					sleep(Random.nextInt(150, 450));
					while (ctx.players.local().isInMotion()) {
						sleep(Random.nextInt(100, 200));
					}
				} else
					for (GroundItem Grapes : ctx.groundItems.select()
							.id(ID_GRAPE).nearest()) {
						if (Grapes != null) {
							status = "Looting..";
							take(Grapes);
						}
					}
			} else {
				if (!ctx.players.local().isInMotion()
						|| ctx.players.local().getLocation()
								.distanceTo(ctx.movement.getDestination()) < Random
								.nextInt(8, 12)) {
					if (ctx.bank.isOpen()) {
						status = "Bank close";
						ctx.bank.close();
					} else {
						status = "Walk to guild";
						ctx.movement.newTilePath(PATH_GUILD).traverse();
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
			if (atLevelThree() || atLevelTwo()) {
				status = "Go down";
				goDown();
			} else if (atLevelOne()) {
				status = "Open door";
				openDoor();
			} else if (!bankerIsOnScreen()) {
				if (!ctx.players.local().isInMotion()
						|| ctx.players.local().getLocation()
								.distanceTo(ctx.movement.getDestination()) < Random
								.nextInt(8, 12)) {
					status = "Walk to bank";
					ctx.movement.newTilePath(PATH_GUILD).reverse().traverse();
				}
			} else {
				if (!ctx.bank.isOpen()) {
					status = "Bank open";
					ctx.bank.open();
				} else {
					status = "Deposit backpack";
					ctx.bank.depositInventory();
					sTimer(ctx.backpack.select().count() == 28, 0, 0);
				}

			}
		}
	}

	private boolean take(GroundItem g) {
		final int count = ctx.backpack.select().count();
		final Point p = g.getLocation().getMatrix(ctx).getPoint(0.5, 0.5, -417);
		final Filter<Entry> filter = new Filter<Entry>() {
			@Override
			public boolean accept(Entry arg0) {
				return arg0.action.equalsIgnoreCase("Take")
						&& arg0.option.equalsIgnoreCase("Grapes");
			}

		};
		if (ctx.menu.click(filter)) {
			if (Random.nextInt(1, 5) == 3)
				mouseMoveSlightly();
			final Timer lootingTimer = new Timer(Random.nextInt(1800, 2000));
			while (lootingTimer.isRunning()
					&& ctx.backpack.count() != count + 1) {
				sleep(Random.nextInt(100, 200));
			}
		} else {
			if (TRIES > 4) {
				ctx.camera.turnTo(g.getLocation());
				sleep(Random.nextInt(400, 650));
			}
			ctx.mouse.move(p);
			TRIES++;
		}
		if (ctx.backpack.select().count() == count + 1) {
			TRIES = 0;
			GRAPES_GAINED++;
			return true;
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

	public boolean bankerIsOnScreen() {
		for (Npc Banker : ctx.npcs.select().id(ID_BANKER).nearest()) {
			if (Banker.isOnScreen())
				return true;
		}
		return false;
	}

	private void openDoor() {
		for (GameObject Door : ctx.objects.select().id(ID_DOOR).nearest()) {
			ctx.camera.turnTo(Door.getLocation());
			if (ctx.players.local().getLocation()
					.distanceTo(Door.getLocation()) < 4) {
				if (Door.isOnScreen()) {
					Door.interact("Open");
					sTimer(atLevelOne(), 0, 0);
					while (ctx.players.local().isInMotion())
						sleep(Random.nextInt(150, 250));
				}
			} else {
				ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Door
						.getLocation()));
			}
		}
	}

	private void goUp() {
		for (GameObject Stairs : ctx.objects.select().id(ID_STAIRS1).nearest()) {
			ctx.camera.turnTo(Stairs.getLocation());
			if (ctx.players.local().getLocation()
					.distanceTo(Stairs.getLocation()) < 4) {
				if (Stairs.isOnScreen()) {
					Stairs.interact("Climb-up");
					sleep(Random.nextInt(1000, 1500));
					while (ctx.players.local().isInMotion())
						sleep(Random.nextInt(300, 500));
				}
			} else {
				ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Stairs
						.getLocation()));
			}
		}
	}

	private void goDown() {
		for (GameObject Stairs : ctx.objects.select().id(ID_STAIRS2).nearest()) {
			ctx.camera.turnTo(Stairs.getLocation());
			if (ctx.players.local().getLocation()
					.distanceTo(Stairs.getLocation()) < 4) {
				if (Stairs.isOnScreen()) {
					Stairs.interact("Climb-down");
					sleep(Random.nextInt(1200, 1600));
					while (ctx.players.local().isInMotion())
						sleep(Random.nextInt(300, 500));
				}
			} else {
				ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Stairs
						.getLocation()));
			}
		}
	}

	private boolean atLevelTwo() {
		for (GameObject Shoot : ctx.objects.select().id(ID_SHOOT1).nearest()) {
			if (Shoot != null) {
				return true;
			}
		}
		return false;
	}

	private boolean atLevelThree() {
		for (GameObject Shoot : ctx.objects.select().id(ID_SHOOT2).nearest()) {
			if (Shoot != null) {
				return true;
			}
		}
		return false;
	}

	private boolean atLevelOne() {
		return AREA_IN_GUILD.contains(ctx.players.local().getLocation());
	}

	private boolean atGuildEntranceDoor() {
		for (GameObject Door : ctx.objects.select().id(ID_DOOR).nearest()) {
			if (!Door.isOnScreen()
					&& ctx.players.local().getLocation()
							.distanceTo(Door.getLocation()) < 6) {
				ctx.camera.turnTo(Door.getLocation());
			}
			if (Door.isOnScreen()
					&& ctx.players.local().getLocation()
							.distanceTo(Door.getLocation()) < 7) {
				return true;
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

	private void sTimer(boolean wait4, int int1, int int2) {
		if (int1 == 0 || int2 == 0) {
			int1 = 1300;
			int2 = 1500;
		}
		TIMER_WAIT = new Timer(Random.nextInt(int1, int2));
		while (TIMER_WAIT.isRunning() && wait4) {
			sleep(Random.nextInt(5, 15));
		}
	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font font = new Font("Times New Roman", 0, 13);
	final static Font fontTwo = new Font("Comic Sans MS", 3, 11);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {
		long millis = System.currentTimeMillis() - TIMER_SCRIPT;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;
		PROFIT_GAINED = GRAPES_GAINED * GRAPE_PRICE;

		g.setColor(black);
		g.fillRect(6, 210, 160, 105);
		g.setColor(Color.MAGENTA);
		//g.drawRect(6, 210, 50, 105);
		g.setFont(fontTwo);
		g.drawString("rGrapeGrabber", 45, 222);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13,
				245);
		g.drawString("Grapes Picked: " + nf.format(GRAPES_GAINED) + "("
				+ PerHour(GRAPES_GAINED) + "/h)", 13, 265);
		g.drawString("Profit: " + nf.format(PROFIT_GAINED) + "("
				+ PerHour(PROFIT_GAINED) + "/h)", 13, 285);
		g.drawString("Status: " + (status), 13, 305);
		drawMouse(g);
	}

	private void drawMouse(final Graphics g) {
		final Point m = ctx.mouse.getLocation();
		g.setColor(ctx.mouse.isPressed() ? Color.GREEN : Color.MAGENTA);
		g.drawLine(m.x - 5, m.y + 5, m.x + 5, m.y - 5);
		g.drawLine(m.x - 5, m.y - 5, m.x + 5, m.y + 5);
	}

	public String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - TIMER_SCRIPT)));
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
