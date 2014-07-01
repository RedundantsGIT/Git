package rGrapes;

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
import java.util.concurrent.Callable;

import org.powerbot.script.Area;
import org.powerbot.script.Condition;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Tile;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.rt6.GameObject;
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.GroundItem;
import org.powerbot.script.rt6.Interactive;
import org.powerbot.script.rt6.Npc;
import org.powerbot.script.rt6.Game.Crosshair;

@Manifest(name = "rGrapes", description = "Loots grapes from the upper level of the cooking guild for money.", properties = "hidden=true")
public class rGrapes extends
		PollingScript<org.powerbot.script.rt6.ClientContext> implements
		PaintListener {
	private static long TIMER_SCRIPT = 0;
	private static String STATUS = "Starting...";
	private static RenderingHints ANTIALIASING = new RenderingHints(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	private static final int ID_BANKER[] = { 553, 2759 };
	private static final int ID_STAIRS1[] = { 24073, 24074, 24075 };
	private static final int ID_STAIRS2[] = { 24074, 24075 };
	private static final Tile TILE_LOOT = new Tile(3144, 3450, 2);
	private static int GRAPES_GAINED, GRAPE_PRICE, PROFIT_GAINED, TRIES;
	private static final int ID_SHOOT1 = 24068, ID_SHOOT2 = 24067,
			ID_DOOR = 2712, ID_GRAPE = 1987;
	private static final Area AREA_IN_GUILD = new Area(new Tile[] {
			new Tile(3147, 3446, 0), new Tile(3145, 3444, 0),
			new Tile(3141, 3444, 0), new Tile(3138, 3448, 0),
			new Tile(3140, 3453, 0), new Tile(3148, 3451) });
	private static final Tile[] PATH_GUILD = { new Tile(3182, 3443, 0),
			new Tile(3183, 3450, 0), new Tile(3176, 3450, 0),
			new Tile(3172, 3450, 0), new Tile(3166, 3451, 0),
			new Tile(3160, 3450, 0), new Tile(3155, 3449, 0),
			new Tile(3152, 3446, 0), new Tile(3148, 3443, 0),
			new Tile(3143, 3443, 0) };

	private static final Tile LOOT_TILE = new Tile(3143, 3450, 2);

	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
		GRAPE_PRICE = getGuidePrice(ID_GRAPE);
		ctx.properties.setProperty("bank.antipattern", "disable");
		log.info("G.E. Grape Price : " + GRAPE_PRICE);
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

	@Override
	public void poll() {

		if (!ctx.game.loggedIn())
			return;

		switch (state()) {
		case CAMERA:
			ctx.camera.pitch(Random.nextInt(45, 50));
			break;
		case BANKING:
			if (atLevelThree()) {
				goDown();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return atLevelTwo();
					}
				}, 250, 20);
			} else if (atLevelTwo()) {
				goDown();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return atLevelOne();
					}
				}, 250, 20);
			} else if (atLevelOne()) {
				openDoor();
				if (didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !atLevelOne();
						}
					}, 250, 20);
				}
			} else {
				if (bankerIsOnScreen()) {
					if (ctx.bank.opened()) {
						STATUS = "Deposit backpack";
						ctx.bank.depositInventory();
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.backpack.select().count() != 28;
							}
						}, 250, 20);
					} else {
						ctx.bank.open();
					}
				} else {
					if (!ctx.players.local().inMotion()
							|| ctx.players.local().tile()
									.distanceTo(ctx.movement.destination()) < Random
									.nextInt(5, 8)) {
						STATUS = "Walk to bank";
						ctx.movement.newTilePath(PATH_GUILD).reverse()
								.traverse();
						ctx.camera.turnTo(ctx.bank.nearest());
					}
				}

			}

			break;
		case GRAPES:
			if (atGuildEntranceDoor()) {
				STATUS = "Open door";
				openDoor();
				if (didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return atLevelOne();
						}
					}, 250, 20);

				}
			} else if (atLevelOne()) {
				goUp();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return atLevelTwo();
					}
				}, 250, 20);

			} else if (atLevelTwo()) {
				goUp();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return atLevelThree();
					}
				}, 250, 20);
			} else if (atLevelThree()) {
				if (ctx.players.local().tile().distanceTo(TILE_LOOT) > 2) {
					STATUS = "Walk to grapes";
					if (LOOT_TILE.tile().matrix(ctx).inViewport()
							&& Random.nextInt(1, 10) == 5) {
						LOOT_TILE.tile().matrix(ctx).interact("Walk here");
						Condition.sleep(Random.nextInt(1000, 1500));
						while (ctx.players.local().inMotion())
							;
					} else
						ctx.movement.step(ctx.movement.closestOnMap(TILE_LOOT));
					ctx.camera.turnTo(TILE_LOOT);
					while (ctx.players.local().inMotion())
						;
				} else {
					final GroundItem Grapes = ctx.groundItems.select()
							.id(ID_GRAPE).nearest().poll();
					if (Grapes.inViewport()) {
						STATUS = "Take grapes";
						take(Grapes);
					} else {
						STATUS = "Waiting for spawn..";
						antiBan();
					}
				}
			} else {
				if (!ctx.players.local().inMotion()
						|| ctx.players.local().tile()
								.distanceTo(ctx.movement.destination()) < Random
								.nextInt(5, 8)) {
					if (ctx.bank.opened()) {
						STATUS = "Bank close";
						ctx.bank.close();
					} else {
						STATUS = "Walk to guild";
						ctx.movement.newTilePath(PATH_GUILD).traverse();
					}
				}
			}

			break;

		}

	}

	private State state() {
		if (ctx.camera.pitch() < 40) {
			return State.CAMERA;
		}
		if (isFull()) {
			return State.BANKING;
		}

		return State.GRAPES;
	}

	private enum State {
		CAMERA, BANKING, GRAPES
	}

	private boolean take(GroundItem g) {
		final int count = ctx.backpack.select().id(ID_GRAPE).count();
		final Point p = g.tile().matrix(ctx).point(0.5, 0.5, -417);
		if (ctx.input.click(p, true)) {
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return ctx.backpack.select().id(ID_GRAPE).count() == count + 1;
				}
			}, 250, 20);
		} else {
			if (TRIES > 2) {
				ctx.camera.turnTo(g.tile());
			}
			TRIES++;
		}
		if (ctx.backpack.select().id(ID_GRAPE).count() == count + 1) {
			TRIES = 0;
			GRAPES_GAINED++;
			return true;
		}

		return false;
	}

	private boolean openDoor() {
		final int[] doorBounds = { -200, 150, -800, -300, 0, 0 };
		final GameObject Door = ctx.objects.select().id(ID_DOOR)
				.each(Interactive.doSetBounds(doorBounds)).nearest().poll();
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS1).nearest()
				.poll();
		if (ctx.players.local().tile().distanceTo(Door.tile()) < 5) {
			if (Door.inViewport()) {
				ctx.camera.turnTo(Stairs.tile());
				Door.interact("Open", "Door");
				while (ctx.players.local().inMotion())
					;
			}
		} else {
			ctx.movement.step(ctx.movement.closestOnMap(Door.tile()));
		}

		return true;
	}

	private boolean goDown() {
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS2).nearest()
				.poll();
		if (Stairs.inViewport()) {
			ctx.camera.turnTo(Stairs.tile());
			Stairs.interact("Climb-down");
			while (ctx.players.local().inMotion())
				;
		} else {
			ctx.movement.step(ctx.movement.closestOnMap(Stairs.tile()));
		}
		return true;
	}

	private boolean goUp() {
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS1).nearest()
				.poll();
		if (Stairs.inViewport()) {
			ctx.camera.turnTo(Stairs.tile());
			Stairs.interact("Climb-up");
			while (ctx.players.local().inMotion())
				;
		} else {
			ctx.movement.step(ctx.movement.closestOnMap(Stairs.tile()));
		}

		return true;
	}

	public boolean bankerIsOnScreen() {
		final Npc Banker = ctx.npcs.select().id(ID_BANKER).nearest().poll();
		return Banker.inViewport();
	}

	private boolean atLevelTwo() {
		final GameObject Shoot = ctx.objects.select().id(ID_SHOOT1).nearest()
				.poll();
		return Shoot.valid();
	}

	private boolean atLevelThree() {
		final GameObject Shoot = ctx.objects.select().id(ID_SHOOT2).nearest()
				.poll();
		return Shoot.valid();
	}

	private boolean atLevelOne() {
		return AREA_IN_GUILD.contains(ctx.players.local().tile());
	}

	private boolean atGuildEntranceDoor() {
		final GameObject Door = ctx.objects.select().id(ID_DOOR).nearest()
				.poll();
		return Door.inViewport()
				&& ctx.players.local().tile().distanceTo(Door.tile()) < 7
				&& !atLevelOne() && !atLevelTwo() && !atLevelThree();
	}

	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}

	private boolean isFull() {
		return ctx.backpack.select().count() == 28;
	}

	private void antiBan() {
		int antiban = Random.nextInt(1, 250);
		switch (antiban) {
		case 1:
			ctx.camera.angle(Random.nextInt(21, 40));
			break;
		case 2:
			ctx.camera.angle(Random.nextInt(25, 75));
			break;
		case 3:
			ctx.camera.angle(Random.nextInt(1, 200));
			break;
		case 4:
			ctx.camera.angle(Random.nextInt(1, 300));
			break;
		case 5:
			ctx.input.move(Random.nextInt(0, (int) (ctx.game.dimensions().getWidth() - 1)), 0);
			break;
		case 6:
			ctx.input.hop(Random.nextInt(-10, (int) (ctx.game.dimensions().getWidth() + 10)), (int) (ctx.game.dimensions().getHeight() + Random.nextInt(10, 100)));
			break;
		}
		Condition.sleep(Random.nextInt(500, 1500));
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
		PROFIT_GAINED = GRAPES_GAINED * GRAPE_PRICE;

		g.setRenderingHints(ANTIALIASING);
		g.setColor(BLACK);
		g.fillRect(5, 5, 190, 125);
		g.setColor(Color.MAGENTA);
		g.drawRect(5, 5, 190, 125);
		g.setFont(FONT);
		g.drawString("rGrapeGrabber", 60, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10,
				40);
		g.drawString("Grapes Picked: " + NF.format(GRAPES_GAINED) + "("
				+ PerHour(GRAPES_GAINED) + "/h)", 10, 60);
		g.drawString("Profit: " + NF.format(PROFIT_GAINED) + "("
				+ PerHour(PROFIT_GAINED) + "/h)", 10, 80);
		g.drawString("Profit ea: " + (GRAPE_PRICE), 10, 100);
		g.drawString("Status: " + (STATUS), 10, 120);
		drawMouse(g);
	}

	public String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - TIMER_SCRIPT)));
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
		g.setColor(Color.MAGENTA);
		g.setStroke(new BasicStroke(2));
		g.fill(new Rectangle(p.x + 1, p.y - 4, 2, 15));
		g.fill(new Rectangle(p.x - 6, p.y + 2, 16, 2));
	}

	private static int getGuidePrice(final int id) {
		return GeItem.price(id);
	}

}
