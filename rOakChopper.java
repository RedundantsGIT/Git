package rOakChopper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import org.powerbot.script.Area;
import org.powerbot.script.Condition;
import org.powerbot.script.Filter;
import org.powerbot.script.MessageEvent;
import org.powerbot.script.MessageListener;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.Tile;
import org.powerbot.script.rt6.ClientAccessor;
import org.powerbot.script.rt6.ClientContext;
import org.powerbot.script.rt6.Component;
import org.powerbot.script.rt6.Game.Crosshair;
import org.powerbot.script.rt6.GameObject;
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.Skills;

@Manifest(name = "rOakChopper", description = "Chops oak trees at the Grand Exchange", properties = "hidden=true")
public class rOakChopper extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {

	private static long elapsedTime = 0;
	private static RenderingHints antialiasing = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String status = "Starting...";
	private static int logsChopped, logPrice, profitGained, logsInBank, tries;
	private static final int oakLogID = 1521;
	private static int startExperience = 0;
	private static int startLevel = 0;
	private static int[] oakID = { 38732, 38731 };
	private final Component geWindow = ctx.widgets.component(105, 87).component(1);
	public final static Area oakArea = new Area(new Tile[] {
			new Tile(3168, 3472, 0), new Tile(3228, 3472, 0),
			new Tile(3220, 3417, 0), new Tile(3176, 3418, 0) });
	private static final Tile[] pathToOak = new Tile[] {
			new Tile(3180, 3502, 0), new Tile(3183, 3498, 0),
			new Tile(3186, 3494, 0), new Tile(3192, 3491, 0),
			new Tile(3195, 3487, 0), new Tile(3197, 3483, 0),
			new Tile(3198, 3479, 0), new Tile(3197, 3475, 0),
			new Tile(3196, 3471, 0), new Tile(3196, 3467, 0),
			new Tile(3195, 3464, 0) };

	private static final Filter<GameObject> FILTER_TREE = new Filter<GameObject>() {
		public boolean accept(GameObject object) {
			Arrays.sort(oakID);
			return Arrays.binarySearch(oakID, object.id()) >= 0
					&& oakArea.contains(object.tile());
		}
	};

	private static JobContainer container;

