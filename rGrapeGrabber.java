package rGrapeGrabber;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.lang.Filter;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.methods.Game.Crosshair;
import org.powerbot.script.methods.Menu.Entry;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.GeItem;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.GroundItem;
import org.powerbot.script.wrappers.Interactive;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rGrapeGrabber", description = "Loots grape from the cooking guild for money.", hidden = true)
public class rGrapeGrabber extends PollingScript implements PaintListener {

	private static String status = "Starting...";

	private static long TIMER_SCRIPT = 0;

	private static final int ID_BANKER[] = { 553, 2759 };
	private static final int ID_STAIRS1[] = { 24073, 24074, 24075 };
	private static final int ID_STAIRS2[] = { 24074, 24075 };
	private static final Tile TILE_LOOT = new Tile(3144, 3450, 2);
	private static int GRAPES_GAINED, GRAPE_PRICE, PROFIT_GAINED, TRIES;
	private static final int ID_SHOOT1 = 24068, ID_SHOOT2 = 24067, ID_DOOR = 2712, ID_GRAPE = 1987;

	private static final Area AREA_IN_GUILD = new Area(new Tile[] {
			new Tile(3147, 3446, 0), new Tile(3145, 3444, 0),
			new Tile(3141, 3444, 0), new Tile(3138, 3448, 0),
			new Tile(3140, 3453, 0), new Tile(3148, 3451) });

	private static final Tile[] PATH_GUILD = { new Tile(3182, 3443, 0),
			new Tile(3183, 3450, 0), new Tile(3176, 3450, 0),
			new Tile(3172, 3450, 0), new Tile(3166, 3451, 0),
			new Tile(3160, 3450, 0), new Tile(3155, 3449, 0),
			new Tile(3152, 3446, 0), new Tile(3148, 3443, 0),
			new Tile(3143, 3443, 0) };
	
	private final Tile ClickTile = new Tile(3143, 3450, 2);

