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

@Manifest(authors = { "Redundant" }, name = "rTanner", description = "Tans all hides in Al-Kharid & Burthorpe for (gp) [Supports all hides/potions]", website = "http://www.powerbot.org/community/topic/876982-vip-rtanner-all-potions-all-hides-al-kharid-burthorpe/", version = 2.2)
public class rTanner extends PollingScript implements PaintListener {
	private final RenderingHints antialiasing = new RenderingHints(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	public long ElapsedTime = 0;
	public Timer wait;
	public Image tanner = getImage("http://i43.tinypic.com/rh288o.jpg");
	public MouseTrail trail = new MouseTrail();
	public int InventoryHideCount, HideCount, ProfitTotal, HidesLeft,
			PotionsLeft;
	public final JobContainer container;
	public boolean gotPrices = false;
	public String status = "Starting...";
	public String location;
	public boolean atBurthorpe = false;
	public boolean atAlKharid = false;
	public final int[] energyPotionID = { 3008, 3010, 3012, 3014, 23375, 23377,
			23379, 23381, 23383, 23385, 11453, 11455, 23387, 23389, 23391,
			23393, 23395, 23397, 11481, 11483, 3016, 3018, 3020, 3022 };
	public final int[] hideID = { 1739, 1753, 1751, 24372, 6287, 7801, 1749,
			1747 };
	public final int[] leatherID = { 1741, 1743, 1745, 2505, 24374, 6289, 2507,
			2509 };
	public final Tile[] pathToJack = { new Tile(2893, 3529, 0),
			new Tile(2892, 3517, 0), new Tile(2889, 3511, 0),
			new Tile(2887, 3503, 0) };
	public final Tile[] pathToEllis = { new Tile(3271, 3167, 0),
			new Tile(3276, 3180, 0), new Tile(3275, 3195, 0) };
	public final int[] bankBoothID = { 76274, 42192 },
			tannerID = { 14877, 2824 };
	private final Area areaBurthorpe = new Area(new Tile[] {
			new Tile(2877, 3540, 0), new Tile(2900, 3540, 0),
			new Tile(2899, 3479, 0), new Tile(2875, 3479, 0) });
	private final Area areaAlKharid = new Area(new Tile[] {
			new Tile(3263, 3203, 0), new Tile(3287, 3203, 0),
			new Tile(3287, 3157, 0), new Tile(3262, 3158, 0) });
	public final int[] snakeSkinhideID = { 6287 },
			SnakeSkinTwohideID = { 7801 }, GreenDHideID = { 1753 },
			BlueDHideID = { 1751 }, RedDHideID = { 1749 },
			BlackDHideID = { 1747 }, RoyalDHideID = { 24372 },
			HardLeatherID = { 1743 }, LeatherID = { 1741 };
	public final int IntCowhideID = 1739, IntSnakeSkinhideID = 6287,
			IntSnakeSkinTwohideID = 7801, IntGreenDHideID = 1753,
			IntBlueDHideID = 1751, IntRedDHideID = 1749,
			IntBlackDHideID = 1747, IntRoyalDHideID = 24372;
	public final int IntLeatherID = 1741, IntHardLeatherID = 1743,
			IntGreenDragonLeatherID = 1745, IntBlueDragonLeatherID = 2505,
			IntBlackDragonLeatherID = 2509, IntRoyalDragonLeatherID = 24374,
			IntTannedSnakeSkinID = 6289, IntTannedSnakeSkinIDTwo = 6289,
			IntRedDragonLeatherID = 2507;
	public int CowHidePrice, SnakeSkinPrice, SnakeSkinTwoPrice,
			GreenDHidePrice, BlueDHidePrice, RedDHidePrice, BlackDHidePrice,
			RoyalDHidePrice, IntLeatherPrice, IntHardLeatherPrice,
			IntGreenDragonLeatherPrice, IntRedDragonLeatherPrice,
			IntBlueDragonLeatherPrice, IntBlackDragonLeatherPrice,
			IntRoyalDragonLeatherPrice, IntTannedSnakeSkinPrice,
			IntTannedSnakeSkinPriceTwo, Profit;

	public rTanner() {
		ElapsedTime = System.currentTimeMillis();

		this.container = new JobContainer(new Job[] {// new Pitch(ctx),
				new GetPlayerArea(ctx), new UseEnergyPotion(ctx), new Tan(ctx),
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
			if (InBurthorpe()) {
				location = "Burthorpe";
				atBurthorpe = true;
			} else {
				if (InAlKharid()) {
					location = "Al Kharid";
					atAlKharid = true;
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
			return ShouldUse();
		}

		@Override
		public void execute() {
			final Item EnergyPotion = ctx.backpack.select().id(energyPotionID)
					.first().isEmpty() ? null : ctx.backpack.iterator().next();

			if (EnergyPotion != null) {
				status = "Use Potion";
				EnergyPotion.interact("Drink");
				final Timer potionTimer = new Timer(6500);
				while (potionTimer.isRunning()
						&& ctx.movement.getEnergyLevel() < 50)
					;
				sleep(Random.nextInt(150, 250));
			}
		}

	}

	public boolean ShouldUse() {
		return ctx.players.local().isInMotion()
				&& ctx.movement.getEnergyLevel() < 50 && ContainsPotions()
				&& !ctx.bank.isOpen() && !ctx.widgets.get(1370, 40).isVisible();
	}

	private class Tan extends Job {
		public Tan(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			Item Hides = ctx.backpack.select().id(hideID).first().isEmpty() ? null
					: ctx.backpack.iterator().next();
			return ctx.backpack.select().contains(Hides);
		}

		@Override
		public void execute() {
			Npc Tanner = ctx.npcs.select().id(tannerID).first().isEmpty() ? null
					: ctx.npcs.iterator().next();
			if (ctx.backpack.getMoneyPouch() < 600) {
				LogOut();
			} else {
				if (atBurthorpe) {
					if (atTanner()) {
						if (Tanner != null) {
							if (!Tanner.isOnScreen()) {
								ctx.camera.turnTo(Tanner.getLocation());
							} else {
								DoTanning();
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
									DoTanning();
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
			Item Hides = ctx.backpack.select().id(hideID).first().isEmpty() ? null
					: ctx.backpack.iterator().next();
			return !ctx.backpack.select().contains(Hides);
		}

		@Override
		public void execute() {
			status = "Walk to Bank";
			if (atBurthorpe) {
				if (!NearBank()) {
					ctx.movement.newTilePath(pathToJack).reverse().traverse();
				} else {
					PerformBanking();
				}
			} else {
				if (atAlKharid) {
					if (!NearBank()) {
						ctx.movement.newTilePath(pathToEllis).reverse()
								.traverse();
					} else {
						PerformBanking();
					}
				}
			}
		}
	}

	public void PerformBanking() {
		if (!ctx.bank.isOpen()) {
			status = "Bank Open";
			ctx.bank.open();
			sleep(Random.nextInt(200, 300));
			while (ctx.players.local().isInMotion())
				sleep(50);
		} else {
			if (!gotPrices) {
				GetPricesFromBank();
				gotPrices = true;
			} else {
				/* Inventory is full. */
				if (ctx.backpack.select().count() == 28) {
					if (!ContainsTannedHides() && !ContainsPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (!ContainsTannedHides() && ContainsPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (ContainsTannedHides() && !ContainsPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (ContainsTannedHides() && ContainsPotions()) {
						status = "Deposit Hides";
						deposit(0, leatherID);
					}
				} else {/* Inventory isn't full */
					if (!BankContainsHides()) {
						LogOut();
					} else if (ContainsPotions() && !ContainsHides()
							&& ctx.backpack.count() > 1) {
						status = "Reset";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (ContainsTannedHides() && ContainsPotions()) {
						status = "Deposit Hides";
						ctx.bank.deposit(IntHardLeatherID, 0);
					} else if (ContainsTannedHides() && !ContainsPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (ctx.backpack.select().count() > 0
							&& !ContainsHides() && !ContainsPotions()) {
						status = "Deposit Inv";
						ctx.bank.depositInventory();
						stimer(ctx.backpack.select().count() > 0, 0, 0);
					} else if (!ContainsPotions() && !ContainsHides()
							&& BankContainsPotions()) {
						status = "Get Potion";
						withdraw(1, energyPotionID);
					} else if (!ContainsHides() && BankContainsHides()) {
						status = "Get Hides";
						withdraw(0, hideID);
					}
					if (ContainsHides()) {
						HidesLeft = ctx.bank.select().id(hideID).count(true);
						PotionsLeft = ctx.bank.select().id(energyPotionID)
								.count(true);
						status = "Close Bank";
						ctx.bank.close();
					}
				}
			}
		}
	}

	public boolean withdraw(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.withdraw(i, count)) {
				System.out.println("Succesfully withdrew all of " + i);
				break;
			}
		}
		return true;
	}

	public boolean deposit(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.deposit(i, count)) {
				System.out.println("Succesfully deposited all of " + i);
				break;
			}
		}
		return true;
	}

	private boolean ContainsPotions() {
		Item Potions = ctx.backpack.select().id(energyPotionID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(Potions);
	}

	private boolean ContainsTannedHides() {
		Item TannedHides = ctx.backpack.select().id(leatherID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(TannedHides);
	}

	private boolean ContainsHides() {
		Item Hides = ctx.backpack.select().id(hideID).first().isEmpty() ? null
				: ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(Hides);
	}

	private boolean BankContainsHides() {
		Item Hides = ctx.bank.select().id(hideID).first().isEmpty() ? null
				: ctx.bank.iterator().next();
		return ctx.bank.contains(Hides);
	}

	public boolean BankContainsPotions() {
		Item Potions = ctx.bank.select().id(energyPotionID).first().isEmpty() ? null
				: ctx.bank.iterator().next();
		return ctx.bank.contains(Potions);
	}

	public boolean NearBank() {
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

	public boolean InBurthorpe() {
		return areaBurthorpe.contains(ctx.players.local().getLocation());
	}

	public boolean InAlKharid() {
		return areaAlKharid.contains(ctx.players.local().getLocation());
	}

	public void DoTanning() {
		final Npc Tanner = ctx.npcs.select().id(tannerID).first().isEmpty() ? null
				: ctx.npcs.iterator().next();
		if (Tanner != null && Tanner.isOnScreen()) {
			status = "Interact";
			if (!ctx.widgets.get(1370, 20).isVisible()) {
				InventoryHideCount = ctx.backpack.select().id(hideID).count();
				Tanner.interact("Tan");
				final Timer InteractTimer = new Timer(3500);
				while (InteractTimer.isRunning()
						&& !ctx.widgets.get(1370, 40).isValid())
					sleep(Random.nextInt(100, 200));
			} else {
				if (gotPrices)
					MembersProfitCalculations();
				HideCount += InventoryHideCount;
				ctx.widgets.get(1370, 20).interact("Make");
				final Timer WidgetTimer = new Timer(6500);
				while (WidgetTimer.isRunning()
						&& ctx.widgets.get(1370, 20).isValid()
						&& !ContainsTannedHides())
					sleep(Random.nextInt(100, 200));
				if (gotPrices)
					NonMembersProfitCalculations();
			}
		}
	}

	private void LogOut() {
		status = "Log-out";
		if (ctx.bank.isOpen()) {
			if (ctx.backpack.getAllItems().length > 0) {
				ctx.bank.depositInventory();
				stimer(ctx.backpack.getAllItems().length > 0, 0, 0);
			}
		}
		ctx.bank.close();
		ctx.game.logout(false);
		getController().stop();
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

	private void MembersProfitCalculations() {

		Item GreenDragonhide = ctx.backpack.select().id(GreenDHideID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		Item BlueDragonhide = ctx.backpack.select().id(BlueDHideID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		Item RedDragonhide = ctx.backpack.select().id(RedDHideID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		Item SnakeSkinhide = ctx.backpack.select().id(snakeSkinhideID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		Item SnakeSkinTwohide = ctx.backpack.select().id(SnakeSkinTwohideID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		Item BlackDragonhide = ctx.backpack.select().id(BlackDHideID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		Item RoyalDragonhide = ctx.backpack.select().id(RoyalDHideID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();

		if (ctx.backpack.select().contains(GreenDragonhide)) {
			int GetCount = ctx.backpack.select().id(GreenDHideID).count();
			Profit = IntGreenDragonLeatherPrice * GetCount - GreenDHidePrice
					* GetCount;
			ProfitTotal += Profit;
		}
		if (ctx.backpack.select().contains(BlueDragonhide)) {
			int GetCount = ctx.backpack.select().id(BlueDHideID).count();
			Profit = IntBlueDragonLeatherPrice * GetCount - BlueDHidePrice
					* GetCount;
			ProfitTotal += Profit;
		}
		if (ctx.backpack.select().contains(RedDragonhide)) {
			int GetCount = ctx.backpack.select().id(RedDHideID).count();
			Profit = IntRedDragonLeatherPrice * GetCount - RedDHidePrice
					* GetCount;
			ProfitTotal += Profit;
		}
		if (ctx.backpack.select().contains(SnakeSkinhide)) {
			int GetCount = ctx.backpack.select().id(snakeSkinhideID).count();
			Profit = -IntTannedSnakeSkinPrice * GetCount - SnakeSkinPrice
					* GetCount;
			ProfitTotal += Profit;
		}
		if (ctx.backpack.select().contains(SnakeSkinTwohide)) {
			int GetCount = ctx.backpack.select().id(SnakeSkinTwohideID).count();
			Profit = IntTannedSnakeSkinPriceTwo * GetCount - SnakeSkinTwoPrice
					* GetCount;
			ProfitTotal += Profit;
		}
		if (ctx.backpack.select().contains(BlackDragonhide)) {
			int GetCount = ctx.backpack.select().id(BlackDHideID).count();
			Profit = IntBlackDragonLeatherPrice * GetCount - BlackDHidePrice
					* GetCount;
			ProfitTotal += Profit;
		}
		if (ctx.backpack.select().contains(RoyalDragonhide)) {
			int GetCount = ctx.backpack.select().id(RoyalDHideID).count();
			Profit = IntRoyalDragonLeatherPrice * GetCount - RoyalDHidePrice
					* GetCount;
			ProfitTotal += Profit;
		}
	}

	private void NonMembersProfitCalculations() {
		Item HardLeather = ctx.backpack.select().id(HardLeatherID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		Item Leather = ctx.backpack.select().id(LeatherID).first().isEmpty() ? null
				: ctx.backpack.iterator().next();
		if (ctx.backpack.select().contains(HardLeather)) {
			int GetHideCount = ctx.backpack.select().id(HardLeatherID).count();
			Profit = IntHardLeatherPrice * GetHideCount - CowHidePrice
					* GetHideCount;
			ProfitTotal += Profit;
		}
		if (ctx.backpack.select().contains(Leather)) {
			int GetHideCount = ctx.backpack.select().id(LeatherID).count();
			Profit = IntLeatherPrice * GetHideCount - CowHidePrice
					* GetHideCount;
			ProfitTotal += Profit;
		}
	}

	private void GetPricesFromBank() {
		Item GreenDragonhide = ctx.bank.select().id(BlueDHideID).first()
				.isEmpty() ? null : ctx.bank.iterator().next();

		Item BlueDragonhide = ctx.bank.select().id(BlueDHideID).first()
				.isEmpty() ? null : ctx.bank.iterator().next();

		Item RedDragonhide = ctx.bank.select().id(RedDHideID).first().isEmpty() ? null
				: ctx.bank.iterator().next();

		Item SnakeSkinhide = ctx.bank.select().id(snakeSkinhideID).first()
				.isEmpty() ? null : ctx.bank.iterator().next();

		Item SnakeSkinTwohide = ctx.bank.select().id(SnakeSkinTwohideID)
				.first().isEmpty() ? null : ctx.bank.iterator().next();

		Item BlackDragonhide = ctx.bank.select().id(BlackDHideID).first()
				.isEmpty() ? null : ctx.bank.iterator().next();

		Item RoyalDragonhide = ctx.bank.select().id(RoyalDHideID).first()
				.isEmpty() ? null : ctx.bank.iterator().next();

		Item Cowhide = ctx.bank.select().id(IntCowhideID).first().isEmpty() ? null
				: ctx.bank.iterator().next();

		status = "Fetching prices...";
		if (ctx.bank.contains(GreenDragonhide)) {
			IntGreenDragonLeatherPrice = getGuidePrice(IntGreenDragonLeatherID) - 20;
			GreenDHidePrice = getGuidePrice(IntGreenDHideID);
		}
		if (ctx.bank.contains(BlueDragonhide)) {
			IntBlueDragonLeatherPrice = getGuidePrice(IntBlueDragonLeatherID) - 20;
			BlueDHidePrice = getGuidePrice(IntBlueDHideID);
		}
		if (ctx.bank.contains(RedDragonhide)) {
			IntRedDragonLeatherPrice = getGuidePrice(IntRedDragonLeatherID) - 20;
			RedDHidePrice = getGuidePrice(IntRedDHideID);
		}
		if (ctx.bank.contains(SnakeSkinhide)) {
			IntTannedSnakeSkinPrice = getGuidePrice(IntTannedSnakeSkinID) - 15;
			SnakeSkinPrice = getGuidePrice(IntSnakeSkinhideID);
		}
		if (ctx.bank.contains(SnakeSkinTwohide)) {
			IntTannedSnakeSkinPriceTwo = getGuidePrice(IntTannedSnakeSkinIDTwo) - 20;
			SnakeSkinTwoPrice = getGuidePrice(IntSnakeSkinTwohideID);
		}
		if (ctx.bank.contains(BlackDragonhide)) {
			IntBlackDragonLeatherPrice = getGuidePrice(IntBlackDragonLeatherID) - 20;
			BlackDHidePrice = getGuidePrice(IntBlackDHideID);

		}
		if (ctx.bank.contains(RoyalDragonhide)) {
			IntRoyalDragonLeatherPrice = getGuidePrice(IntRoyalDragonLeatherID) - 20;
			RoyalDHidePrice = getGuidePrice(IntRoyalDHideID);

		}
		if (ctx.bank.contains(Cowhide)) {
			IntHardLeatherPrice = getGuidePrice(IntHardLeatherID) - 3;
			IntLeatherPrice = getGuidePrice(IntLeatherID);
			CowHidePrice = getGuidePrice(IntCowhideID);
		}
	}

	final Color Black = new Color(0, 0, 0, 200);
	final Font Font = new Font("Tahoma", 0, 11);
	final Font FontTwo = new Font("Arial", 1, 12);
	final Font FONT_THREE = new Font("Arial", 1, 9);
	final NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {
		long millis = System.currentTimeMillis() - ElapsedTime;
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
		g.drawString("Tanned: " + nf.format(HideCount) + "("
				+ PerHour(HideCount) + ")", 110, 340);
		g.drawString("Hides Left: " + nf.format(HidesLeft), 110, 360);
		g.drawString("Potions Left: " + nf.format(PotionsLeft), 230, 320);
		g.drawString("User: " + (DisplayName()), 230, 340);
		g.drawString("Location: " + (location), 230, 360);
		g.drawString("Profit: " + nf.format(ProfitTotal) + "("
				+ PerHour(ProfitTotal) + ")", 350, 320);
		g.drawString("Status: " + (status), 350, 340);
		g.setFont(FONT_THREE);
		g.setColor(Color.GREEN);
		g.drawString("v2.2", 490, 360);
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

	private static class MouseTrail {
		private final static int SIZE = 25;

		private Point[] points;
		private int index;

		public MouseTrail() {
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

	
 private String DisplayName() { 
		return Environment.getDisplayName(); 
	}

	public String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - ElapsedTime)));
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

	private static int getGuidePrice(final int id) {
		final String add = "http://scriptwith.us/api/?return=text&item=" + id;
		try (final BufferedReader in = new BufferedReader(
				new InputStreamReader(new URL(add).openConnection()
						.getInputStream()))) {
			final String line = in.readLine();
			return Integer.parseInt(line.split("[:]")[1]);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}
}
