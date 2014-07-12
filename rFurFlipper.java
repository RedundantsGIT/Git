package rFurFlipper;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import org.powerbot.script.Condition;
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
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.Npc;

@Manifest(name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for money", properties = "topic=1135335")
public class rFurFlipper extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {
	private static JobContainer CONTAINER;
	private static RenderingHints ANTIALIASING = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String STATUS = "Starting...";
	private static long TIMER_SCRIPT = 0;
	private static int FUR_PRICE, FUR_BOUGHT, furStored;
	private static int ID_BARAEK = 547, ID_FUR = 948;
	private final Component WIDGET_MENU = ctx.widgets.component(1188, 3);
	private static final Tile[] PATH_TO_NPC = { new Tile(3189, 3435, 0), new Tile(3200, 3429, 0), new Tile(3208, 3431, 0), new Tile(3215, 3433, 0) };

	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
		STATUS = "Get prices..";
		FUR_PRICE = getGuidePrice(ID_FUR) - 20;
		CONTAINER = new JobContainer(new Job[] { new Camera(ctx), new Fix(ctx), new Menu(ctx), new Continue(ctx),
				new Talking(ctx), new Banking(ctx) });
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
		log.info("[rFurFlipper]: -Total Fur Purchased: " + FUR_BOUGHT);
		log.info("[rFurFlipper]: -Total Profit Gained: " + profit());
		log.info("Script stopped");
	}

	public abstract class Job extends ClientAccessor {
		public Job(ClientContext ctx) {
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
	public void poll() {
		if (!ctx.game.loggedIn())
			return;

		final Job job = CONTAINER.get();
		if (job != null) {
			job.execute();
			return;
		}
	}

	private class Camera extends Job {
		public Camera(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.pitch() < 45;
		}

		@Override
		public void execute() {
			STATUS = "Set Pitch";
			ctx.camera.pitch(Random.nextInt(48, 55));
		}
	}

	private class Fix extends Job {
		public Fix(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.widgets.component(1191, 10).text().contains("Can I have a newspaper, please?");
		}

		@Override
		public void execute() {
			STATUS = "Close Menu";
			close();
		}
	}

	private class Menu extends Job {
		public Menu(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return WIDGET_MENU.valid();
		}

		@Override
		public void execute() {
			STATUS = "Select Option";
			ctx.input.send("1");
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return !WIDGET_MENU.visible() && ctx.chat.queryContinue();
				}
			}, 250, 20);
		}
	}

	private class Continue extends Job {
		public Continue(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.widgets.component(1191, 10).valid() || ctx.widgets.component(1184, 10).valid();
		}

		@Override
		public void execute() {
			STATUS = "Select Continue";
			if (ctx.widgets.component(1191, 10).text().contains("Can you sell me some furs?") || ctx.widgets.component(1191, 10).text().contains("Yeah, OK, here you go.")) {
				ctx.input.send(" ");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return ctx.widgets.component(1184, 9).text().contains("Yeah, sure. They're 20 gold coins each.") || ctx.widgets.component(1189, 2).text().contains("Baraek sells you a fur.");
					}
				}, 250, 20);
			} else {
				ctx.input.send(" ");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return WIDGET_MENU.valid();
					}
				}, 250, 20);
			}
		}
	}

	private class Talking extends Job {
		public Talking(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.backpack.select().count() != 28;
		}

		@Override
		public void execute() {
			final Npc baraek = ctx.npcs.select().id(ID_BARAEK).nearest().poll();
			if (ctx.backpack.moneyPouchCount() < 20) {
				ctx.controller.stop();
			} else {
			if (ctx.players.local().tile().distanceTo(baraek.tile()) < 8) {
					if (baraek.inViewport()) {
						STATUS = "Talk to Baraek";
						if (baraek.interact("Talk-to", "Baraek")) {
							if (didInteract()) {
								Condition.wait(new Callable<Boolean>() {
									@Override
									public Boolean call() throws Exception {
										return WIDGET_MENU.valid();
									}
								}, 250, 20);
							}
						
					} else {
						STATUS = "Walk to Npc";
						ctx.movement.step(ctx.movement.closestOnMap(baraek.tile()));
						ctx.camera.turnTo(baraek.tile());
						while (ctx.players.local().inMotion() && !baraek.inViewport());
					}
				}
			} else {
				if (ctx.bank.opened()) {
					STATUS = "Close Bank";
					furStored = ctx.bank.select().id(ID_FUR).count(true);
					if (Random.nextInt(1, 15) == 10)
						ctx.bank.close();
					else
						close();
				} else {
					STATUS = "Walk to Npc";
					if (!ctx.players.local().inMotion() || ctx.players.local().tile().distanceTo(ctx.movement.destination()) < Random.nextInt(5, 8)) {
						ctx.movement.step(getNextTile(randomizePath(PATH_TO_NPC, 2, 2)));
						}
					}
				}
			}
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
				if (ctx.bank.opened()) {
					STATUS = "Deposit";
					ctx.bank.depositInventory();
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.backpack.select().isEmpty();
						}
					}, 250, 20);
			} else {
				if (ctx.bank.inViewport() && ctx.players.local().tile().distanceTo(ctx.bank.nearest()) < 6) {
					STATUS = "Bank Open";
					ctx.camera.turnTo(ctx.bank.nearest());
					ctx.bank.open();
				} else {
				STATUS = "Walk to Bank";
				if (!ctx.players.local().inMotion() || ctx.players.local().tile().distanceTo(ctx.movement.destination()) < Random.nextInt(5, 8)) {
					ctx.movement.step(getNextTile(randomizePath(reversePath(PATH_TO_NPC), 3, 3)));
						if (Random.nextInt(1, 10) == 5)
							ctx.camera.turnTo(ctx.bank.nearest());
					}
				}

			}
		}
	}

	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}

	private boolean close() {
		ctx.input.send("{VK_ESCAPE down}");
		Condition.sleep(Random.nextInt(50, 200));
		return ctx.input.send("{VK_ESCAPE up}");
	}


	public Tile[] randomizePath(Tile[] path, int maxXDeviation,
			int maxYDeviation) {
		Tile[] rez = new Tile[path.length];

		for (int i = 0; i < path.length; i++) {
			int x = path[i].x();
			int y = path[i].y();
			if (maxXDeviation > 0) {
				double d = Math.random() * 2 - 1.0;
				d *= maxXDeviation;
				x += (int) d;
			}
			if (maxYDeviation > 0) {
				double d = Math.random() * 2 - 1.0;
				d *= maxYDeviation;
				y += (int) d;
			}
			rez[i] = new Tile(x, y, path[i].floor());
		}

		return rez;
	}

	public Tile getNextTile(Tile[] path) {
		int dist = 99;
		int closest = -1;
		for (int i = path.length - 1; i >= 0; i--) {
			Tile tile = path[i];
			int d = distanceTo(tile);
			if (d < dist) {
				dist = d;
				closest = i;
			}
		}

		int feasibleTileIndex = -1;

		for (int i = closest; i < path.length; i++) {

			if (distanceTo(path[i]) <= 16) {
				feasibleTileIndex = i;
			} else {
				break;
			}
		}

		if (feasibleTileIndex == -1) {
			return null;
		} else {
			return path[feasibleTileIndex];
		}
	}

	public int distanceTo(Tile tile) {
		return (int) ctx.players.local().tile().distanceTo(tile);
	}
	
	private Tile[] reversePath(Tile tiles[]) {
		Tile r[] = new Tile[tiles.length];
		int i;
		for (i = 0; i < tiles.length; i++) {
			r[i] = tiles[(tiles.length - 1) - i];
		}
		return r;
	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();
		if (message.contains("20 coins have been removed from your money pouch."))
			FUR_BOUGHT++;
	}

	final static Color BLACK = new Color(25, 0, 0, 200);
	final static Font FONT = new Font("Comic Sans MS", 1, 12);
	final static NumberFormat NF = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {

		final Graphics2D g = (Graphics2D) g1;

		long millis = System.currentTimeMillis() - TIMER_SCRIPT;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setRenderingHints(ANTIALIASING);
		g.setColor(BLACK);
		g.fillRect(5, 5, 190, 145);
		g.setColor(Color.RED);
		g.drawRect(5, 5, 190, 145);
		g.setFont(FONT);
		g.drawString("rFurFlipper", 70, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Fur Bought: " + NF.format(FUR_BOUGHT) + "(" + PerHour(FUR_BOUGHT) + "/h)", 10, 60);
		g.drawString("Fur In Bank: " + NF.format(furStored), 10, 80);
		g.drawString("Fur Price: " + FUR_PRICE, 10, 100);
		g.drawString("Profit: " + NF.format(profit()) + "(" + PerHour(profit()) + "/h)", 13, 120);
		g.drawString("Status: " + (STATUS), 10, 140);
		g.setColor(Color.RED);
		g.drawString("v1.5", 165, 140);
		drawMouse(g);
		drawTrail(g);
		drawBaraekTile(g);
	}

	public String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System.currentTimeMillis() - TIMER_SCRIPT)));
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

	private mouseTrail trail = new mouseTrail();

	private void drawTrail(final Graphics g) {
		final Point m = ctx.input.getLocation();
		trail.add(m);
		trail.draw(g);
	}

	private final class mouseTrail {
		private final int SIZE = 50;
		private final double ALPHA_STEP = (255.0 / SIZE);
		private final Point[] points;
		private int index;

		public mouseTrail() {
			points = new Point[SIZE];
			index = 0;
		}

		public void add(final Point p) {
			points[index++] = p;
			index %= SIZE;
		}

		public void draw(final Graphics g) {
			double alpha = 0;
			for (int i = index; i != (index == 0 ? SIZE - 1 : index - 1); i = (i + 1) % SIZE) {
				if (points[i] != null && points[(i + 1) % SIZE] != null) {
					Color rainbow = Color.getHSBColor((float) (alpha / 255), 1, 1);
					g.setColor(new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), (int) alpha));
					g.drawLine(points[i].x, points[i].y, points[(i + 1) % SIZE].x, points[(i + 1) % SIZE].y);
					alpha += ALPHA_STEP;
				}
			}
		}
	}

	private static int profit() {
		return FUR_BOUGHT * FUR_PRICE;
	}

	public void drawMouse(Graphics2D g) {
		Point p = ctx.input.getLocation();
		g.setColor(Color.RED);
		g.setStroke(new BasicStroke(2));
		g.fill(new Rectangle(p.x + 1, p.y - 4, 2, 15));
		g.fill(new Rectangle(p.x - 6, p.y + 2, 16, 2));
	}
	
	private void drawBaraekTile(final Graphics g) {
		final Npc Baraek = ctx.npcs.select().id(ID_BARAEK).nearest().poll();
			if (Baraek.inViewport() && ctx.backpack.select().count() != 28)
				Baraek.tile().matrix(ctx).draw(g);
	}

	private static int getGuidePrice(final int id) {
		return GeItem.price(id);
	}

}
