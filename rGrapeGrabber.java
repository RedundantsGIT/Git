package rGrapeGrabber;

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

import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.util.Timer;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.GroundItem;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rGrapeGrabber", description = "", version = 0.1, instances = 55, hidden = true)
public class rGrapeGrabber extends PollingScript implements PaintListener {
	private static long scriptTimer = 0;
	public Timer wait;
	public final JobContainer container;
	public String status = "Starting...";
	private static final int stairsID[] = { 24073, 24074, 24075 };
	private static final int stairsID2[] = { 24074, 24075 };
	private static final Tile lootTile = new Tile(3143, 3450, 2);
	private static final int tableID = 17099;
	private static int grapesGained, grapePrice, profit, tries;
	private static final Tile GuildEntranceDoorTile = new Tile(3143, 3443, 0);
	private static final int IntBankerID = 553, ShootID1 = 24068,
			ShootID2 = 24067, doorID = 2712, grapeID = 1987;

	private static final Area inGuild = new Area(new Tile[] {
			new Tile(3137, 3444, 0), new Tile(3144, 3444, 0),
			new Tile(3146, 3448, 0), new Tile(3147, 3454, 0),
			new Tile(3138, 3454, 0) });

	public final Tile[] pathToGuild = { new Tile(3182, 3443, 0),
			new Tile(3183, 3450, 0), new Tile(3176, 3450, 0),
			new Tile(3172, 3450, 0), new Tile(3166, 3451, 0),
			new Tile(3160, 3450, 0), new Tile(3155, 3449, 0),
			new Tile(3152, 3446, 0), new Tile(3148, 3443, 0),
			new Tile(3143, 3440, 0) };

	public rGrapeGrabber() {
		scriptTimer = System.currentTimeMillis();
		grapePrice = getGuidePrice(grapeID);

		this.container = new JobContainer(new Job[] {new Camera(ctx), new Grapes(ctx),
				new Banking(ctx) });
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
		/* Game isn't logged in/map isn't loaded */
		if (!ctx.game.isLoggedIn()
				|| ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) {
			return 1000;
		}

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return job.delay();
		}

