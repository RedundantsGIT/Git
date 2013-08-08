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
import org.powerbot.script.methods.Game;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.util.Timer;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Item;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rTanner", description = "Tans all hides in Al-Kharid & Burthorpe for (gp) [Supports all hides/potions]", website = "http://www.powerbot.org/community/topic/876982-vip-rtanner-all-potions-all-hides-al-kharid-burthorpe/", version = 2.5)
public class rTanner extends PollingScript implements PaintListener {
	private final RenderingHints antialiasing = new RenderingHints(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	public long elapsedTime = 0;
	public String status = "Starting...";
	public String location;
	public Timer wait;
	public Image tanner = getImage("http://i43.tinypic.com/rh288o.jpg");
	public mouseTrail trail = new mouseTrail();
	public boolean gotPrices = false;
	public boolean atBurthorpe = false;
	public boolean atAlKharid = false;
	public final int[] leatherID = { 1741, 1743, 1745, 2505, 24374, 6289, 2507,
			2509 },
			bankBoothID = { 76274, 42192 },
			tannerID = { 14877, 2824 },
			hideID = { 1739, 1753, 1751, 24372, 6287, 7801, 1749, 1747 },
			energyPotionID = { 3008, 3010, 3012, 3014, 23375, 23377, 23379,
					23381, 23383, 23385, 11453, 11455, 23387, 23389, 23391,
					23393, 23395, 23397, 11481, 11483, 3016, 3018, 3020, 3022 };
	private final int IntCowhideID = 1739, IntSnakeSkinhideID = 6287,
			IntSnakeSkinTwohideID = 7801, IntGreenDHideID = 1753,
			IntBlueDHideID = 1751, IntRedDHideID = 1749,
			IntBlackDHideID = 1747, IntRoyalDHideID = 24372,
			IntLeatherID = 1741, IntHardLeatherID = 1743,
			IntGreenDragonLeatherID = 1745, IntBlueDragonLeatherID = 2505,
			IntBlackDragonLeatherID = 2509, IntRoyalDragonLeatherID = 24374,
			IntTannedSnakeSkinID = 6289, IntTannedSnakeSkinIDTwo = 6289,
			IntRedDragonLeatherID = 2507;
	private int backpackHideCount, hideCount, profitTotal, hidesLeft,
			potionsLeft;
	private int cowHidePrice, snakeSkinPrice, swampSnakeSkinPrice,
			greenDHidePrice, blueDHidePrice, redDHidePrice, blackDHidePrice,
			royalDHidePrice, leatherPrice, hardLeatherPrice,
			greenDragonLeatherPrice, redDragonLeatherPrice,
			blueDragonLeatherPrice, blackDragonLeatherPrice,
			royalDragonLeatherPrice, snakeLeatherPrice, swampSnakeLeatherPrice,
			Profit;
	public final Tile[] pathToJack = { new Tile(2897, 3520, 0),
			new Tile(2891, 3514, 0), new Tile(2891, 3507, 0),
			new Tile(2887, 3502, 0) };
	public final Tile[] pathToBurthorpeBank = { new Tile(2891, 3507, 0),
			new Tile(2891, 3514, 0), new Tile(2897, 3520, 0),
			new Tile(2893, 3529, 0) };
	public final Tile[] pathToEllis = { new Tile(3271, 3167, 0),
			new Tile(3275, 3173, 0), new Tile(3276, 3178, 0),
			new Tile(3279, 3185, 0), new Tile(3273, 3195, 0) };
	public final Tile[] pathToAlKharidBank = { new Tile(3279, 3185, 0),
			new Tile(3276, 3178, 0), new Tile(3275, 3173, 0),
			new Tile(3271, 3168, 0) };
	private final Area areaBurthorpe = new Area(new Tile[] {
			new Tile(2877, 3540, 0), new Tile(2900, 3540, 0),
			new Tile(2899, 3479, 0), new Tile(2875, 3479, 0) });
	private final Area areaAlKharid = new Area(new Tile[] {
			new Tile(3263, 3203, 0), new Tile(3287, 3203, 0),
			new Tile(3287, 3157, 0), new Tile(3262, 3158, 0) });

	public final JobContainer container;

	public rTanner() {
		elapsedTime = System.currentTimeMillis();

		this.container = new JobContainer(new Job[] { new GetPlayerArea(ctx),
				new UseEnergyPotion(ctx), new Tan(ctx), new Banking(ctx) });
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

		return 250;
	}

	private class GetPlayerArea extends Job {
		public GetPlayerArea(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return !atAlKharid && !atBurthorpe;
		}

		@Override
		public void execute() {
			if (inBurthorpe()) {
				location = "Burthorpe";
				atBurthorpe = true;
			} else {
				if (inAlKharid())
					location = "Al Kharid";
				atAlKharid = true;
			}
		}
	}

	private class UseEnergyPotion extends Job {
		public UseEnergyPotion(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ShouldUse();
		}

		@Override
		public void execute() {
			final Item EnergyPotion = ctx.backpack.select().id(energyPotionID)
					.first().isEmpty() ? null : ctx.backpack.iterator().next();
			if (EnergyPotion != null) {
				status = "Use Potion";
				EnergyPotion.interact("Drink");
				final Timer potionTimer = new Timer(4500);
				while (potionTimer.isRunning()
						&& ctx.movement.getEnergyLevel() < 50) {
					sleep(Random.nextInt(150, 250));
				}
			}
		}

	}

	public boolean ShouldUse() {
		return ctx.players.local().isInMotion()
				&& ctx.movement.getEnergyLevel() < 50 && hasPotions()
				&& !ctx.bank.isOpen() && !ctx.widgets.get(1370, 40).isVisible();
	}

	private class Tan extends Job {
		public Tan(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return hasHides();
		}

		@Override
		public void execute() {
			Npc Tanner = ctx.npcs.select().id(tannerID).first().isEmpty() ? null
					: ctx.npcs.iterator().next();
			if (ctx.backpack.getMoneyPouch() < 600) {
				logOut();
			} else {
				if (atBurthorpe) {
					if (atTanner()) {
						if (Tanner != null) {
							if (!Tanner.isOnScreen()) {
								ctx.camera.turnTo(Tanner.getLocation());
							} else {
								doTanning();
							}
						}
					} else {
						status = "Walk to Jack";
						ctx.movement.newTilePath(pathToJack).traverse();
					}
				} else {
					if (atAlKharid) {
						if (atTanner()) {
							if (Tanner != null) {
								if (!Tanner.isOnScreen()) {
									ctx.camera.turnTo(Tanner.getLocation());
								} else {
									doTanning();
								}
							}
						} else {
							status = "Walk to Ellis";
							ctx.movement.newTilePath(pathToEllis).traverse();
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
			return !hasHides();
		}

		@Override
		public void execute() {
			status = "Walk to Bank";
			if (atBurthorpe) {
				if (atBank()) {
					doBanking();
				} else {
					ctx.movement.newTilePath(pathToBurthorpeBank).traverse();
				}
			} else {
				if (atAlKharid) {
					if (atBank()) {
						doBanking();
					} else {
						ctx.movement.newTilePath(pathToAlKharidBank).traverse();
					}
				}
			}
		}
	}

	public void doBanking() {
		if (ctx.bank.isOpen()) {
			if (!gotPrices) {
				getBankPrices();
				gotPrices = true;
			} else {
				if (ctx.backpack.select().count() == 28) {
					if (!hasLeather() && !hasPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (!hasLeather() && hasPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (hasLeather() && !hasPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (hasLeather() && hasPotions()) {
						status = "Deposit Hides";
						deposit(0, leatherID);
					}
				} else {
					if (!bankHasHides()) {
						logOut();
					} else if (hasPotions() && !hasHides()
							&& ctx.backpack.count() > 1) {
						status = "Reset";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (hasLeather() && hasPotions()) {
						status = "Deposit Hides";
						ctx.bank.deposit(IntHardLeatherID, 0);
					} else if (hasLeather() && !hasPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (ctx.backpack.select().count() > 0 && !hasHides()
							&& !hasPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (!hasPotions() && !hasHides() && banHasPotions()) {
						status = "Get Potion";
						withdraw(1, energyPotionID);
					} else if (!hasHides() && bankHasHides()) {
						status = "Get Hides";
						withdraw(0, hideID);
					}
					if (hasHides()) {
						hidesLeft = ctx.bank.select().id(hideID).count(true);
						potionsLeft = ctx.bank.select().id(energyPotionID)
								.count(true);
						status = "Close Bank";
						ctx.bank.close();
					}
				}
			}
		} else {
			status = "Bank Open";
			ctx.bank.open();
			while (ctx.players.local().isInMotion())
				sleep(Random.nextInt(25, 50));
		}
	}

	private boolean hasPotions() {
		Item Potions = ctx.backpack.select().id(energyPotionID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(Potions);
	}

	private boolean hasLeather() {
		Item TannedHides = ctx.backpack.select().id(leatherID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(TannedHides);
	}

	private boolean hasHides() {
		Item Hides = ctx.backpack.select().id(hideID).first().isEmpty() ? null
				: ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(Hides);
	}

	public boolean deposit(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.deposit(i, count)) {
				break;
			}
		}
		return true;
	}

	private boolean bankHasHides() {
		Item Hides = ctx.bank.select().id(hideID).first().isEmpty() ? null
				: ctx.bank.iterator().next();
		return ctx.bank.select().contains(Hides);
	}

	public boolean banHasPotions() {
		Item Potions = ctx.bank.select().id(energyPotionID).first().isEmpty() ? null
				: ctx.bank.iterator().next();
		return ctx.bank.select().contains(Potions);
	}

	public boolean withdraw(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.withdraw(i, count)) {
				break;
			}
		}
		return true;
	}

	public boolean atBank() {
		GameObject BankBooth = ctx.objects.select().id(bankBoothID).first()
				.isEmpty() ? null : ctx.objects.iterator().next();
		return BankBooth != null && BankBooth.isOnScreen();
	}

	public boolean atTanner() {
		Npc Tanner = ctx.npcs.select().id(tannerID).first().isEmpty() ? null
				: ctx.npcs.iterator().next();
		return Tanner != null
				&& ctx.players.local().getLocation()
						.distanceTo(Tanner.getLocation()) < 6;
	}

	public boolean inBurthorpe() {
		return areaBurthorpe.contains(ctx.players.local().getLocation());
	}

	public boolean inAlKharid() {
		return areaAlKharid.contains(ctx.players.local().getLocation());
	}

	public void doTanning() {
		Npc Tanner = ctx.npcs.select().id(tannerID).first().isEmpty() ? null
				: ctx.npcs.iterator().next();
		if (Tanner != null && Tanner.isOnScreen()) {
			status = "Interact";
			if (!ctx.widgets.get(1370, 20).isVisible()) {
				backpackHideCount = ctx.backpack.select().id(hideID).count();
				Tanner.interact("Tan");
				final Timer InteractTimer = new Timer(3500);
				while (InteractTimer.isRunning()
						&& !ctx.widgets.get(1370, 40).isValid()){
					sleep(Random.nextInt(100, 200));
				}
			} else {
				if (gotPrices)
					calculateMemberProfit();
				hideCount += backpackHideCount;
				ctx.widgets.get(1370, 20).interact("Make");
				final Timer WidgetTimer = new Timer(6500);
				while (WidgetTimer.isRunning()
						&& ctx.widgets.get(1370, 20).isValid() && !hasLeather()){
					sleep(Random.nextInt(100, 200));
				}
				if (gotPrices)
					calculateFreeProfit();
			}
		}
	}

	private void logOut() {
		status = "Log-out";
		if (ctx.bank.isOpen()) {
			if (ctx.backpack.select().count() > 0) {
				ctx.bank.depositInventory();
				stimer(ctx.backpack.select().count() > 0, 1600, 1800);
			} else {
				ctx.bank.close();
				ctx.game.logout(false);
				getController().stop();
			}
		}
	}

	private void stimer(boolean wait4, int int1, int int2) {
		if (int1 == 0 || int2 == 0) {
			int1 = 1300;
			int2 = 1600;
		}
		wait = new Timer(Random.nextInt(int1, int2));
		while (wait.isRunning() && wait4) {
			sleep(Random.nextInt(5, 10));
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
			profitTotal += Profit;
		}
		if (ctx.backpack.select().contains(BlackDragonhide)) {
			int GetCount = ctx.backpack.select().id(IntBlackDHideID).count();
			Profit = blackDragonLeatherPrice * GetCount - blackDHidePrice
					* GetCount;
			profitTotal += Profit;
		}
		if (ctx.backpack.select().contains(RoyalDragonhide)) {
			int GetCount = ctx.backpack.select().id(IntRoyalDHideID).count();
			Profit = royalDragonLeatherPrice * GetCount - royalDHidePrice
					* GetCount;
			profitTotal += Profit;
		}
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
			profitTotal += Profit;
		}
		if (ctx.backpack.select().contains(Leather)) {
			int GetHideCount = ctx.backpack.select().id(IntLeatherID).count();
			Profit = leatherPrice * GetHideCount - cowHidePrice * GetHideCount;
			profitTotal += Profit;
		}
	}

	private void getBankPrices() {
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
		status = "Fetching prices...";
		if (ctx.bank.contains(GreenDragonhide)) {
			greenDragonLeatherPrice = getGuidePrice(IntGreenDragonLeatherID) - 20;
			greenDHidePrice = getGuidePrice(IntGreenDHideID);
		}
		if (ctx.bank.contains(BlueDragonhide)) {
			blueDragonLeatherPrice = getGuidePrice(IntBlueDragonLeatherID) - 20;
			blueDHidePrice = getGuidePrice(IntBlueDHideID);
		}
		if (ctx.bank.contains(RedDragonhide)) {
			redDragonLeatherPrice = getGuidePrice(IntRedDragonLeatherID) - 20;
			redDHidePrice = getGuidePrice(IntRedDHideID);
		}
		if (ctx.bank.contains(SnakeSkinhide)) {
			snakeLeatherPrice = getGuidePrice(IntTannedSnakeSkinID) - 15;
			snakeSkinPrice = getGuidePrice(IntSnakeSkinhideID);
		}
		if (ctx.bank.contains(SnakeSkinTwohide)) {
			swampSnakeLeatherPrice = getGuidePrice(IntTannedSnakeSkinIDTwo) - 20;
			swampSnakeSkinPrice = getGuidePrice(IntSnakeSkinTwohideID);
		}
		if (ctx.bank.contains(BlackDragonhide)) {
			blackDragonLeatherPrice = getGuidePrice(IntBlackDragonLeatherID) - 20;
			blackDHidePrice = getGuidePrice(IntBlackDHideID);
		}
		if (ctx.bank.contains(RoyalDragonhide)) {
			royalDragonLeatherPrice = getGuidePrice(IntRoyalDragonLeatherID) - 20;
			royalDHidePrice = getGuidePrice(IntRoyalDHideID);
		}
		if (ctx.bank.contains(Cowhide)) {
			hardLeatherPrice = getGuidePrice(IntHardLeatherID) - 3;
			leatherPrice = getGuidePrice(IntLeatherID);
			cowHidePrice = getGuidePrice(IntCowhideID);
		}
	}

	final Color Black = new Color(0, 0, 0, 200);
	final Font Font = new Font("Tahoma", 0, 11);
	final Font FontTwo = new Font("Arial", 1, 12);
	final Font FONT_THREE = new Font("Arial", 1, 9);
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

		if (ctx.game.getClientState() != Game.INDEX_MAP_LOADED)
			return;

		g.setRenderingHints(antialiasing);
		g.setColor(Color.RED);
		g.drawRect(3, 285, 514, 105);
		g.setColor(Color.GREEN);
		g.setColor(Black);
		g.fillRect(3, 285, 514, 105);
		g.drawImage(tanner, 10, 140, null);
		g.setFont(FontTwo);
		g.setColor(Color.GREEN);
		g.drawString("rTanner", 240, 300);
		g.setFont(Font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 110,
				320);
		g.drawString("Tanned: " + nf.format(hideCount) + "("
				+ perHour(hideCount) + ")", 110, 340);
		g.drawString("Hides Left: " + nf.format(hidesLeft), 110, 360);
		g.drawString("Potions Left: " + nf.format(potionsLeft), 230, 320);
		g.drawString("User: " + getDisplayName(), 230, 340);
		g.drawString("Location: " + (location), 230, 360);
		g.drawString("Profit: " + nf.format(profitTotal) + "("
				+ perHour(profitTotal) + ")", 350, 320);
		g.drawString("Status: " + (status), 350, 340);
		g.setFont(FONT_THREE);
		g.setColor(Color.GREEN);
		g.drawString("v2.5", 490, 360);
		drawMouse(g);
	}

	private void drawMouse(Graphics g) {
		g.setColor(ctx.mouse.isPressed() ? Color.RED : Color.GREEN);
		final Point m = ctx.mouse.getLocation();
		g.drawLine(m.x - 5, m.y + 5, m.x + 5, m.y - 5);
		g.drawLine(m.x - 5, m.y - 5, m.x + 5, m.y + 5);
		g.fillOval(m.x - 3, m.y - 3, 6, 6);
		trail.add(m);
		trail.draw(g);
	}

	private static class mouseTrail {
		private final static int SIZE = 25;

		private Point[] points;
		private int index;

		public mouseTrail() {
			points = new Point[SIZE];
			index = 0;
		}

		public void add(Point p) {
			points[index++] = p;
			index %= SIZE;
		}

		public void draw(Graphics graphics) {
			double alpha = 0;

			for (int i = index; i != (index == 0 ? SIZE - 1 : index - 1); i = (i + 1)
					% SIZE) {
				if (points[i] != null && points[(i + 1) % SIZE] != null) {
					graphics.setColor(new Color(255, 0, 0, (int) alpha));
					graphics.drawLine(points[i].x, points[i].y, points[(i + 1)
							% SIZE].x, points[(i + 1) % SIZE].y);
					alpha += (255.0 / SIZE);
				}
			}
		}
	}

	private String getDisplayName() {
		return Environment.getDisplayName();
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

	public int getGuidePrice(final int id) {
		try {
			String price;
			final URL url = new URL(
					"http://www.tip.it/runescape/json/ge_single_item?item="
							+ id);
			final URLConnection con = url.openConnection();
			con.setRequestProperty(
					"User-Agent",
					"Mozilla/5.0 "
							+ "(Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
			final BufferedReader in = new BufferedReader(new InputStreamReader(
					con.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				if (line.contains("mark_price")) {
					price = line.substring(line.indexOf("mark_price") + 13,
							line.indexOf(",\"daily_gp") - 1);
					price = price.replace(",", "");
					in.close();
					return Integer.parseInt(price);
				}
			}
		} catch (final Exception ignored) {
			return -1;
		}
		return -1;
	}
}
