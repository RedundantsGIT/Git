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

import org.powerbot.event.MessageEvent;
import org.powerbot.event.MessageListener;
import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.methods.Environment;
import org.powerbot.script.methods.Hud;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.methods.Game.Crosshair;
import org.powerbot.script.methods.Hud.Window;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Item;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rTanner", description = "Tans all hides in Al-Kharid & Burthorpe for (gp) [Supports all hides/potions]", topic = 876982)
public class rTanner extends PollingScript implements PaintListener, MessageListener {

	private static long elapsedTime = 0;

	private static RenderingHints antialiasing = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	private static String location;
	private static String status = "Starting...";

	private static boolean atAlKharid = false;
	private static boolean atBurthorpe = false;
	private static boolean atVarrock = false;

	private static int hideCount, hidesLeft, potionsLeft;

	private static final int doorID = 24376;
	final Component Make = ctx.widgets.get(1370, 20);

	private static final int[] tannerID = { 14877, 2824, 2320 };
	private static final int[] hideID = { 1739, 1753, 1751, 24372, 6287, 7801, 1749, 1747 };
	private static final int[] leatherID = { 1741, 1743, 1745, 2505, 24374, 6289, 2507, 2509 };
	private static final int[] energyPotionID = { 3008, 3010, 3012, 3014, 23375, 23377, 23379, 23381, 
		                                          23383, 23385, 11453, 11455, 23387, 23389, 23391, 23393, 
		                                          23395, 23397, 11481, 11483, 3016, 3018, 3020, 3022 };
	
	private static final Tile doorTile = new Tile(3187, 3403, 0);
	private static Tile[] tilePath;
	private static final Tile[] pathToJack = { new Tile(2893, 3529), new Tile(2891, 3514, 0), new Tile(2889, 3510, 0), new Tile(2887, 3502) };
	private static final Tile[] pathToEllis = { new Tile(3271, 3168), new Tile(3276, 3180, 0), new Tile(3280, 3187, 0), new Tile(3275, 3195, 0) };
	private static final Tile[] pathToTanner = { new Tile(3183, 3434, 0), new Tile(3182, 3426, 0), new Tile(3183, 3416, 0), new Tile(3187, 3403, 0) };
	
	private static final Area areaBurthorpe = new Area(new Tile[] { new Tile(2877, 3540, 0), new Tile(2900, 3540, 0), new Tile(2899, 3479, 0), new Tile(2875, 3479, 0) });
	private static final Area areaAlKharid = new Area(new Tile[] { new Tile(3239, 3154, 0), new Tile(3315, 3151, 0), new Tile(3319, 3224, 0), new Tile(3250, 3223, 0) });
	private static final Area areaVarrock = new Area(new Tile[] { new Tile(3166, 3445, 0), new Tile(3171, 3390, 0), new Tile(3214, 3397, 0), new Tile(3206, 3453, 0) });

	private static JobContainer container;