	private static JobContainer container;

	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
		GRAPE_PRICE = getGuidePrice(ID_GRAPE);
		ctx.properties.setProperty("bank.antipattern", "disable");
		log.info("G.E. Grape Price : " + GRAPE_PRICE);
		rGrapeGrabber.container = new JobContainer(new Job[] { new Pitch(ctx), new Grapes(ctx), new Banking(ctx) });
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
		log.info("Script stopped");
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
		if (!ctx.game.isLoggedIn() || ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) {
			return 1000;
		}

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return job.delay();
		}

		return Random.nextInt(100, 200);
	}

	private class Pitch extends Job {
		public Pitch(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.getPitch() < 40;
		}

		@Override
		public void execute() {
			ctx.camera.setPitch(Random.nextInt(40, 45));
		}
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
			if (atGuildEntranceDoor() && !atLevelOne() && !atLevelTwo() && !atLevelThree()) {
				status = "Open door";
				openDoor();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return atLevelOne();
					}
				}, 250, 20);
				while (ctx.players.local().isInMotion()){
					sleep(Random.nextInt(50, 100));
				}
			} else if (atLevelOne() || atLevelTwo()) {
				status = "Go up";
				goUp();
				sleep(1500, 2000);
			} else if (atLevelThree()) {
				if (ctx.players.local().getLocation().distanceTo(TILE_LOOT) > 2) {
					status = "Walk to grapes";
					if(ClickTile.getLocation().getMatrix(ctx).isInViewport()){
					ClickTile.getLocation().getMatrix(ctx).interact("Walk here");
					sleep(550, 1000);
					while(ctx.players.local().isInMotion());
					}else
						ctx.movement.stepTowards(ctx.movement.getClosestOnMap(TILE_LOOT.getLocation()));
					ctx.camera.turnTo(TILE_LOOT);
					while (ctx.players.local().isInMotion()) 
						sleep(Random.nextInt(100, 200));
				} else
					for (GroundItem Grapes : ctx.groundItems.select().id(ID_GRAPE).nearest()) {
							status = "Looting..";
							take(Grapes);
					}
			} else {
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(8, 12)) {
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
				sleep(1500, 2000);
			} else if (atLevelOne()) {
				status = "Open door";
				openDoor();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !atLevelOne();
					}
				}, 250, 20);
			} else if (bankerIsOnScreen()) {
				if (ctx.bank.isOpen()) {
					status = "Deposit backpack";
					ctx.bank.depositInventory();
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.backpack.select().count() != 28;
						}
					}, 250, 20);

				} else {
					ctx.bank.open();
				}
			} else {
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(8, 12)) {
					status = "Walk to bank";
					ctx.movement.newTilePath(PATH_GUILD).reverse().traverse();
				}

			}
		}
	}

	private boolean take(GroundItem g) {
		final int count = ctx.backpack.select().id(ID_GRAPE).count();
		final Point p = g.getLocation().getMatrix(ctx).getPoint(0.5, 0.5, -417);
		final Filter<Entry> filter = new Filter<Entry>() {
			@Override
			public boolean accept(Entry arg0) {
				return arg0.action.equalsIgnoreCase("Take") && arg0.option.equalsIgnoreCase("Grapes");
			}

		};
		if (ctx.menu.click(filter)) {
			if(Random.nextInt(1, 10) == 5)
			ctx.mouse.move(Random.nextInt(1, 15), Random.nextInt(1, 10));
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return ctx.backpack.select().id(ID_GRAPE).count() == count + 1;
				}
			}, 250, 20);
		} else {
			if (TRIES > 4) {
				ctx.camera.turnTo(g.getLocation());
			}
			ctx.mouse.move(p);
			TRIES++;
		}
		if (ctx.backpack.select().id(ID_GRAPE).count() == count + 1) {
			TRIES = 0;
			GRAPES_GAINED++;
			return true;
		}

		return false;
	}

	private void openDoor() {
		final int[] doorBounds = {-200, 150, -800, -300, 0, 0};
		final GameObject Door = ctx.objects.select().id(ID_DOOR).each(Interactive.doSetBounds(doorBounds)).nearest().poll();
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS1).nearest().poll();
			if (ctx.players.local().getLocation().distanceTo(Door.getLocation()) < 5) {
				if (Door.isInViewport()) {
					ctx.camera.turnTo(Stairs.getLocation());
					Door.interact("Open", "Door");
					while (ctx.players.local().isInMotion());
			}
		} else {
			ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Door.getLocation()));
		}

	}

	private void goUp() {
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS1).nearest().poll();
			if (Stairs.isInViewport()) {
				ctx.camera.turnTo(Stairs.getLocation());
				if (Stairs.interact("Climb-up")) {
					while (ctx.players.local().isInMotion());
				}
			} else {
				ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Stairs.getLocation()));
			}
		}
	
	public boolean didInteract() {
		return ctx.game.getCrosshair() == Crosshair.ACTION;
	}

	private void goDown() {
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS2).nearest().poll();
				if (Stairs.isInViewport()) {
					ctx.camera.turnTo(Stairs.getLocation());
					if(Stairs.interact("Climb-down")){
					while (ctx.players.local().isInMotion());
					}
			} else {
				ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Stairs.getLocation()));
			}
		}
	
	public boolean bankerIsOnScreen() {
		    final Npc Banker = ctx.npcs.select().id(ID_BANKER).nearest().poll();
			return Banker.isInViewport();
	}
	
	private boolean atLevelTwo() {
		final GameObject Shoot = ctx.objects.select().id(ID_SHOOT1).nearest().poll();
		return Shoot.isValid();
	}

	private boolean atLevelThree() {
		final GameObject Shoot = ctx.objects.select().id(ID_SHOOT2).nearest().poll();
		return Shoot.isValid();
	}

	private boolean atLevelOne() {
		return AREA_IN_GUILD.contains(ctx.players.local().getLocation());
	}

	private boolean atGuildEntranceDoor() {
		final GameObject Door = ctx.objects.select().id(ID_DOOR).nearest().poll();
			return Door.isInViewport() && ctx.players.local().getLocation().distanceTo(Door.getLocation()) < 7;
	}

	final static Color BLACK = new Color(25, 0, 0, 200);
	final static Font FONT = new Font("Comic Sans MS", 3, 11);
	final static NumberFormat NF = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {
		long millis = System.currentTimeMillis() - TIMER_SCRIPT;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;
		PROFIT_GAINED = GRAPES_GAINED * GRAPE_PRICE;

		g.setColor(BLACK);
		g.fillRect(6, 210, 160, 105);
		g.setColor(Color.MAGENTA);
		g.setFont(FONT);
		g.drawString("rGrapeGrabber", 45, 222);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13, 245);
		g.drawString("Grapes Picked: " + NF.format(GRAPES_GAINED) + "(" + PerHour(GRAPES_GAINED) + "/h)", 13, 265);
		g.drawString("Profit: " + NF.format(PROFIT_GAINED) + "(" + PerHour(PROFIT_GAINED) + "/h)", 13, 285);
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
		return formatNumber((int) ((gained) * 3600000D / (System.currentTimeMillis() - TIMER_SCRIPT)));
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

	private static int getGuidePrice(final int id) {
		return GeItem.getPrice(id);
	}
}
