package rFurFlipper;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

import org.powerbot.event.MessageEvent;
import org.powerbot.event.MessageListener;
import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for (gp)", topic = 1135335)
public class rFurFlipper extends PollingScript implements PaintListener, MessageListener {
	private static JobContainer container;
	private static RenderingHints antialiasing = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String status = "Starting...";
	private static long scriptTimer = 0;
	private static int furPrice, furBought, furStored;
	private static int baraekID = 547, furID = 948;
	private final Component pressOne = ctx.widgets.get(1188, 2);
	private final Component achievements = ctx.widgets.get(1477).getComponent(74);
	private final Component collectionBox = ctx.widgets.get(109).getComponent(61);
	private final Component nameBox = ctx.widgets.get(1184, 10);
	private static final Tile[] pathToNpc = { new Tile(3189, 3435, 0), new Tile(3197, 3430, 0), new Tile(3206, 3430, 0), new Tile(3216, 3433, 0) };

	@Override
	public void start() {
		scriptTimer = System.currentTimeMillis();
		status = "Getting G.E. Fur Price";
		furPrice = getGuidePrice(furID) - 20;
		rFurFlipper.container = new JobContainer(new Job[] { new Camera(ctx), new Close(ctx), new Fix(ctx), new WalkToBaraek(ctx), new Talk(ctx), new PressOne(ctx), new Continue1(ctx),
				new Continue2(ctx), new Continue3(ctx), new WalkToBank(ctx), new Banking(ctx) });
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
		log.info("[rFurFlipper]: -Total Fur Purchased: " + furBought);
		log.info("[rFurFlipper]: -Total Profit Gained: " + profit());
		System.out.println("Script stopped");
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

	private class Camera extends Job {
		public Camera(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.getPitch() < 40 && !ctx.bank.isOpen();
		}

		@Override
		public void execute() {
			status = "Set Pitch";
			ctx.camera.setPitch(Random.nextInt(50, 55));
		}
	}

	private class Close extends Job {
		public Close(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return nameBox.getText().contains("Snow impling") || nameBox.getText().contains("Benny");
		}

		@Override
		public void execute() {
			status = "Close";
			close();
		}
	}

	private class Fix extends Job {
		public Fix(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return achievements.isVisible() || collectionBox.isVisible();
		}

		@Override
		public void execute() {
			status = "Close";
			close();
		}
	}

	private class WalkToBaraek extends Job {
		public WalkToBaraek(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() != 28 && !nearBaraek();
		}

		@Override
		public void execute() {
			if (ctx.bank.isOpen()) {
				status = "Close Bank";
				furStored = ctx.bank.select().id(furID).count(true);
				close();
			} else {
				status = "Walk to Baraek";
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(7, 9)) {
					ctx.movement.newTilePath(pathToNpc).traverse();
				}
			}
		}
	}

	private class Talk extends Job {
		public Talk(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return nearBaraek()
	                && !pressOne.isValid()
					&& !ctx.widgets.get(1191, 10).getText().contains("Can you sell me some furs?")
					&& !ctx.widgets.get(1184, 9).getText().contains("Yeah, sure. They're 20 gold coins each.")
					&& !ctx.widgets.get(1191, 10).getText().contains("Yeah, OK, here you go.")
					&& ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			status = "Talk";
			for (Npc baraek : ctx.npcs.select().id(baraekID).nearest()) {
				if (baraek.isOnScreen()) {
					if (baraek.interact("Talk")) {
						final Timer talkTimer = new Timer(Random.nextInt(2800, 3200));
						while (talkTimer.isRunning() && !pressOne.isValid())
							sleep(25, 50);
						break;
					} else {
						ctx.movement.stepTowards(ctx.movement.getClosestOnMap(baraek.getLocation()));
					}
				}
			}
		}
	}

	private class PressOne extends Job {
		public PressOne(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return pressOne.isValid();
		}

		@Override
		public void execute() {
			status = "Press 1";
			ctx.keyboard.send("1");
			final Timer pressTimer = new Timer(Random.nextInt(2100, 2200));
			while (pressTimer.isRunning() && pressOne.isVisible() && !ctx.chat.isContinue())
				sleep(100);
		}
	}

	private class Continue1 extends Job {
		public Continue1(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.widgets.get(1191, 10).getText().contains("Can you sell me some furs?");
		}

		@Override
		public void execute() {
			status = "Continue1";
			ctx.keyboard.send(" ");
			final Timer pressTimer = new Timer(Random.nextInt(2300, 2400));
			while (pressTimer.isRunning() && !ctx.widgets.get(1184, 9).getText().contains("Yeah, sure. They're 20 gold coins each."))
				sleep(100);
		}
	}