	@Override
	public void start() {
		elapsedTime = System.currentTimeMillis();
		ctx.properties.setProperty("bank.antipattern", "disable");
		rTanner.container = new JobContainer(new Job[] { new GetPlayerArea(ctx), new Pitch(ctx), new CloseInterfaces(ctx), new Door(ctx), 
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
	public int poll() {
		if (!ctx.game.isLoggedIn() || ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) {
			return 1000;
		}

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return job.delay();
		}

		return 50;
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
			return ctx.camera.getPitch() < 33 && !ctx.bank.isOpen();
		}

		@Override
		public void execute() {
			status = "Set Pitch";
			ctx.camera.setPitch(Random.nextInt(35, 40));
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
			final Component Achievements = ctx.widgets.get(1477).getComponent(74);
			final Component CollectionBox = ctx.widgets.get(109).getComponent(61);
			status = "Close";
			if (Achievements.isVisible()) {
				Achievements.getChild(1).click(true);
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !Achievements.isVisible();
					}
				}, 250, 20);
			} else if (CollectionBox.isVisible()) {
				CollectionBox.getChild(1).interact("Close");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !CollectionBox.isVisible();
					}
				}, 250, 20);
			} else {
				getClose().click(true);
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !getClose().isVisible();
					}
				}, 250, 20);
			}
		}
	}
	
	private static final int[][] CLOSE = { { 1477, 74 }, { 109, 61 }, {1401, 35} };

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
			return ctx.players.local().isInMotion() && ctx.movement.getEnergyLevel() < 50 && hasPotion() && !ctx.bank.isOpen() && !Make.isVisible();
		}

		@Override
		public void execute() {
			final Item EnergyPotion = ctx.backpack.select().id(energyPotionID).poll();
			if (!ctx.hud.isVisible(Window.BACKPACK)) {
				status = "Open Backpack";
				ctx.hud.view(Hud.Window.BACKPACK);
			} else {
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
			return tileContainsDoor() && atTanner() && !Make.isVisible();
		}

		@Override
		public void execute() {
			final GameObject Door = ctx.objects.select().select().id(doorID).at(doorTile).poll();
				status = "Door";
				if (Door.isInViewport()) {
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
						status = "Walking to Tanner";
						if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(8, 10)) {
							ctx.movement.newTilePath(tilePath).traverse();
							cameraTurnToTanner();
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
				status = "Walking to Bank";
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(8, 10)) {
					ctx.movement.newTilePath(tilePath).reverse().traverse();
					ctx.camera.turnTo(ctx.bank.getNearest());
				}
			}
		}
	}

	private void doBanking() {
		if (ctx.bank.isOpen()) {
			if (ctx.backpack.select().count() == 28) {
				if (hasLeather() && hasPotion()) {
					deposit(0, leatherID);
				} else {
					status = "Depositing Backpack";
					depositInventory();
				}
			} else {
				if (bankHasHide()) {
					if (hasPotion() && !hasHide() && ctx.backpack.count() > 1 || hasLeather() && !hasPotion() 
					|| hasLeather() && !hasPotion() || ctx.backpack.select().count() > 0 && !hasHide() && !hasPotion()) {
						depositInventory();
					} else if (hasLeather() && hasPotion()) {
						status = "Deposit Leather";
						deposit(0, leatherID);
					} else if (bankHasPotion() && !hasPotion() && !hasHide()) {
						status = "Withdraw Potion";
						withdraw(1, energyPotionID);
					} else {
						status = "Withdraw Hides";
						withdraw(0, hideID);
						hidesLeft = ctx.bank.select().id(hideID).count(true);
						potionsLeft = ctx.bank.select().id(energyPotionID).count(true);
					}
				} else {
					logOut();
				}
			}
		} else {
			status = "Opening Bank";
			ctx.camera.turnTo(ctx.bank.getNearest());
			ctx.bank.open();
		}
	}

	private void TanHides() {
		final Component CloseButton = ctx.widgets.get(1370, 30);
		final Npc Tanner = ctx.npcs.select().id(tannerID).nearest().poll();
		if (Make.isValid()) {
			if (Make.interact("Make")) {
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !Make.isVisible();
					}
				}, 100, 20);
			}
			if (CloseButton.isVisible()) {
				CloseButton.interact("Close");
			}
		} else {
			if (Tanner.isInViewport()) {
				status = "Talk to Tanner";
				if (atAlKharid) {
					Tanner.interact("Tan hides", "Ellis");
				} else if (atBurthorpe) {
					Tanner.interact("Tan hide", "Jack Oval");
				} else {
					Tanner.interact("Trade", "Tanner");
				}
				if (didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return Make.isVisible() || hasLeather();
						}
					}, 250, 20);
					while (ctx.players.local().isInMotion() && !Make.isVisible());
				}
			} else {
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(2, 3))
					ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Tanner.getLocation()));
				ctx.camera.turnTo(Tanner.getLocation());
			}
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
	
	private void logOut() {
		status = "Logout";
		if (ctx.bank.isOpen() && ctx.backpack.select().count() > 0) {
			depositInventory();
			ctx.bank.close();
		}
		if (ctx.game.logout(true)) {
			getController().stop();
		}
	}
	
	private boolean didInteract() {
		return ctx.game.getCrosshair() == Crosshair.ACTION;
	}

	private boolean hasPotion() {
		return !ctx.backpack.select().id(energyPotionID).isEmpty();
	}

	private boolean hasLeather() {
		return !ctx.backpack.select().id(leatherID).isEmpty();
	}

	private boolean hasHide() {
			return !ctx.backpack.select().id(hideID).isEmpty();
	}

	private boolean bankHasHide() {
		return !ctx.bank.select().id(hideID).isEmpty();
	}

	private boolean bankHasPotion() {
		return !ctx.bank.select().id(energyPotionID).isEmpty();
	}
	
	private boolean atBank() {
		return ctx.bank.isInViewport() && ctx.players.local().getLocation().distanceTo(ctx.bank.getNearest()) < 9;
	}
	
	private boolean tileContainsDoor() {
		final GameObject Door = ctx.objects.select().select().id(doorID).at(doorTile).poll();
			return Door.isValid() && ctx.players.local().getLocation().distanceTo(Door.getLocation()) < 13;
	}
	
	private boolean atTanner() {
		final Npc Tanner = ctx.npcs.select().id(tannerID).nearest().poll();
			if (ctx.players.local().getLocation().distanceTo(Tanner.getLocation()) < 9) 
				return true;
		return false;
	}
	
	private boolean cameraTurnToTanner() {
		final Npc Tanner = ctx.npcs.select().id(tannerID).nearest().poll();
		ctx.camera.turnTo(Tanner.getLocation());
		if (Tanner.isInViewport())
			return true;
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

	final Color black = new Color(0, 0, 0, 200);
	final Font font = new Font("Comic Sans MS", 0, 13);
	final Font fontTwo = new Font("Comic Sans MS", 1, 13);
	final Font fontThree = new Font("Comic Sans MS", 3, 9);
	final Font fontFour = new Font("Comic Sans MS", 0, 11);
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
		g.drawRect(3, 285, 400, 90);
		g.setColor(black);
		g.fillRect(3, 285, 400, 90);
		g.setFont(fontTwo);
		g.setColor(Color.RED);
		g.drawString("rTanner", 190, 300);
		g.setFont(font);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 25, 320);
		g.drawString("Tanned: " + nf.format(hideCount) + "(" + perHour(hideCount) + ")", 25, 345);
		g.drawString("Hides Left: " + nf.format(hidesLeft), 150, 345);
		g.drawString("Potions Left: " + nf.format(potionsLeft), 150, 320);
		g.drawString("User: " + Environment.getDisplayName(), 275, 320);
		g.drawString("Location: " + (location), 275, 345);
		g.setFont(fontFour);
		g.setColor(Color.GREEN);
		g.drawString("*" + (status) + "*", 150, 370);
		g.setFont(fontThree);
		g.setColor(Color.RED);
		g.drawString("v4.7", 382, 370);
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
		final Npc Tanner = ctx.npcs.select().id(tannerID).nearest().poll();
			if (Tanner.isInViewport() && hasHide())
				Tanner.getLocation().getMatrix(ctx).draw(g);
	}

	private String perHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System.currentTimeMillis() - elapsedTime)));
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

	@Override
	public void messaged(MessageEvent msg) {
		String m = msg.getMessage().toLowerCase();
		if (m.contains("tanner")) {
			int count = Integer.parseInt(m.replaceAll("\\D", ""));
			hideCount += count;
		}
	}
}
