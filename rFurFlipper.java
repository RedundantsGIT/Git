package rFurFlipper;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
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
import org.powerbot.script.methods.Game;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.util.Random;
import org.powerbot.script.util.Timer;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Locatable;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for profit.", version = 0.4, hidden = true)
public class rFurFlipper extends PollingScript implements PaintListener,
		MessageListener {
	private static Timer timeRan = new Timer(0);
	private static String status = "Starting...";
	private static long scriptTimer = 0;
	private static int furPrice, furBought, furStored;
	private static int baraekID = 547, furID = 948;
	private static int[] boothID = { 782 };
	public final Tile[] pathToBaraek = { new Tile(3189, 3435, 0),
			new Tile(3197, 3430, 0), new Tile(3206, 3429, 0),
			new Tile(3216, 3433, 0) };

	public JobContainer container;

	@Override
	public void start() {
		System.out.println("Script started");
		scriptTimer = System.currentTimeMillis();
		status = "Getting G.E. Fur Price";
		furPrice = getGuidePrice(furID);
		this.container = new JobContainer(new Job[] { new BuyFur(ctx),
				new Banking(ctx) });
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
		System.out.println("[rFurFlipper]: -Total Time: "
				+ timeRan.toElapsedString());
		System.out.println("[rFurFlipper]: -Total Fur Purchased: " + furBought);
		System.out.println("[rFurFlipper]: -Total Profit Gained: " + profit());
		System.out.println("Script stopped");
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

		return 50;
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
			final Component pressOne = ctx.widgets.get(1188, 2);
			final Component canContinue1 = ctx.widgets.get(1191, 13);
			final Component canContinue2 = ctx.widgets.get(1184, 13);
			final Npc baraek = ctx.npcs.select().id(baraekID).first().isEmpty() ? null
					: ctx.npcs.iterator().next();
			if (nearBaraek()) {
				if (baraek != null) {
					if (ctx.backpack.getMoneyPouch() < 20) {
						System.out
								.println("[rFurFlipper]: -Not enough gold left to continue, stopping script.. .");
						getController().stop();
					} else if (!baraek.isOnScreen()
							&& !ctx.players.local().isInMotion()) {
						ctx.movement.stepTowards(ctx.movement.getClosestOnMap(baraek.getLocation()));
					} else if (!canContinue1.isValid()
							&& !canContinue2.isValid() && !pressOne.isVisible()) {
						status = "Talk to Baraek";
					 mouseMoveSlightly();
						baraek.interact("Talk-to", "Baraek");
						final Timer talkTimer = new Timer(1600);
						while (talkTimer.isRunning() && !pressOne.isVisible()) {
							sleep(50, 100);
						}
						while (ctx.players.local().isInMotion()) {
							sleep(50, 150);
						}
					} else if (pressOne.isValid()) {
						status = "Press 1";
						log.info("1");
						ctx.keyboard.send("1");
						final Timer pressTimer = new Timer(1500);
						while (pressTimer.isRunning() && pressOne.isVisible()) {
							sleep(25, 50);
						}
					} else if (canContinue1.isValid()) {
						status = "Press Spacebar";
						log.info("2");
						ctx.keyboard.send(" ");
						final Timer pressTimer = new Timer(1500);
						while (pressTimer.isRunning() && canContinue1.isValid()) {
							sleep(25, 50);
						}
					} else {
						status = "Press Spacebar";
						log.info("3");
						ctx.keyboard.send(" ");
						final Timer pressTimer = new Timer(1500);
						while (pressTimer.isRunning()
								&& canContinue2.isVisible()) {
							sleep(25, 50);
						}
					}
				}
			} else {
				status = "Walk to Baraek";
				if (ctx.bank.isOpen()) {
					ctx.bank.close();
				} else {
					ctx.movement.newTilePath(pathToBaraek).traverse();
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
			GameObject booth = ctx.objects.select().id(boothID).first()
					.isEmpty() ? null : ctx.objects.iterator().next();
			if (nearBank()) {
				if (booth != null && booth.isOnScreen()) {
					if (ctx.bank.isOpen()) {
						status = "Deposit Inventory";
						ctx.bank.depositInventory();
						final Timer depositTimer = new Timer(1700);
						while (depositTimer.isRunning()
								&& ctx.backpack.select().count() == 28) {
							sleep(Random.nextInt(150, 250));
						}
						furStored = ctx.bank.select().id(furID).count(true);
						status = "Bank Close";
						ctx.bank.close();
					} else {
						status = "Bank Open";
						ctx.bank.open();
					}
				}
			} else {
				status = "Walk to Banker";
				ctx.movement.newTilePath(pathToBaraek).reverse().traverse();
			}
		}
	}

	public boolean nearBaraek() {
		for (Npc baraek : ctx.npcs.select().id(baraekID).nearest()) {
			if (baraek != null
					&& ctx.players.local().getLocation()
							.distanceTo(baraek.getLocation()) < 6) {
				return true;
			}
		}
		return false;
	}

	public boolean nearBank() {
		for (GameObject booth : ctx.objects.select().id(boothID).nearest()) {
			if (booth != null
					&& ctx.players.local().getLocation()
							.distanceTo(booth.getLocation()) < 4) {
				return true;
			}
		}
		return false;
	}
	

	public void mouseMoveSlightly() {
		Point p = new Point(
				(int) (ctx.mouse.getLocation().getX() + (Math.random() * 50 > 25 ? 1
						: -1)
						* (20 + Math.random() * 70)), (int) (ctx.mouse
						.getLocation().getY() + (Math.random() * 50 > 25 ? 1
						: -1) * (20 + Math.random() * 85)));
		if (p.getX() < 1 || p.getY() < 1 || p.getX() > 761 || p.getY() > 499) {
			mouseMoveSlightly();
			return;
		}
		ctx.mouse.move(p);
	}

	public void turnTo(final Locatable l) {
		int turnAngle = ctx.camera.getAngleTo(l.getLocation().getPlane()
				+ Random.nextInt(-40, 40));
		int distance = (int) ctx.players.local().getLocation().distanceTo(l);
		int xl = (int) (turnAngle * 2.86);
		int yl = (int) ((125 - ctx.getClient().getCameraPitch()
				- Random.nextInt(18, 28) - distance * 5.11) * 2.55);
		Point p1 = new Point(xl > 0 ? Random.nextInt(20, 500 - Math.abs(xl))
				: Random.nextInt(20 + Math.abs(xl), 500),
				yl > 0 ? Random.nextInt(100, 360 - Math.abs(yl)) : Random
						.nextInt(100 + Math.abs(yl), 360));
		Point p2 = new Point(xl > 0 ? (int) p1.getX() + Math.abs(xl)
				: (int) p1.getX() - Math.abs(xl), yl > 0 ? (int) p1.getY()
				+ Math.abs(yl) : (int) p1.getY() - Math.abs(yl));
		ctx.mouse.drag(p1, p2, 2);
	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message
				.contains("20 coins have been removed from your money pouch."))
			furBought++;

	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font font = new Font("Times New Roman", 0, 13);
	final static Font fontTwo = new Font("Arial", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {
		if (ctx.game.getClientState() != Game.INDEX_MAP_LOADED) {
			return;
		}

		long millis = System.currentTimeMillis() - scriptTimer;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setColor(black);
		g.fillRect(6, 210, 200, 145);
		g.setColor(Color.RED);
		g.drawRect(6, 210, 200, 145);
		g.setFont(fontTwo);
		g.drawString("rFurFlipper", 75, 222);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13,
				245);
		g.drawString("Fur Bought: " + nf.format(furBought) + "("
				+ PerHour(furBought) + "/h)", 13, 265);
		g.drawString("Fur In Bank: " + nf.format(furStored), 13, 285);
		g.drawString("Fur Price: " + furPrice, 13, 305);
		g.drawString("Profit: " + nf.format(profit()) + "(" + PerHour(profit())
				+ "/h)", 13, 325);
		g.drawString("Status: " + (status), 13, 345);
		drawCross(g);
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

	private int profit() {
		int cost = 0;
		if (furBought != 0)
			cost = -20;
		return furBought * furPrice - cost;
	}

	private void drawCross(Graphics g) {
		g.setColor(ctx.mouse.isPressed() ? Color.GREEN : Color.RED);
		g.drawLine(0, (int) (ctx.mouse.getLocation().getY()), 800,
				(int) (ctx.mouse.getLocation().getY()));
		g.drawLine((int) (ctx.mouse.getLocation().getX()), 0,
				(int) (ctx.mouse.getLocation().getX()), 800);

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
