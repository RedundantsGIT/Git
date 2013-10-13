package rTanner;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.methods.Environment;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Item;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rTanner", description = "Tans all hides in Al-Kharid & Burthorpe for (gp) [Supports all hides/potions]", topic = 876982, version = 3.7, instances = 5)
public class rTanner extends PollingScript implements PaintListener {

	private static long elapsedTime = 0;

	private static Image tanner = getImage("http://i43.tinypic.com/rh288o.jpg");
	private static RenderingHints antialiasing = new RenderingHints(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	private static String location;
	private static String status = "Starting...";

	private static boolean gotPrices = false;
	private static boolean atAlKharid = false;
	private static boolean atBurthorpe = false;
	private static boolean atVarrock = false;

	private static int cowHidePrice, snakeSkinPrice, swampSnakeSkinPrice,
			greenDHidePrice, blueDHidePrice, redDHidePrice, blackDHidePrice,
			royalDHidePrice, leatherPrice, hardLeatherPrice,
			greenDragonLeatherPrice, redDragonLeatherPrice,
			blueDragonLeatherPrice, blackDragonLeatherPrice,
			royalDragonLeatherPrice, snakeLeatherPrice, swampSnakeLeatherPrice,
			Profit, backpackHideCount, hideCount, profitTotal, hidesLeft,
			potionsLeft;
	private static final int IntCowhideID = 1739, IntSnakeSkinhideID = 6287,
			IntSnakeSkinTwohideID = 7801, IntGreenDHideID = 1753,
			IntBlueDHideID = 1751, IntRedDHideID = 1749,
			IntBlackDHideID = 1747, IntRoyalDHideID = 24372,
			IntLeatherID = 1741, IntHardLeatherID = 1743,
			IntGreenDragonLeatherID = 1745, IntBlueDragonLeatherID = 2505,
			IntBlackDragonLeatherID = 2509, IntRoyalDragonLeatherID = 24374,
			IntTannedSnakeSkinID = 6289, IntTannedSnakeSkinIDTwo = 6289,
			IntRedDragonLeatherID = 2507, doorID = 24376;

	private static final int[] leatherID = { 1741, 1743, 1745, 2505, 24374,
			6289, 2507, 2509 },
			tannerID = { 14877, 2824, 2320 },
			hideID = { 1739, 1753, 1751, 24372, 6287, 7801, 1749, 1747 },
			energyPotionID = { 3008, 3010, 3012, 3014, 23375, 23377, 23379,
					23381, 23383, 23385, 11453, 11455, 23387, 23389, 23391,
					23393, 23395, 23397, 11481, 11483, 3016, 3018, 3020, 3022 },
			stairsID = { 76222 };

	private static Tile doorTile = new Tile(3187, 3403, 0);
	private static Tile tannerTile = new Tile(3187, 3406, 0);
	private static int tries;

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
				new CloseInterfaces(ctx), new Door(ctx), new Stairs(ctx),
				new UseEnergyPotion(ctx), new Tan(ctx), new Banking(ctx) });
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
		System.out.println("[rTanner]: -Total Proft Gained: " + profitTotal);
		System.out.println("[rTanner]: -Total Hides Tanned: " + hideCount);
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
			ctx.camera.setPitch(Random.nextInt(32, 34));
			sleep(50, 100);
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

	private class Stairs extends Job {
		public Stairs(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			final GameObject Stairs = ctx.objects.select().id(stairsID).first()
					.isEmpty() ? null : ctx.objects.iterator().next();
			return Stairs != null && Stairs.isOnScreen();
		}

		@Override
		public void execute() {
			for (GameObject Stairs : ctx.objects.select().id(stairsID)
					.nearest()) {
				if (Stairs.interact("Climb-down")) {
					final Timer climbTimer = new Timer(Random.nextInt(3500,
							4000));
					while (climbTimer.isRunning() && Stairs.isOnScreen())
						sleep(Random.nextInt(25, 50));
				}
			}
		}
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
					final Timer potionTimer = new Timer(Random.nextInt(3500,
							4000));
					while (potionTimer.isRunning()
							&& ctx.movement.getEnergyLevel() < 50)
						sleep(Random.nextInt(50, 100));
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
			for (GameObject Door : ctx.objects.select().select().id(doorID)
					.at(doorTile)) {
				status = "Door";
				if (Door.isOnScreen()) {
					Door.click(true);
					final Timer doorTimer = new Timer(
							Random.nextInt(2100, 2300));
					while (doorTimer.isRunning() && tileContainsDoor())
						sleep(Random.nextInt(5, 15));
				} else {
					ctx.movement.stepTowards(ctx.movement
							.getClosestOnMap(doorTile));
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
				System.out
						.println("[rTanner]: -Gold dropped below 600, logging out...");
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
										.distanceTo(
												ctx.movement.getDestination()) < 7) {
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
								.distanceTo(ctx.movement.getDestination()) < 8) {
					ctx.movement.newTilePath(tilePath).reverse().traverse();
					if (tries < 1) {
						ctx.camera.turnTo(ctx.bank.getNearest());
						tries++;
					}
				}
			}
		}
	}

	public void doBanking() {
		if (ctx.bank.isOpen()) {
			tries = 0;
			if (!gotPrices) {
				getBankPrices();
				gotPrices = true;
			} else {
				if (ctx.backpack.select().count() == 28) {
					if (!hasLeather() && !hasPotion()) {
						status = "Deposit Backpack";
						depositInventory();
					} else if (!hasLeather() && hasPotion()) {
						status = "Deposit Backpack";
						depositInventory();
					} else if (hasLeather() && !hasPotion()) {
						status = "Deposit Backpack";
						depositInventory();
					} else if (hasLeather() && hasPotion()) {
						status = "Deposit Leather";
						deposit(0, leatherID);
					}
				} else {
					if (!bankHasHide()) {
						System.out
								.println("[rTanner]: -Ran out of hides to tan, logging out...");
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
				final Timer depositTimer = new Timer(Random.nextInt(2100, 2300));
				while (depositTimer.isRunning()
						&& ctx.backpack.select().count() > 0)
					sleep(Random.nextInt(5, 15));
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

	public boolean bankHasPotion() {
		for (Item Potion : ctx.bank.select().id(energyPotionID)) {
			if (ctx.bank.select().contains(Potion))
				return true;
		}
		return false;
	}

	public boolean deposit(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.deposit(i, count))
				break;
		}
		return true;
	}

	public boolean withdraw(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.withdraw(i, count))
				break;
		}
		return true;
	}

	public boolean atBank() {
		return ctx.bank.isOnScreen()
				&& ctx.players.local().getLocation()
						.distanceTo(ctx.bank.getNearest()) < 10;
	}

	public boolean atTanner() {
		for (Npc Tanner : ctx.npcs.select().id(tannerID).nearest()) {
			if (ctx.players.local().getLocation()
					.distanceTo(Tanner.getLocation()) < 11)
				return true;
		}
		return false;
	}

	public boolean tileContainsDoor() {
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
		final Component button1 = ctx.widgets.get(1477).getComponent(77);
		final Component button2 = ctx.widgets.get(26).getComponent(8);
		if (ctx.bank.isOpen()) {
			if (ctx.backpack.select().count() > 0)
				depositInventory();
			ctx.bank.close();
		}
		if (button1.isVisible() && button1.getChild(1).interact("Logout")) {
			final Timer button1Timer = new Timer(Random.nextInt(1800, 2000));
			while (button1Timer.isRunning() && !button2.isVisible())
				sleep(Random.nextInt(5, 15));
		}
		if (button2.isVisible() && button2.interact("Select")) {
			sleep(Random.nextInt(1000, 2000));
			getController().stop();
		}
	}

	public void TanHides() {
		final Component Make = ctx.widgets.get(1370, 20);
		final Component CloseButton = ctx.widgets.get(1370, 30);
		if (Make.isValid()) {
			calculateMemberProfit();
			hideCount += backpackHideCount;
			if (Make.interact("Make")) {
				final Timer WidgetTimer = new Timer(Random.nextInt(4800, 5200));
				while (WidgetTimer.isRunning() && hasHide())
					sleep(Random.nextInt(25, 75));
			}
			if (CloseButton.isVisible())
				CloseButton.interact("Close");
			calculateFreeProfit();
		} else {
			for (Npc Tanner : ctx.npcs.select().id(tannerID).nearest()) {
				if (Tanner.isOnScreen()) {
					status = "Interact";
					backpackHideCount = ctx.backpack.select().id(hideID)
							.count();
					if (atVarrock) {
						Tanner.interact("Trade", "Tanner");
					} else {
						Tanner.interact("Tan");
					}
					final Timer InteractTimer = new Timer(Random.nextInt(2500,
							3000));
					while (InteractTimer.isRunning() && !Make.isVisible())
						sleep(Random.nextInt(25, 50));
					while (ctx.players.local().isInMotion()
							&& !Make.isVisible())
						sleep(Random.nextInt(1, 5));
					break;
				} else {
					if (atVarrock) {
						Tile Loc = tannerTile.randomize(-1, -1);
						ctx.movement.stepTowards(ctx.movement
								.getClosestOnMap(Loc));
						ctx.camera.turnTo(tannerTile);
					} else {
						Tile Loc = Tanner.getLocation().randomize(-1, -2);
						ctx.movement.stepTowards(ctx.movement
								.getClosestOnMap(Loc));
						sleep(Random.nextInt(150, 300));
						ctx.camera.turnTo(Tanner.getLocation());
					}
				}

			}
		}
	}

	private void calculateMemberProfit() {
		final Item GreenDragonhide = ctx.backpack.select().id(IntGreenDHideID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		final Item BlueDragonhide = ctx.backpack.select().id(IntBlueDHideID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		final Item RedDragonhide = ctx.backpack.select().id(IntRedDHideID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		final Item SnakeSkinhide = ctx.backpack.select().id(IntSnakeSkinhideID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		final Item SnakeSkinTwohide = ctx.backpack.select()
				.id(IntSnakeSkinTwohideID).first().isEmpty() ? null
				: ctx.backpack.iterator().next();
		final Item BlackDragonhide = ctx.backpack.select().id(IntBlackDHideID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		final Item RoyalDragonhide = ctx.backpack.select().id(IntRoyalDHideID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		if (ctx.backpack.select().contains(GreenDragonhide)) {
			int GetCount = ctx.backpack.select().id(IntGreenDHideID).count();
			Profit = greenDragonLeatherPrice * GetCount - greenDHidePrice
					* GetCount;
			profitTotal += Profit;
		}
		if (ctx.backpack.select().contains(BlueDragonhide)) {
			int GetCount = ctx.backpack.select().id(IntBlueDHideID).count();
			Profit = blueDragonLeatherPrice * GetCount - blueDHidePrice
					* GetCount;
			profitTotal += Profit;
		}
		if (ctx.backpack.select().contains(RedDragonhide)) {
			int GetCount = ctx.backpack.select().id(IntRedDHideID).count();
			Profit = redDragonLeatherPrice * GetCount - redDHidePrice
					* GetCount;
			profitTotal += Profit;
		}
		if (ctx.backpack.select().contains(SnakeSkinhide)) {
			int GetCount = ctx.backpack.select().id(IntSnakeSkinhideID).count();
			Profit = -snakeLeatherPrice * GetCount - snakeSkinPrice * GetCount;
			profitTotal += Profit;
		}
		if (ctx.backpack.select().contains(SnakeSkinTwohide)) {
			int GetCount = ctx.backpack.select().id(IntSnakeSkinTwohideID)
					.count();
			Profit = swampSnakeLeatherPrice * GetCount - swampSnakeSkinPrice
					* GetCount;
		}
		if (ctx.backpack.select().contains(BlackDragonhide)) {
			int GetCount = ctx.backpack.select().id(IntBlackDHideID).count();
			Profit = blackDragonLeatherPrice * GetCount - blackDHidePrice
					* GetCount;
		}
		if (ctx.backpack.select().contains(RoyalDragonhide)) {
			int GetCount = ctx.backpack.select().id(IntRoyalDHideID).count();
			Profit = royalDragonLeatherPrice * GetCount - royalDHidePrice
					* GetCount;
		}
		profitTotal += Profit;
	}

	private void calculateFreeProfit() {
		final Item HardLeather = ctx.backpack.select().id(IntHardLeatherID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		final Item Leather = ctx.backpack.select().id(IntLeatherID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		if (ctx.backpack.select().contains(HardLeather)) {
			int GetHideCount = ctx.backpack.select().id(IntHardLeatherID)
					.count();
			Profit = hardLeatherPrice * GetHideCount - cowHidePrice
					* GetHideCount;
		}
		if (ctx.backpack.select().contains(Leather)) {
			int GetHideCount = ctx.backpack.select().id(IntLeatherID).count();
			Profit = leatherPrice * GetHideCount - cowHidePrice * GetHideCount;
		}
		profitTotal += Profit;
	}

	private void getBankPrices() {
		status = "Fetching prices...";
		final Item GreenDragonhide = ctx.bank.select().id(IntBlueDHideID)
				.first().isEmpty() ? null : ctx.bank.iterator().next();
		final Item BlueDragonhide = ctx.bank.select().id(IntBlueDHideID)
				.first().isEmpty() ? null : ctx.bank.iterator().next();
		final Item RedDragonhide = ctx.bank.select().id(IntRedDHideID).first()
				.isEmpty() ? null : ctx.bank.iterator().next();
		final Item SnakeSkinhide = ctx.bank.select().id(IntSnakeSkinhideID)
				.first().isEmpty() ? null : ctx.bank.iterator().next();
		final Item SnakeSkinTwohide = ctx.bank.select()
				.id(IntSnakeSkinTwohideID).first().isEmpty() ? null : ctx.bank
				.iterator().next();
		final Item BlackDragonhide = ctx.bank.select().id(IntBlackDHideID)
				.first().isEmpty() ? null : ctx.bank.iterator().next();
		final Item RoyalDragonhide = ctx.bank.select().id(IntRoyalDHideID)
				.first().isEmpty() ? null : ctx.bank.iterator().next();
		final Item Cowhide = ctx.bank.select().id(IntCowhideID).first()
				.isEmpty() ? null : ctx.bank.iterator().next();
		if (ctx.bank.select().contains(GreenDragonhide)) {
			greenDragonLeatherPrice = getGuidePrice(IntGreenDragonLeatherID) - 20;
			greenDHidePrice = getGuidePrice(IntGreenDHideID);
		}
		if (ctx.bank.select().contains(BlueDragonhide)) {
			blueDragonLeatherPrice = getGuidePrice(IntBlueDragonLeatherID) - 20;
			blueDHidePrice = getGuidePrice(IntBlueDHideID);
		}
		if (ctx.bank.select().contains(RedDragonhide)) {
			redDragonLeatherPrice = getGuidePrice(IntRedDragonLeatherID) - 20;
			redDHidePrice = getGuidePrice(IntRedDHideID);
		}
		if (ctx.bank.select().contains(SnakeSkinhide)) {
			snakeLeatherPrice = getGuidePrice(IntTannedSnakeSkinID) - 15;
			snakeSkinPrice = getGuidePrice(IntSnakeSkinhideID);
		}
		if (ctx.bank.select().contains(SnakeSkinTwohide)) {
			swampSnakeLeatherPrice = getGuidePrice(IntTannedSnakeSkinIDTwo) - 20;
			swampSnakeSkinPrice = getGuidePrice(IntSnakeSkinTwohideID);
		}
		if (ctx.bank.select().contains(BlackDragonhide)) {
			blackDragonLeatherPrice = getGuidePrice(IntBlackDragonLeatherID) - 20;
			blackDHidePrice = getGuidePrice(IntBlackDHideID);
		}
		if (ctx.bank.select().contains(RoyalDragonhide)) {
			royalDragonLeatherPrice = getGuidePrice(IntRoyalDragonLeatherID) - 20;
			royalDHidePrice = getGuidePrice(IntRoyalDHideID);
		}
		if (ctx.bank.select().contains(Cowhide)) {
			hardLeatherPrice = getGuidePrice(IntHardLeatherID) - 3;
			leatherPrice = getGuidePrice(IntLeatherID);
			cowHidePrice = getGuidePrice(IntCowhideID);
		}
	}

	final Color Black = new Color(0, 0, 0, 200);
	final Font Font = new Font("Tahoma", 3, 11);
	final Font FontTwo = new Font("Arial", 3, 12);
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
		g.drawRect(3, 285, 514, 105);
		g.setColor(Black);
		g.fillRect(3, 285, 514, 105);
		g.drawImage(tanner, 10, 140, null);
		g.setFont(FontTwo);
		g.setColor(Color.RED);
		g.drawString("rTanner", 240, 300);
		g.setFont(Font);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 110,
				320);
		g.drawString("Tanned: " + nf.format(hideCount) + "("
				+ perHour(hideCount) + ")", 110, 340);
		g.drawString("Hides Left: " + nf.format(hidesLeft), 110, 360);
		g.drawString("Potions Left: " + nf.format(potionsLeft), 230, 320);
		g.drawString("User: " + Environment.getDisplayName(), 230, 340);
		g.drawString("Location: " + (location), 230, 360);
		g.drawString("Profit: " + nf.format(profitTotal) + "("
				+ perHour(profitTotal) + ")", 350, 320);
		g.drawString("Status: " + (status), 350, 340);
		g.setFont(FONT_THREE);
		g.setColor(Color.GREEN);
		g.drawString("v3.7", 490, 360);
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

	public String perHour(int gained) {
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

	private static Image getImage(String url) {
		try {
			return ImageIO.read(new URL(url));
		} catch (IOException e) {
			return null;
		}
	}

	private static int getGuidePrice(int itemId) {
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