		return 250;
	}
	
	private class Camera extends Job {
		public Camera(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.getPitch() > 58;
		}

		@Override
		public void execute() {
		ctx.camera.setPitch(45);
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
			if (atGuildEntranceDoor() && !atLevelOne() && !atLevelTwo()
					&& !atLevelThree()) {
				openDoor();
				log.info("Door");
				sTimer(!atLevelOne(), 0, 0);
				while (ctx.players.local().isInMotion())
					sleep(Random.nextInt(50, 100));
			} else {
				if (atLevelOne() || atLevelTwo()) {
					log.info("go up");
					goUp();
				} else {
					if (atLevelThree()) {
						if (ctx.players.local().getLocation()
								.distanceTo(lootTile) > 2) {
							log.info("Walk to loot tile");
							ctx.movement.stepTowards(ctx.movement.getClosestOnMap(lootTile.getLocation()));
							ctx.camera.turnTo(lootTile);
							sleep(Random.nextInt(150, 450));
							while (ctx.players.local().isInMotion()) {
								sleep(Random.nextInt(100, 200));
							}
						} else
							for (GroundItem Grapes : ctx.groundItems.select()
									.id(grapeID).nearest()) {
								log.info("Take");
								if (Grapes != null) {
									take(Grapes);
								}
							}
					} else {
						if (!ctx.players.local().isInMotion()
								|| ctx.players
										.local()
										.getLocation()
										.distanceTo(
												ctx.movement.getDestination()) < Random
										.nextInt(8, 12)) {
							ctx.movement.newTilePath(pathToGuild).traverse();
						}
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
				log.info("Go Down");
				goDown();
			} else {
				if (atLevelOne()) {
					log.info("open door");
					openDoor();
					sTimer(atLevelOne(), 0, 0);
					while (ctx.players.local().isInMotion())
						sleep(Random.nextInt(150, 250));
				} else {
					if (!bankerIsOnScreen()) {
						if (!ctx.players.local().isInMotion()
								|| ctx.players
										.local()
										.getLocation()
										.distanceTo(
												ctx.movement.getDestination()) < Random
										.nextInt(8, 12)) {
							log.info("Walk to banker");
						ctx.movement.newTilePath(pathToGuild).reverse()
								.traverse();
					} 
					}else {
						if (!ctx.bank.isOpen()) {
							log.info("open bank");
							ctx.bank.open();
						} else {
							log.info("deposit");
							ctx.bank.depositInventory();
							sTimer(ctx.backpack.select().count() == 28, 0, 0);
							ctx.bank.close();
						}
					}
				}
			}
		}
	}

	private boolean take(GroundItem g) {
		final int count = ctx.backpack.select().count();
		if (g != null) {
			if(tries > 1){
			ctx.camera.setPitch(45);
			ctx.camera.turnTo(g.getLocation());
			sleep(Random.nextInt(50, 150));
			}
			ctx.mouse.click(g.getCenterPoint().x ,
					g.getCenterPoint().y - Random.nextInt(14, 13), true);
			tries++;
			final Timer lootingTimer = new Timer(2000);
			while (lootingTimer.isRunning()
					&& ctx.backpack.select().count() != count + 1) {
				sleep(Random.nextInt(100, 200));
			}
			if (ctx.backpack.select().count() == count + 1) {
	            tries = 0;
				grapesGained++;
				return true;
			}
		}

		return false;
	}
	
	private boolean bankerIsOnScreen() {
		for (Npc Banker : ctx.npcs.select().id(IntBankerID).nearest()) {
			if(Banker.isOnScreen()){
				return true;
			}
		}
		return false;
	}

	private void openDoor() {
		for (GameObject Door : ctx.objects.select().id(doorID).nearest()) {
			ctx.camera.turnTo(Door.getLocation());
			if(Door.isOnScreen()){
			Door.interact("Open");
			}
		}
	}

	private void goUp() {
		for (GameObject Stairs : ctx.objects.select().id(stairsID).nearest()) {
			ctx.camera.turnTo(Stairs.getLocation());
			if (Stairs.isOnScreen()) {
				Stairs.interact("Climb-up");
				sleep(Random.nextInt(1000, 1500));
				while (ctx.players.local().isInMotion())
					sleep(Random.nextInt(300, 500));
			} else {
				ctx.camera.turnTo(Stairs.getLocation());
			}
		}
	}

	private void goDown() {
		for (GameObject Stairs : ctx.objects.select().id(stairsID2).nearest()) {
			ctx.camera.turnTo(Stairs.getLocation());
			if (Stairs.isOnScreen()) {
				Stairs.interact("Climb-down");
				sleep(Random.nextInt(1000, 1500));
				while (ctx.players.local().isInMotion())
					sleep(Random.nextInt(300, 500));
			} else {
				ctx.camera.turnTo(Stairs.getLocation());
			}
		}
	}

	private boolean atLevelTwo() {
		for (GameObject Shoot : ctx.objects.select().id(ShootID1).nearest()) {
			if (Shoot != null) {
				return true;
			}
		}
		return false;
	}

	private boolean atLevelThree() {
		for (GameObject Shoot : ctx.objects.select().id(ShootID2).nearest()) {
			if (Shoot != null) {
				return true;
			}
		}
		return false;
	}

	private boolean atLevelOne() {
		return inGuild.contains(ctx.players.local().getLocation());
	}

	private boolean atGuildEntranceDoor() {
		for (GameObject Door : ctx.objects.select().id(doorID).nearest()) {
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

	/* Timer */
	private void sTimer(boolean wait4, int int1, int int2) {
		if (int1 == 0 || int2 == 0) {
			int1 = 1300;
			int2 = 1500;
		}
		wait = new Timer(Random.nextInt(int1, int2));
		while (wait.isRunning() && wait4) {
			sleep(Random.nextInt(5, 15));
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
		profit = grapesGained*grapePrice;
		
		g.setColor(black);
		 g.fillRect(6, 210, 200, 145);
		g.setColor(Color.RED);
		 g.drawRect(6, 210, 200, 145);
		g.setFont(fontTwo);
		g.drawString("rGrapeGrabber", 65, 222);
		
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13,
				245);
		g.drawString("Grapes Picked: " + nf.format(grapesGained) + "("
				+ PerHour(grapesGained) + "/h)", 13, 265);
		g.drawString("Profit: " + nf.format(profit) + "("
				+ PerHour(profit) + "/h)", 13, 285);

		drawCross(g);
	}

	private void drawCross(Graphics g) {
		g.setColor(ctx.mouse.isPressed() ? Color.GREEN : Color.RED);
		g.drawLine(0, (int) (ctx.mouse.getLocation().getY()), 800,
				(int) (ctx.mouse.getLocation().getY()));
		g.drawLine((int) (ctx.mouse.getLocation().getX()), 0,
				(int) (ctx.mouse.getLocation().getX()), 800);
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
