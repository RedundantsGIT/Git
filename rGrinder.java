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
import java.util.concurrent.Callable;

import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.methods.Hud.Window;
import org.powerbot.script.methods.Hud;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.Item;

@Manifest(name = "rGrinder", description = "Grinds Chocolate Bars Into Dust", hidden = true)
public class rGrinder extends PollingScript implements PaintListener {
	private static JobContainer container;

	private static long TimeElapsed = 0;
	private static String Status = "Starting..";

	private static final int ChocolateBarID = 1973;
	private static final int ChocolateDustID = 1975;
	private static int ChocolateBarPrice;
	private static int ChocolateDustPrice;
	private static int ChocolateDustGained;
	private static int ChocolateBarsLeft;
	private int ChocolateDustAmount = ctx.backpack.select().id(ChocolateDustID)
			.count();

	@Override
	public void start() {
		ChocolateBarPrice = getGuidePrice(ChocolateBarID);
		ChocolateDustPrice = getGuidePrice(ChocolateDustID) - ChocolateBarPrice;
		TimeElapsed = System.currentTimeMillis();
		rGrinder.container = new JobContainer(new Job[] { new Grinding(ctx), new Menu(ctx), new Grind(ctx), new Bank(ctx) });
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
			return 50;
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
	
	private class Grinding extends Job {
		public Grinding(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return Grinding();
		}

		@Override
		public void execute() {
			while (Grinding()) {
				Status = "Grinding...";
				sleep(Random.nextInt(15, 50));
			}

		}
	}

	private class Menu extends Job {
		public Menu(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.chat.isContinue() || ctx.widgets.get(1188, 3).isValid();
		}

		@Override
		public void execute() {
			if (ctx.widgets.get(1188, 3).isVisible()) {
				Status = "Press 1";
				ctx.keyboard.send("1");
			} else {
				Status = "Click Continue";
				ctx.chat.clickContinue();
				sleep(Random.nextInt(250, 500));
			}
		}
	}

	private class Grind extends Job {
		public Grind(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return BackpackContainsBars();
		}

		@Override
		public void execute() {
			final Component Make = ctx.widgets.get(1370, 20);
			final Item ChocolateBar = ctx.backpack.select().id(ChocolateBarID).poll();
			if (ctx.bank.isOpen()) {
				Status = "Close Bank";
				ctx.bank.close();
			} else if (Make.isValid()) {
				Status = "Make";
				if (Make.interact("Make")) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !Make.isVisible();
						}
					}, 250, 20);
				}
			} else if (ctx.hud.isVisible(Window.BACKPACK)) {
				Status = "Open Menu";
				if (ChocolateBar.interact("Powder", "Chocolate Bar")) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return Make.isValid();
						}
					}, 250, 20);
				}

			} else {
				Status = "Open Backpack";
				if (ctx.hud.view(Hud.Window.BACKPACK)) {
					sleep(Random.nextInt(50, 250));
				}
			}

		}
	}

	private class Bank extends Job {
		public Bank(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return !BackpackContainsBars();
		}

		@Override
		public void execute() {
			if (ctx.bank.isOpen()) {
				if (ctx.backpack.select().count() > 0) {
					DepositInventory();
				} else if (BankContainsBars()) {
					Status = "Withdraw Bars";
					ChocolateBarsLeft = ctx.bank.select().id(ChocolateBarID).count(true);
					ctx.bank.withdraw(ChocolateBarID, 0);
				} else {
					LogOut();
				}
			} else {
				Status = "Bank Open";
				if (Random.nextInt(1, 5) == 3) {
					ctx.camera.turnTo(ctx.bank.getNearest().getLocation());
					if (ctx.camera.getPitch() > 55)
						ctx.camera.setPitch(Random.nextInt(32, 35));
				}
				if (ctx.bank.isOnScreen()) {
					Status = "Bank Open";
					ctx.bank.open();
				} else {
					Status = "Walk to Bank";
					ctx.movement.stepTowards(ctx.bank.getNearest()
							.getLocation());
				}
			}

		}
	}

	private boolean Grinding() {
		final Component Grinding = ctx.widgets.get(1251, 11);
		return Grinding.isVisible();
	}

	private boolean BackpackContainsBars() {
		return ctx.backpack.select().id(ChocolateBarID).count() > 0;
	}

	private boolean BankContainsBars() {
		return ctx.bank.select().id(ChocolateBarID).count() > 0;
	}

	private void DepositInventory() {
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

	private void LogOut() {
		Status = "Log Out";
		if (ctx.bank.isOpen() && ctx.backpack.select().count() > 0) {
			DepositInventory();
			ctx.bank.close();
		}
		if (ctx.game.logout(true)) {
			getController().stop();
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

	final static Color Black = new Color(25, 0, 0, 200);
	final static Font FontTwo = new Font("Comic Sans MS", 1, 12);
	final static NumberFormat Nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {

		long millis = System.currentTimeMillis() - TimeElapsed;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		if (ctx.backpack.select().id(ChocolateDustID).count() == 0) {
			ChocolateDustAmount = 0;
		}

		if (ChocolateDustAmount < ctx.backpack.select().id(ChocolateDustID).count()) {
			ChocolateDustGained += ctx.backpack.select().id(ChocolateDustID).count() - ChocolateDustAmount;
			ChocolateDustAmount = ctx.backpack.select().id(ChocolateDustID).count();

		}

		g.setColor(Black);
		g.fillRect(6, 210, 200, 145);
		g.setColor(Color.CYAN);
		g.drawRect(6, 210, 200, 145);
		g.setFont(FontTwo);
		g.drawString("rGrinder", 80, 222);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13, 245);
		g.drawString("Dust Gained: " + Nf.format(ChocolateDustGained) + "(" + PerHour(ChocolateDustGained) + "/h)", 13, 265);
		g.drawString("Bars Left: " + Nf.format(ChocolateBarsLeft), 13, 285);
		g.drawString("Profit Ea: " + (ChocolateDustPrice), 13, 305);
		g.drawString("Profit: " + Nf.format(Profit()) + "(" + PerHour(Profit()) + "/h)", 13, 325);
		g.drawString("Status: " + (Status), 13, 345);
		DrawMouse(g);
		BankerTile(g);

	}

	private static int Profit() {
		return ChocolateDustGained * ChocolateDustPrice;
	}

	private static String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - TimeElapsed)));
	}

	private static String formatNumber(int start) {
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

	private void DrawMouse(final Graphics g) {
		final Point m = ctx.mouse.getLocation();
		g.setColor(Grinding() ? Color.GREEN : Color.CYAN);
		g.drawLine(m.x - 5, m.y + 5, m.x + 5, m.y - 5);
		g.drawLine(m.x - 5, m.y - 5, m.x + 5, m.y + 5);
	}
	
	private void BankerTile(final Graphics g) {
		if (ctx.bank.isOnScreen()) {
			ctx.bank.getNearest().getLocation().getMatrix(ctx).draw(g);
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