	@Override
	public void start() {
		System.out.println("Script started");
		elapsedTime = System.currentTimeMillis();
		logPrice = getGuidePrice(oakLogID);
		startExperience = ctx.skills.experience(Skills.WOODCUTTING);
		startLevel = ctx.skills.level(Skills.WOODCUTTING);
		rOakChopper.container = new JobContainer(new Job[] {
				new CloseInterfaces(ctx), new AntiPattern(ctx), new Banking(ctx), new Chopping(ctx) });
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

	public abstract class Job extends ClientAccessor {
		public Job(ClientContext ctx) {
			super(ctx);
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
	public void poll() {
		if (!ctx.game.loggedIn())
			return;

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return;
		}

		return;
	}

	private class CloseInterfaces extends Job {
		public CloseInterfaces(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return geWindow.visible();
		}

		@Override
		public void execute() {
			status = "Close";
			ctx.camera.turnTo(ctx.bank.nearest());
			close();
		}
	}
	
	private class AntiPattern extends Job {
		public AntiPattern(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.players.local().animation() != -1;
		}

		@Override
		public void execute() {
			status = "Chopping...";
			antiBan();
			log.info("ANTIPATTERN" );
		}
	}

	private class Banking extends Job {
		public Banking(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() == 28;
		}

		@Override
		public void execute() {
			if (atBanker()) {
				if (ctx.bank.opened()) {
					status = "Banking";
					ctx.bank.depositInventory();
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.backpack.select().isEmpty();
						}
					}, 250, 20);
					logsInBank = ctx.bank.select().id(oakLogID).count(true);
				} else {
					status = "Bank Open";
					ctx.bank.open();
				}
			} else {
				status = "Walk to Bank";
				if (!ctx.players.local().inMotion()
						|| ctx.players.local().tile()
								.distanceTo(ctx.movement.destination()) < Random
								.nextInt(7, 9))
					ctx.movement.newTilePath(pathToOak).reverse().traverse();
			}
		}
	}

	private class Chopping extends Job {
		public Chopping(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.players.local().animation() == -1 && ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			if (oakArea.contains(ctx.players.local().tile())) {
				for (GameObject oak : ctx.objects.select().select(FILTER_TREE).nearest().first()) {
						if (oak.inViewport()) {
							if (tries > 1) {
								ctx.camera.pitch(Random.nextInt(30, 40));
								ctx.camera.turnTo(oak.tile());
								tries = 0;
							} else {
								status = "Interact with oak";
								log.info("Chop down");
								if (oak.interact("Chop down")) {
									if (Random.nextInt(1, 3) == 2) {
										mouseMoveSlightly();
									}
									tries++;
									if(didInteract()){
									Condition.wait(new Callable<Boolean>() {
										@Override
										public Boolean call() throws Exception {
											return ctx.players.local()
													.animation() != -1;
										}
									}, 250, 20);
								}
							}
						}
						} else {
							status = "Walk to tree";
							log.info("Walk to tree");
							if (ctx.camera.pitch() < 30) {
								ctx.camera.pitch(Random.nextInt(35, 60));
							}
							if (!ctx.players.local().inMotion()
									|| ctx.players
											.local()
											.tile()
											.distanceTo(
													ctx.movement.destination()) < 3)
								ctx.movement.step(ctx.movement
										.closestOnMap(oak).tile());
						}
					}
			} else {
				if (ctx.bank.opened()) {
					status = "Bank Close";
					log.info("Close bank");
					if (Random.nextInt(1, 15) == 10)
						ctx.bank.close();
					else
						close();
				} else {
					status = "Walk to Oaks";
					log.info("Walking to oaks");
					if (!ctx.players.local().inMotion()
							|| ctx.players.local().tile()
									.distanceTo(ctx.movement.destination()) < Random
									.nextInt(7, 9))
						ctx.movement.newTilePath(pathToOak).traverse();
				}
			}

		}
	}

	private boolean atBanker() {
		return ctx.bank.inViewport()
				&& ctx.players.local().tile().distanceTo(ctx.bank.nearest()) < 6;
	}

	public void mouseMoveSlightly() {
		Point p = new Point(
				(int) (ctx.input.getLocation().getX() + (Math.random() * 50 > 25 ? 1
						: -1)
						* (20 + Math.random() * 70)), (int) (ctx.input
						.getLocation().getY() + (Math.random() * 50 > 25 ? 1
						: -1) * (20 + Math.random() * 85)));
		if (p.getX() < 1 || p.getY() < 1 || p.getX() > 761 || p.getY() > 499) {
			mouseMoveSlightly();
			return;
		}
		ctx.input.move(p);
	}
	

	private boolean close() {
		ctx.input.send("{VK_ESCAPE down}");
		Condition.sleep(Random.nextInt(50, 200));
		return ctx.input.send("{VK_ESCAPE up}");
	}

	public boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}

	private int antiBan() {
		int antiban = Random.nextInt(1, 1000);
		switch (antiban) {
		case 1:
			ctx.camera.angle(Random.nextInt(21, 40));
			break;
		case 2:
			ctx.camera.angle(Random.nextInt(25, 75));
			break;
		case 3:
			ctx.camera.angle(Random.nextInt(0, 200));
			break;
		case 4:
			ctx.camera.angle(Random.nextInt(0, 300));
			break;
		case 5:
			ctx.input.move(Random.nextInt(0, (int) (ctx.game.dimensions()
					.getWidth() - 1)), 0);
			break;
		case 6:
			ctx.input.hop(Random.nextInt(-10, (int) (ctx.game.dimensions()
					.getWidth() + 10)), (int) (ctx.game.dimensions()
					.getHeight() + Random.nextInt(10, 100)));
			break;
		case 7:
			ctx.input.move(Random.nextInt(0, 500), Random.nextInt(0, 500));
			break;
		case 8:
			ctx.input.hop(Random.nextInt(0, 500), Random.nextInt(0, 500));
			break;
		case 9:
			ctx.camera.pitch(Random.nextInt(40, 55));
			ctx.camera.angle(Random.nextInt(0, 300));
			break;
		}
		return 0;
	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();
		if (message.contains("You get some oak logs."))
			logsChopped++;
	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font font = new Font("Times New Roman", 0, 13);
	final static Font fontTwo = new Font("Arial", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {

		final Graphics2D g = (Graphics2D) g1;

		long millis = System.currentTimeMillis() - elapsedTime;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		profitGained = logsChopped * logPrice;

		g.setRenderingHints(antialiasing);
		g.setColor(black);
		g.fillRect(6, 210, 205, 168);
		g.setColor(Color.RED);
		g.drawRect(6, 210, 205, 168);
		g.setFont(fontTwo);
		g.drawString("rOakChopper", 75, 222);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 13,
				245);
		g.drawString("Chopped: " + nf.format(logsChopped) + "("
				+ PerHour(logsChopped) + "/h)", 13, 265);
		g.drawString("Profit: " + nf.format(profitGained) + "("
				+ PerHour(profitGained) + "/h)", 13, 285);
		g.drawString(
				"Exp: "
						+ nf.format(ctx.skills.experience(Skills.WOODCUTTING)
								- startExperience)
						+ "("
						+ PerHour(ctx.skills.experience(Skills.WOODCUTTING)
								- startExperience) + "/h" + ")" + "+"
						+ (ctx.skills.level(Skills.WOODCUTTING) - startLevel),
				13, 305);
		g.drawString("Log Price: " + (logPrice), 13, 325);
		g.drawString("Logs In Bank: " + (logsInBank), 13, 345);
		g.drawString("Status: " + (status), 13, 365);
		drawMouse(g);

	}

	public String PerHour(int gained) {
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

	public void drawMouse(Graphics2D g) {
		Point p = ctx.input.getLocation();
		g.setColor(Color.RED);
		g.setStroke(new BasicStroke(2));
		g.fill(new Rectangle(p.x + 1, p.y - 4, 2, 15));
		g.fill(new Rectangle(p.x - 6, p.y + 2, 16, 2));
	}

	private static int getGuidePrice(final int id) {
		return GeItem.price(id);
	}
}
