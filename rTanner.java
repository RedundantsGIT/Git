package rTanner;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
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
import org.powerbot.script.methods.Environment;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Item;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rTanner", description = "Tans all hides in Al-Kharid & Burthorpe for (gp) [Supports all hides/potions]", topic = 876982, instances = 5)
public class rTanner extends PollingScript implements PaintListener {

	private static long elapsedTime = 0;

	private static RenderingHints antialiasing = new RenderingHints(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	private static String location;
	private static String status = "Starting...";

	private static boolean atAlKharid = false;
	private static boolean atBurthorpe = false;
	private static boolean atVarrock = false;

	private static int backpackHideCount, hideCount, hidesLeft, potionsLeft, tries;

	private static final int doorID = 24376;

	private static final int[] leatherID = { 1741, 1743, 1745, 2505, 24374, 6289, 2507, 2509 },
			tannerID = { 14877, 2824, 2320 },
			hideID = { 1739, 1753, 1751, 24372, 6287, 7801, 1749, 1747 },
			energyPotionID = { 3008, 3010, 3012, 3014, 23375, 23377, 23379,
					23381, 23383, 23385, 11453, 11455, 23387, 23389, 23391,
					23393, 23395, 23397, 11481, 11483, 3016, 3018, 3020, 3022 };

	private static Tile doorTile = new Tile(3187, 3403, 0);
	private static Tile tannerTile = new Tile(3187, 3406, 0);

	private static Tile[] tilePath;
	private static final Tile[] pathToJack = { new Tile(2893, 3529),
			new Tile(2891, 3514, 0), new Tile(2889, 3510, 0),
			new Tile(2887, 3502) };
	private static final Tile[] pathToEllis = { new Tile(3270, 3168),
			new Tile(3274, 3178, 0), new Tile(3280, 3187, 0),
			new Tile(3275, 3195, 0) };
	private static final Tile[] pathToTanner = { new Tile(3183, 3435, 0),
			new Tile(3181, 3426, 0), new Tile(3180, 3416, 0),
			new Tile(3187, 3403, 0) };
	private static final Area areaBurthorpe = new Area(new Tile[] {
			new Tile(2877, 3540, 0), new Tile(2900, 3540, 0),
			new Tile(2899, 3479, 0), new Tile(2875, 3479, 0) });
	private static final Area areaAlKharid = new Area(new Tile[] {
			new Tile(3239, 3154, 0), new Tile(3315, 3151, 0),
			new Tile(3319, 3224, 0), new Tile(3250, 3223, 0) });
	private static final Area areaVarrock = new Area(new Tile[] {
			new Tile(3166, 3445, 0), new Tile(3171, 3390, 0),
			new Tile(3214, 3397, 0), new Tile(3206, 3453, 0) });

	private static JobContainer container;

	@Override
	public void start() {
		elapsedTime = System.currentTimeMillis();
		rTanner.container = new JobContainer(new Job[] {
				new GetPlayerArea(ctx), new Pitch(ctx),
				new CloseInterfaces(ctx), new Door(ctx),
				new UseEnergyPotion(ctx), new Tan(ctx), new Banking(ctx) });
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
		log.info("[rTanner]: -Total Hides Tanned: " + hideCount);
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

		return 100;
	}

	private class GetPlayerArea extends Job {
		public GetPlayerArea(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return !atAlKharid && !atBurthorpe && !atVarrock;
		}

		@Override
		public void execute() {
			if (areaBurthorpe.contains(ctx.players.local().getLocation())) {
				location = "Burthorpe";
				tilePath = pathToJack;
				atBurthorpe = true;
			} else if (areaAlKharid.contains(ctx.players.local().getLocation())) {
				location = "Al Kharid";
				tilePath = pathToEllis;
				atAlKharid = true;
			} else if (areaVarrock.contains(ctx.players.local().getLocation())) {
				location = "Varrock";
				tilePath = pathToTanner;
				atVarrock = true;
			}
		}
	}

	private class Pitch extends Job {
		public Pitch(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.getPitch() < 30;
		}

		@Override
		public void execute() {
			status = "Set Pitch";
			ctx.camera.setPitch(Random.nextInt(35, 38));
			sleep(100, 200);
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
			final Component Achievements = ctx.widgets.get(1477).getComponent(73);
			if (Achievements.isVisible()) {
				Achievements.getChild(1).click(true);
				sleep(Random.nextInt(15, 25));
			} else {
				getClose().click(true);
				sleep(Random.nextInt(10, 20));
			}
		}
	}

	private static final int[][] CLOSE = { { 109, 12 }, { 1433, 19 },
			{ 1265, 87 }, { 1401, 35 }, { 1477, 73 } };

	private Component getClose() {
		for (int[] i : CLOSE) {
			Component c = ctx.widgets.get(i[0], i[1]);
			if (c != null && c.isVisible())
				return c;
		}
		return null;
	}

	private boolean canClose() {
		return getClose() != null;
	}

	private class UseEnergyPotion extends Job {
		public UseEnergyPotion(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			final Component Make = ctx.widgets.get(1370, 20);
			return ctx.players.local().isInMotion()
					&& ctx.movement.getEnergyLevel() < 50 && hasPotion()
					&& !ctx.bank.isOpen() && !Make.isVisible();
		}

		@Override
		public void execute() {
			for (Item EnergyPotion : ctx.backpack.select().id(energyPotionID)) {
				status = "Use Potion";
				if (EnergyPotion.interact("Drink")) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.movement.getEnergyLevel() > 50;
						}
					}, 250, 20);
				}
			}

		}
	}

	private class Door extends Job {
		public Door(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			final Component Make = ctx.widgets.get(1370, 20);
			return tileContainsDoor() && atTanner() && !Make.isVisible();
		}

		@Override
		public void execute() {
			for (GameObject Door : ctx.objects.select().select().id(doorID).at(doorTile)) {
				status = "Door";
				if (Door.isOnScreen()) {
					Door.click(true);
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !tileContainsDoor();
						}
					}, 250, 20);
				} else {
					ctx.movement.stepTowards(ctx.movement.getClosestOnMap(doorTile));
				}
			}
		}

	}

	private class Tan extends Job {
		public Tan(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return hasHide();
		}

		@Override
		public void execute() {
			if (ctx.backpack.getMoneyPouch() < 600) {
				log.info("[rTanner]: -Gold dropped below 600, logging out...");
				logOut();
			} else {
				if (atTanner()) {
					TanHides();
				} else {
					if (ctx.bank.isOpen()) {
						status = "Close Bank";
						ctx.bank.close();
					} else {
						status = "Walk to Tanner";
						if (!ctx.players.local().isInMotion()
								|| ctx.players
										.local()
										.getLocation()
										.distanceTo(ctx.movement.getDestination()) < Random.nextInt(7, 9)) {
							ctx.movement.newTilePath(tilePath).traverse();

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
			return !hasHide();
		}

		@Override
		public void execute() {
			if (atBank()) {
				doBanking();
			} else {
				status = "Walk to Bank";
				if (!ctx.players.local().isInMotion()
						|| ctx.players.local().getLocation()
								.distanceTo(ctx.movement.getDestination()) < Random
								.nextInt(7, 9)) {
					ctx.movement.newTilePath(tilePath).reverse().traverse();
					if (tries < 1) {
						ctx.camera.turnTo(ctx.bank.getNearest());
						tries++;
					}
				}
			}
		}
	}

	private void doBanking() {
		if (ctx.bank.isOpen()) {
			tries = 0;
			if (ctx.backpack.select().count() == 28) {
				if (hasLeather() && hasPotion()) {
					deposit(0, leatherID);
				} else {
					status = "Deposit Backpack";
					depositInventory();
				}
			} else {
				if (!bankHasHide()) {
					log.info("[rTanner]: -Ran out of hides to tan, logging out...");
					logOut();
				} else if (hasPotion() && !hasHide()
						&& ctx.backpack.count() > 1) {
					status = "Reset Banking...";
					depositInventory();
				} else if (hasLeather() && hasPotion()) {
					status = "Deposit Leather";
					deposit(0, leatherID);
				} else if (hasLeather() && !hasPotion()) {
					status = "Deposit Backpack";
					depositInventory();
				} else if (ctx.backpack.select().count() > 0 && !hasHide()
						&& !hasPotion()) {
					status = "Deposit Backpack";
					depositInventory();
				} else if (!hasPotion() && !hasHide() && bankHasPotion()) {
					status = "Get Potion";
					withdraw(1, energyPotionID);
				} else if (!hasHide() && bankHasHide()) {
					status = "Get Hides";
					withdraw(0, hideID);
					hidesLeft = ctx.bank.select().id(hideID).count(true);
					potionsLeft = ctx.bank.select().id(energyPotionID)
							.count(true);
				}
			}
		} else {
			status = "Bank Open";
			if (Random.nextInt(1, 5) == 3) {
				ctx.camera.turnTo(ctx.bank.getNearest());
			}
			ctx.bank.open();
		}
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

	private void depositInventory() {
		final Component DepositBackpackButton = ctx.widgets.get(762, 11);
		if (DepositBackpackButton.isVisible()) {
			if (DepositBackpackButton.interact("Deposit carried items")) {
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return ctx.backpack.select().isEmpty();
					}
				}, 250, 20);
			}
		}
	}

	private boolean hasPotion() {
		for (Item Potion : ctx.backpack.select().id(energyPotionID)) {
			if (ctx.backpack.select().contains(Potion))
				return true;
		}
		return false;
	}

	private boolean hasLeather() {
		for (Item Leather : ctx.backpack.select().id(leatherID)) {
			if (ctx.backpack.select().contains(Leather))
				return true;
		}
		return false;
	}

	private boolean hasHide() {
		for (Item Hide : ctx.backpack.select().id(hideID)) {
			if (ctx.backpack.select().contains(Hide))
				return true;
		}
		return false;
	}

	private boolean bankHasHide() {
		for (Item Hide : ctx.bank.select().id(hideID)) {
			if (ctx.bank.select().contains(Hide))
				return true;
		}
		return false;
	}

	private boolean bankHasPotion() {
		for (Item Potion : ctx.bank.select().id(energyPotionID)) {
			if (ctx.bank.select().contains(Potion))
				return true;
		}
		return false;
	}

	private boolean deposit(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.deposit(i, count))
				break;
		}
		return true;
	}

	private boolean withdraw(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.withdraw(i, count))
				break;
		}
		return true;
	}

	private boolean atBank() {
		return ctx.bank.isOnScreen()
				&& ctx.players.local().getLocation().distanceTo(ctx.bank.getNearest()) < 10;
	}

	private boolean atTanner() {
		for (Npc Tanner : ctx.npcs.select().id(tannerID).nearest()) {
			if (ctx.players.local().getLocation()
					.distanceTo(Tanner.getLocation()) < 11)
				return true;
		}
		return false;
	}

	private boolean tileContainsDoor() {
		for (GameObject Door : ctx.objects.select().select().id(doorID)
				.at(doorTile)) {
			if (Door.isValid()
					&& ctx.players.local().getLocation()
							.distanceTo(Door.getLocation()) < 13)
				return true;
		}
		return false;
	}

	private void logOut() {
		status = "Logout";
		if (ctx.bank.isOpen() && ctx.backpack.select().count() > 0) {
			depositInventory();
			ctx.bank.close();
		}
		if (ctx.game.logout(false)) {
			getController().stop();
		}
	}

	private void TanHides() {
		final Component Make = ctx.widgets.get(1370, 20);
		final Component CloseButton = ctx.widgets.get(1370, 30);
		if (Make.isValid()) {
			hideCount += backpackHideCount;
			if (Make.interact("Make")) {
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !Make.isVisible();
					}
				}, 250, 20);
			}
			if (CloseButton.isVisible()) {
				CloseButton.interact("Close");
			}
		} else {
			for (Npc Tanner : ctx.npcs.select().id(tannerID).nearest()) {
				if (Tanner.isOnScreen()) {
					status = "Interact";
					backpackHideCount = ctx.backpack.select().id(hideID).count();
					if (atVarrock) {
						Tanner.interact("Trade", "Tanner");
					} else {
						Tanner.interact("Tan");
					}
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return Make.isVisible();
						}
					}, 250, 20);
					break;
				} else {
					if (atVarrock) {
						Tile Loc = tannerTile.randomize(-1, -1);
						ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Loc));
						ctx.camera.turnTo(tannerTile);
					} else {
						Tile Loc = Tanner.getLocation().randomize(-1, -2);
						ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Loc));
						sleep(Random.nextInt(150, 300));
						ctx.camera.turnTo(Tanner.getLocation());
					}
				}

			}
		}
	}

	final Color Black = new Color(0, 0, 0, 200);
	final Font Font = new Font("Tahoma", 0, 13);
	final Font FontTwo = new Font("Arial", 0, 13);
	final Font FONT_THREE = new Font("Arial", 3, 9);
	final NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {
		long millis = System.currentTimeMillis() - elapsedTime;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		final Graphics2D g = (Graphics2D) g1;

		g.setRenderingHints(antialiasing);
		g.setColor(Color.RED);
		g.drawRect(3, 285, 425, 105);
		g.setColor(Black);
		g.fillRect(3, 285, 425, 105);
		g.setFont(FontTwo);
		g.setColor(Color.RED);
		g.drawString("rTanner", 200, 300);
		g.setFont(Font);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 25,
				320);
		g.drawString("Tanned: " + nf.format(hideCount) + "("
				+ perHour(hideCount) + ")", 25, 345);
		g.drawString("Hides Left: " + nf.format(hidesLeft), 25, 370);
		g.drawString("Potions Left: " + nf.format(potionsLeft), 150, 320);
		g.drawString("User: " + Environment.getDisplayName(), 150, 345);
		g.drawString("Location: " + (location), 150, 370);
		g.drawString("Status: " + (status), 275, 320);
		g.setFont(FONT_THREE);
		g.setColor(Color.GREEN);
		g.drawString("v4.0", 400, 370);
		drawMouse(g);
		drawTannerTile(g);
	}

	private void drawMouse(final Graphics g) {
		final Point m = ctx.mouse.getLocation();
		g.setColor(ctx.mouse.isPressed() ? Color.GREEN : Color.RED);
		g.drawLine(m.x - 5, m.y + 5, m.x + 5, m.y - 5);
		g.drawLine(m.x - 5, m.y - 5, m.x + 5, m.y + 5);
	}

	private void drawTannerTile(final Graphics g) {
		for (Npc Tanner : ctx.npcs.select().id(tannerID).nearest()) {
			if (Tanner.isOnScreen()) {
				Tanner.getLocation().getMatrix(ctx).draw(g);
			}
		}
	}

	private String perHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - elapsedTime)));
	}

	private String formatNumber(int start) {
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
}