	private class Continue2 extends Job {
		public Continue2(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.widgets.get(1184, 9).getText().contains("Yeah, sure. They're 20 gold coins each.");
		}

		@Override
		public void execute() {
			status = "Continue2";
			ctx.keyboard.send(" ");
			final Timer pressTimer = new Timer(Random.nextInt(2300, 2400));
			while (pressTimer.isRunning() && !pressOne.isVisible());
				sleep(100);
		}
	}

	private class Continue3 extends Job {
		public Continue3(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.widgets.get(1191, 10).getText().contains("Yeah, OK, here you go.");
		}

		@Override
		public void execute() {
			status = "Continue3";
			ctx.keyboard.send(" ");
			final Timer pressTimer = new Timer(Random.nextInt(2300, 2400));
			while (pressTimer.isRunning() && !ctx.widgets.get(1189, 2).getText().contains("Baraek sells you a fur."))
				sleep(100);
		}
	}

	private class WalkToBank extends Job {
		public WalkToBank(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() == 28 && !nearBank();
		}

		@Override
		public void execute() {
			status = "Walk to Bank";
			if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation() .distanceTo(ctx.movement.getDestination()) < Random.nextInt(7, 9)) {
				ctx.movement.newTilePath(pathToNpc).reverse().traverse();
			}
		}
	}

	private class Banking extends Job {
		public Banking(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() == 28 && nearBank();
		}

		@Override
		public void execute() {
			if (ctx.bank.isOpen()) {
				status = "Deposit Inventory";
				ctx.bank.depositInventory();
				final Timer depositTimer = new Timer(Random.nextInt(2100, 2300));
				while (depositTimer.isRunning() && ctx.backpack.select().count() > 0)
					sleep(Random.nextInt(5, 15));
			} else {
				status = "Bank Open";
				ctx.camera.turnTo(ctx.bank.getNearest());
				ctx.bank.open();
			}
		}
	}

	private boolean close() {
		return ctx.keyboard.send("{VK_ESCAPE down}") && ctx.keyboard.send("{VK_ESCAPE up}");
	}

	public boolean nearBaraek() {
		for (Npc baraek : ctx.npcs.select().id(baraekID).nearest()) {
			if (ctx.players.local().getLocation()
					.distanceTo(baraek.getLocation()) < 7) {
				return true;
			}
		}
		return false;
	}

	public boolean nearBank() {
		return ctx.bank.isOnScreen() && ctx.players.local().getLocation().distanceTo(ctx.bank.getNearest()) < 9;
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

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message.contains("20 coins have been removed from your money pouch.")) {
			furBought++;
		}
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
		g.fillRect(6, 210, 200, 145);
		g.setColor(Color.GREEN);
		g.drawRect(6, 210, 200, 145);
		g.setFont(fontTwo);
		g.drawString("rFurFlipper", 75, 222);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13, 245);
		g.drawString("Fur Bought: " + nf.format(furBought) + "(" + PerHour(furBought) + "/h)", 13, 265);
		g.drawString("Fur In Bank: " + nf.format(furStored), 13, 285);
		g.drawString("Fur Price: " + furPrice, 13, 305);
		g.drawString("Profit: " + nf.format(profit()) + "(" + PerHour(profit()) + "/h)", 13, 325);
		g.drawString("Status: " + (status), 13, 345);
		drawMouse(g);
		drawBaraekTile(g);
	}

	private void drawBaraekTile(final Graphics g) {
		final Npc baraek = ctx.npcs.select().id(baraekID).nearest().poll();
		if (ctx.chat.isContinue() || pressOne.isValid()) {
			if (baraek.isOnScreen())
				baraek.getLocation().getMatrix(ctx).draw(g);
		}
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

	private static int profit() {
		return furBought * furPrice;
	}

	private void drawMouse(final Graphics g) {
		g.setColor(Color.GREEN);
		g.drawLine(0, (int) (ctx.mouse.getLocation().getY()), 800, (int) (ctx.mouse.getLocation().getY()));
		g.drawLine((int) (ctx.mouse.getLocation().getX()), 0, (int) (ctx.mouse.getLocation().getX()), 800);
	}

	private static int getGuidePrice(int itemId) {
		try {
			final URL website = new URL("http://www.tip.it/runescape/json/ge_single_item?item=" + itemId);

			final URLConnection conn = website.openConnection(); conn.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.2; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.57 Safari/537.36");
			conn.setRequestProperty("Connection", "close");
			final BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			final String json = br.readLine();
			return Integer.parseInt(json.substring(json.indexOf("mark_price") + 13, json.indexOf(",\"daily_gp") - 1).replaceAll(",", ""));
		} catch (Exception a) {
			System.out.println("Error looking up price for item: " + itemId);
			return -1;
		}
	}

}
