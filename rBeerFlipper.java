package rBeerFlipper;

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
import org.powerbot.script.util.Timer;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Interactive;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rBeerFlipper", description = "Buys Beer From The Bartender In Varrock.", hidden = true)
public class rBeerFlipper extends PollingScript implements PaintListener, MessageListener {
	private static long elapsedTime = 0;
	private static JobContainer container;
	private static String status = "Starting...";
	private static final Tile bartenderTile = new Tile(3224, 3399, 0), doorTile = new Tile(3215, 3395, 0);
	private static final int barTenderID = 733, beerID = 1917;
	private static int beerBought, beerPrice, beerInBank, profitGained, tries;
	
	private static final Tile[] pathToBartender = { new Tile(3189, 3435, 0),
			new Tile(3195, 3428, 0), new Tile(3198, 3420, 0),
			new Tile(3204, 3410, 0), new Tile(3209, 3405, 0),
			new Tile(3211, 3402, 0), new Tile(3212, 3396, 0), 
			new Tile(3224, 3399, 0) };
	

	@Override
	public void start() {
		elapsedTime = System.currentTimeMillis();
		beerPrice = getGuidePrice(beerID) - 2;
		rBeerFlipper.container = new JobContainer(new Job[] { new Buy(ctx), new Banking(ctx) });
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
		if (!ctx.game.isLoggedIn() || ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) 
			return 500;

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return job.delay();
		}

		return 50;
	}

	private class Buy extends Job {
		public Buy(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {

			return ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			final Component pressOne = ctx.widgets.get(1188, 3);
			if (nearClosedDoor()) {
				openDoor();
			} else if (atBartender()) {
				if (ctx.chat.isContinue()) {
					tries = 0;
					status = "Press Spacebar";
					ctx.keyboard.send(" ");
					final Timer pressTimer = new Timer(Random.nextInt(2000, 2200));
					while (pressTimer.isRunning() && ctx.chat.isContinue()) {
						sleep(15, 50);
					}
				} else if (pressOne.isValid()) {
					status = "Press 1";
					ctx.keyboard.send("1");
					final Timer pressTimer = new Timer(Random.nextInt(2000, 2200));
					while (pressTimer.isRunning() && pressOne.isVisible()) {
						sleep(10, 50);
					}
				} else {
					for (Npc Bartender : ctx.npcs.select().id(barTenderID).nearest()) {
						if (Bartender.isOnScreen()) {
							status = "Interact";
							if (!ctx.players.local().isInMotion()) {
								if (interact(Bartender, "Talk-to", "Bartender")) {
									tries ++;
									final Timer talkTimer = new Timer(Random.nextInt(1800, 2000));
									while (talkTimer.isRunning() && !ctx.chat.isContinue()) {
										sleep(15, 50);
									}
									if(tries > 1){
										ctx.camera.turnTo(Bartender.getLocation());
									}
								}
							}
						} else {
							ctx.camera.turnTo(bartenderTile);
						}
					}
				}
			} else if (ctx.bank.isOpen()) {
				status = "Close Bank";
				beerInBank = ctx.bank.select().id(beerID).count(true);
				ctx.bank.close();
			} else {
				status = "Walk to Bartender";
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(6, 10)) 
					ctx.movement.newTilePath(pathToBartender).traverse();
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
			if (nearClosedDoor()) {
				openDoor();
			} else {
				if (atBank()) {
					if (ctx.bank.isOpen()) {
						tries = 0;
						status = "Deposit Inventory";
						ctx.bank.depositInventory();
						final Timer depositTimer = new Timer(1800);
						while (depositTimer.isRunning() && ctx.backpack.select().count() == 28) {
							sleep(Random.nextInt(100, 650));
						}
					} else if (ctx.bank.isOnScreen()) {
						status = "Bank Open";
						ctx.bank.open();
					} else {
						ctx.movement.stepTowards(ctx.movement.getClosestOnMap(ctx.bank.getNearest()));
					}
				} else {
					status = "Walk to Banker";
					if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(6, 10)) 
						ctx.movement.newTilePath(pathToBartender).reverse().traverse();
				}
			}
		}
	}

	private void openDoor() {
		for (GameObject Door : ctx.objects.select().at(doorTile).nearest()) {
			status = "Door";
			if (!ctx.players.local().isInMotion()) {
				if (Door.isOnScreen()) {
					Door.click(true);
					final Timer doorTimer = new Timer(1800);
					while (doorTimer.isRunning() && Door != null) {
						sleep(Random.nextInt(100, 200));
					}
				} else if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(4, 5)) {
					ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Door));
				}
			}
		}
	}

	public boolean atBank() {
		return ctx.players.local().getLocation()
				.distanceTo(ctx.bank.getNearest()) < 4;
	}

	private boolean atBartender() {
		return ctx.players.local().getLocation().distanceTo(bartenderTile) < 4;
	}

	private boolean nearClosedDoor() {
		for (GameObject Door : ctx.objects.select().at(doorTile).nearest()) {
			if (ctx.players.local().getLocation().distanceTo(Door) < 10) {
				return true;
			}
		}
		return false;
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
					return arg0.action.equalsIgnoreCase(action)
							&& arg0.option.equalsIgnoreCase(option);
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

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message.contains("You buy a pint of beer.")) {
			beerBought++;
		}
	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font font = new Font("Times New Roman", 0, 13);
	final static Font fontTwo = new Font("Arial", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {
		long millis = System.currentTimeMillis() - elapsedTime;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;
		profitGained = beerBought * beerPrice;

		g.setColor(black);
		g.fillRect(6, 210, 200, 145);
		g.setColor(Color.RED);
		g.drawRect(6, 210, 200, 145);
		g.setFont(fontTwo);
		g.drawString("rBeerFlipper", 75, 222);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13, 245);
		g.drawString("Beer Bought: " + nf.format(beerBought) + "(" + perHour(beerBought) + "/h)", 13, 265);
		g.drawString("Beer Price: " + (beerPrice), 13, 285);
		g.drawString("Beer In Bank: " + (beerInBank), 13, 305);
		g.drawString("Profit: " + nf.format(profitGained) + "(" + perHour(profitGained) + "/h)", 13, 325);
		g.drawString("Status: " + (status), 13, 345);
		drawCross(g);
	}

	private void drawCross(Graphics g) {
		g.setColor(ctx.mouse.isPressed() ? Color.RED : Color.GREEN);
		g.drawLine(0, (int) (ctx.mouse.getLocation().getY()), 800,(int) (ctx.mouse.getLocation().getY()));
		g.drawLine((int) (ctx.mouse.getLocation().getX()), 0, (int) (ctx.mouse.getLocation().getX()), 800);
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

