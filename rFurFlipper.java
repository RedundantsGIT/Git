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
import org.powerbot.script.lang.Filter;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.methods.Game.Crosshair;
import org.powerbot.script.methods.Menu.Entry;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.Interactive;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for profit.")
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
	private static final Tile[] pathToNpc = { new Tile(3189, 3435, 0), new Tile(3197, 3430, 0), new Tile(3206, 3430, 0), new Tile(3216, 3433, 0) };

	@Override
	public void start() {
		log.info("Script started");
		scriptTimer = System.currentTimeMillis();
		status = "Getting G.E. Fur Price";
		furPrice = getGuidePrice(furID) - 20;
		rFurFlipper.container = new JobContainer(new Job[] { new Camera(ctx), new Fix(ctx), new BuyFur(ctx), new Banking(ctx) });
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
		log.info("[rFurFlipper]: -Total Fur Purchased: " + furBought);
		log.info("[rFurFlipper]: -Total Profit Gained: " + profit());
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
			ctx.camera.setPitch(Random.nextInt(50, 55));
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
			log.info("1");
		    close();
		}
	}

	private class BuyFur extends Job {
		public BuyFur(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			final Npc baraek = ctx.npcs.select().id(baraekID).nearest().poll();
			final Component nameBox = ctx.widgets.get(1184, 10);
			if (nearBaraek()) {
				if (ctx.backpack.getMoneyPouch() < 20) {
					log.info("[rFurFlipper]: -Not enough gold left to continue, stopping script.. .");
					getController().stop();
				}else if(nameBox.getText().contains("Snow impling") || nameBox.getText().contains("Benny")){
					status = "Close";
					close();
				}else if (ctx.chat.isContinue()) {
					status = "Continue";
					if (Random.nextInt(1, 20) == 10) 
						ctx.chat.clickContinue();
					 else 
						ctx.keyboard.send(" ");
					final Timer pressTimer = new Timer(Random.nextInt(1600, 1800));
					while (pressTimer.isRunning() && ctx.chat.isContinue()) 
						sleep(10, 20);
				} else if (pressOne.isValid()) {
					status = "Press 1";
					ctx.keyboard.send("1");
					final Timer pressTimer = new Timer(Random.nextInt(1600, 1800));
					while (pressTimer.isRunning() && pressOne.isVisible()) 
						sleep(10, 25);
				} else {
						status = "Talk to Baraek";
						if (baraek.isOnScreen()) {
							if (interact(baraek, "Talk-to", "Baraek")) {
								final Timer talkTimer = new Timer(Random.nextInt(2600, 2800));
								while (talkTimer.isRunning() && !pressOne.isVisible() || ctx.players.local().isInMotion() && !pressOne.isValid()) 
									sleep(25, 50);
						} else {
							ctx.movement.stepTowards(ctx.movement.getClosestOnMap(baraek.getLocation()));
						}
					}
				}
			} else {
				if (ctx.bank.isOpen()) {
					status = "Close Bank";
					furStored = ctx.bank.select().id(furID).count(true);
					if(Random.nextInt(1, 15) == 5)
					ctx.bank.close();
					else
					close();
				} else {
					status = "Walk to Baraek";
					if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(7, 9)) 
						ctx.movement.newTilePath(pathToNpc).traverse();
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
			if (nearBank()) {
				if (ctx.bank.isOpen()) {
					status = "Deposit Inventory";
					depositInventory();
				} else {
					status = "Bank Open";
					ctx.camera.turnTo(ctx.bank.getNearest());
					ctx.bank.open();
				}
			} else {
				status = "Walk to Banker";
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(7, 9)) 
					ctx.movement.newTilePath(pathToNpc).reverse().traverse();
			}
		}
	}

	public boolean didInteract() {
		return ctx.game.getCrosshair() == Crosshair.ACTION;
	}

	public boolean interact(Interactive interactive, final String action,
			final String option) {
		if (interactive != null && interactive.isOnScreen()) {
			final Filter<Entry> filter = new Filter<Entry>() {

				@Override
				public boolean accept(Entry arg0) {
					return arg0.action.equalsIgnoreCase(action) && arg0.option.equalsIgnoreCase(option);
				}

			};
			if (ctx.menu.click(filter)) {
				return didInteract();
			} else {
				ctx.mouse.move(interactive);
				return interact(interactive, action, option);
			}
		}
		return false;
	}

	private void depositInventory() {
		final Component DepositBackpackButton = ctx.widgets.get(762, 11);
		if (DepositBackpackButton.isVisible()) {
			if (DepositBackpackButton.interact("Deposit carried items")) {
				final Timer depositTimer = new Timer(Random.nextInt(2100, 2300));
				while (depositTimer.isRunning() && ctx.backpack.select().count() > 0) 
					sleep(Random.nextInt(5, 15));
			}
		}
	}
	
	private boolean close() {
		return ctx.keyboard.send("{VK_ESCAPE down}") && ctx.keyboard.send("{VK_ESCAPE up}");
	}

	public boolean nearBaraek() {
		final Npc baraek = ctx.npcs.select().id(baraekID).nearest().poll();
		return ctx.players.local().getLocation().distanceTo(baraek.getLocation()) < 7;
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

			final URLConnection conn = website.openConnection();
			conn.addRequestProperty( "User-Agent", "Mozilla/5.0 (Windows NT 6.2; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.57 Safari/537.36");
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
