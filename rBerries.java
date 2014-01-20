import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
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
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.methods.MethodProvider;
import org.powerbot.script.methods.Game.Crosshair;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Area;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rBerries", description = "Picks berries in Varrock", hidden = true)
public class rBerries extends PollingScript implements PaintListener {
	private static JobContainer container;

	private long TIMER_ELAPSED = 0;
	private int BERRIES_GAINED;
	private final int[] ID_BERRIES = { 23628, 23629, 23625, 23626 };
	
	private final Area AREA_GRAPES = new Area(new Tile[] {
			new Tile(3264, 3375, 0), new Tile(3295, 3376, 0),
			new Tile(3291, 3362, 0), new Tile(3269, 3362, 0) });

	private final Tile[] PATH_TO_GRAPES = { new Tile(3253, 3420, 0),
			new Tile(3254, 3425, 0), new Tile(3258, 3428, 0),
			new Tile(3266, 3428, 0), new Tile(3270, 3428, 0),
			new Tile(3279, 3427, 0), new Tile(3282, 3421, 0),
			new Tile(3281, 3416, 0), new Tile(3282, 3413, 0),
			new Tile(3286, 3411, 0), new Tile(3289, 3407, 0),
			new Tile(3291, 3403, 0), new Tile(3292, 3398, 0),
			new Tile(3292, 3393, 0), new Tile(3292, 3389, 0),
			new Tile(3292, 3384, 0), new Tile(3292, 3378, 0),
			new Tile(3287, 3373, 0), new Tile(3279, 3373, 0) };

	@Override
	public void start() {
		TIMER_ELAPSED = System.currentTimeMillis();
		rBerries.container = new JobContainer(new Job[] { new Menu(ctx), new Berries(ctx), new Bank(ctx) });
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
		if (!ctx.game.isLoggedIn() || ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) 
			return 500;

		final Job job = container.get();
		if (job != null) {
			job.execute();
			return job.delay();
		}

		return 50;
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
				ctx.keyboard.send("1");
			} else {
				ctx.chat.clickContinue();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !ctx.chat.isContinue();
					}
				}, 250, 20);
			}
		}
	}

	private class Berries extends Job {
		public Berries(MethodContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return !isFull();
		}

		@Override
		public void execute() {
			int count = ctx.backpack.select().count();
			int TRIES = 0;
			if (atGrapes()) {
				for (GameObject Bush : ctx.objects.select().select().id(ID_BERRIES).nearest().first()) {
					if (Bush.isOnScreen() && ctx.players.local().getLocation().distanceTo(Bush.getLocation()) < 3) {
						if (TRIES > 1) {
							ctx.camera.turnTo(Bush.getLocation());
						}
						Bush.interact("Pick");
						if (didInteract()) {
							final Timer LootingTimer = new Timer(Random.nextInt(3000, 3500));
							while (LootingTimer.isRunning() && ctx.backpack.select().count() != count + 1) {
								sleep(Random.nextInt(10, 20));
							}
						}
						TRIES++;
						if (ctx.backpack.select().count() == count + 1) {
							TRIES = 0;
							BERRIES_GAINED ++;
						}
					} else {
						if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < 3) 
						ctx.movement.stepTowards(Bush.getLocation());
					}
				}
			} else {
					if (ctx.bank.isOpen()) {
						ctx.bank.close();
					} else {
						if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(6, 10))
						ctx.movement.newTilePath(PATH_TO_GRAPES).traverse();
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
			return isFull();
		}

		@Override
		public void execute() {
			if (atBank()) {
				if (ctx.bank.isOpen()) {
					ctx.bank.depositInventory();
					final Timer DepositTimer = new Timer(Random.nextInt(1500, 1800));
					while (DepositTimer.isRunning() && isFull()) {
						sleep(Random.nextInt(100, 200));
					}
				} else {
					ctx.bank.open();
				}
			} else {
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(6, 10)) 
					ctx.movement.newTilePath(PATH_TO_GRAPES).reverse().traverse();
			}
			

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
	
	public boolean didInteract() {
		return ctx.game.getCrosshair() == Crosshair.ACTION;
	}
	
	private boolean isFull() {
		return ctx.backpack.select().count() == 28;
	}

	private boolean atGrapes() {
		return AREA_GRAPES.contains(ctx.players.local().getLocation());
	}

	private boolean atBank() {
		return ctx.bank.isOnScreen() && ctx.players.local().getLocation().distanceTo(ctx.bank.getNearest()) < 4;
	}

	final Color BLACK = new Color(0, 0, 0, 200);
	final Font FONT_ONE = new Font("Comic Sans MS", 0, 13);
	final Font FONT_TWO = new Font("Comic Sans MS", 1, 13);
	final Font FONT_THREE = new Font("Comic Sans MS", 1, 10);
	final NumberFormat NF = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {

		long millis = System.currentTimeMillis() - TIMER_ELAPSED;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setColor(BLACK);
		g.drawRect(5, 5, 150, 70);
		g.fillRect(5, 5, 150, 70);
		g.setFont(FONT_TWO);
		g.setColor(Color.RED);
		g.drawString("rBerries", 58, 18);
		g.setFont(FONT_ONE);
		g.drawString("Timer: " + hours + ":" + minutes + ":" + seconds, 15, 45);
		g.drawString("Looted: " + (BERRIES_GAINED) + "(" + perHour(BERRIES_GAINED) + ")", 15, 65);
		g.setFont(FONT_THREE);
		g.drawString("v0.1", 128, 65);
		drawMouse(g);

	}
	
	private String perHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System.currentTimeMillis() - TIMER_ELAPSED)));
	}
	
	private String formatNumber(int start) {
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

	private void drawMouse(Graphics g) {
		final Point m = ctx.mouse.getLocation();
		g.setColor(ctx.mouse.isPressed() ? Color.GREEN : Color.RED);
		g.drawLine(m.x - 5, m.y + 5, m.x + 5, m.y - 5);
		g.drawLine(m.x - 5, m.y - 5, m.x + 5, m.y + 5);
	}

}
