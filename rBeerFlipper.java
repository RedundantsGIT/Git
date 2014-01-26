package rBeerFlipper;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
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
import org.powerbot.script.lang.Filter;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.methods.Game.Crosshair;
import org.powerbot.script.methods.Menu.Entry;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.GeItem;
import org.powerbot.script.util.Random;
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
		rBeerFlipper.container = new JobContainer(new Job[] { new Camera(ctx), new Buy(ctx), new Banking(ctx) });
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
	
	private class Camera extends Job {
		public Camera(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {

			return ctx.camera.getPitch() < 52;
		}

		@Override
		public void execute() {
			ctx.camera.setPitch(50);
		}

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
				if (ctx.widgets.get(1184, 9).getText().contains("What can I do yer for?")) {
					tries = 0;
					status = "Press Spacebar";
					ctx.keyboard.send(" ");
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return pressOne.isValid();
						}
					}, 250, 20);
				}else if (ctx.widgets.get(1191, 10).getText().contains("A glass of your finest ale please.")){
					status = "Press Spacebar";
					ctx.keyboard.send(" ");
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.widgets.get(1184, 9).getText().contains("No problemo. That'll be 2 coins.");
						}
					}, 250, 20);
					}else if  (ctx.widgets.get(1184, 9).getText().contains("No problemo. That'll be 2 coins.")){
						status = "Press Spacebar";
						ctx.keyboard.send(" ");
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !ctx.widgets.get(1184, 9).getText().contains("No problemo. That'll be 2 coins.");
						}
					}, 250, 20);
			} else if (pressOne.isValid()) {
					status = "Press 1";
					ctx.keyboard.send("1");
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !pressOne.isValid();
						}
					}, 250, 20);
				} else {
					for (Npc Bartender : ctx.npcs.select().id(barTenderID).nearest()) {
						if (Bartender.isOnScreen()) {
							status = "Interact";
							if (!ctx.players.local().isInMotion()) {
								if (interact(Bartender, "Talk-to", "Bartender")) {
									tries ++;
									Condition.wait(new Callable<Boolean>() {
										@Override
										public Boolean call() throws Exception {
											return ctx.chat.isContinue();
										}
									}, 250, 20);
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
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.backpack.select().isEmpty();
							}
						}, 250, 20);
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
		for (final GameObject Door : ctx.objects.select().at(doorTile).nearest()) {
			status = "Door";
			if (!ctx.players.local().isInMotion()) {
				if (Door.isOnScreen()) {
					Door.click(true);
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return Door == null;
						}
					}, 250, 20);
				} else if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(4, 5)) {
					ctx.movement.stepTowards(ctx.movement.getClosestOnMap(Door));
				}
			}
		}
	}

	public boolean atBank() {
		return ctx.players.local().getLocation().distanceTo(ctx.bank.getNearest()) < 4;
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

	public boolean interact(Interactive interactive, final String action, final String option) {
		if (interactive != null && interactive.isOnScreen()) {
			final Filter<Entry> filter = new Filter<Entry>() {
				@Override
				public boolean accept(Entry arg0) {
					return arg0.action.equalsIgnoreCase(action) && arg0.option.equalsIgnoreCase(option);
				}

			};
			
			if(Random.nextInt(1, 3) == 2)
			ctx.mouse.move(ctx.mouse.getLocation().x + Random.nextInt(1, 10), ctx.mouse.getLocation().y + Random.nextInt(1, 6));
			if (ctx.menu.click(filter)) {
				if(Random.nextInt(1, 5) == 3)
				ctx.mouse.move(ctx.mouse.getLocation().x + Random.nextInt(1, 15), ctx.mouse.getLocation().y + Random.nextInt(1, 12));
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
		if (message.contains("You buy a pint of beer."))
			beerBought++;
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
		g.drawString("v0.2", 175, 345);
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

	private static int getGuidePrice(final int id) {
		return GeItem.getPrice(id);
	}
}

