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
import org.powerbot.script.Filter;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Tile;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.rt6.Component;
import org.powerbot.script.rt6.GameObject;
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.GroundItem;
import org.powerbot.script.rt6.Interactive;
import org.powerbot.script.rt6.Menu;
import org.powerbot.script.rt6.Npc;
import org.powerbot.script.rt6.Game.Crosshair;

@Manifest(name = "rGrapes(Beta)", description = "Loots grapes from the upper level of the cooking guild for money.", properties = "hidden=true")
public class Grapes extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener {
	private static RenderingHints ANTIALIASING = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	
	private static long TIMER_SCRIPT = 0;
	private boolean started = false;
	private static String STATUS = "Starting...";
	private static final int ID_BANKER[] = { 553, 2759 };
	private static final int ID_STAIRS_UP[] = { 24073, 24074, 24075 };
	private static final int ID_STAIRS_DOWN[] = { 24074, 24075 };
	private static final int ID_DOOR = 2712, ID_GRAPE = 1987;
	private static int GRAPES_GAINED, GRAPE_PRICE, PROFIT_GAINED, TRIES;
	private static final Tile TILE_LOOT = new Tile(3144, 3450, 2);
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
	
	@Override
	public void start() {
		ctx.properties.put("login.disable", "true");
		TIMER_SCRIPT = System.currentTimeMillis();
		GRAPE_PRICE = getGuidePrice(ID_GRAPE);
		log.info("G.E. Grape Price : " + GRAPE_PRICE);
		started = true;
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
		breakHandler();
		if (!ctx.game.loggedIn())
			return;
		switch (state()) {
		case CAMERA:
			ctx.camera.pitch(Random.nextInt(55, 65));
			break;
		case BANKING:
			if (atLevelThree()) {
				goDown();
				if (didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return atLevelTwo();
						}
					}, 250, 20);
				}
			} else if (atLevelTwo()) {
					goDown();
					if(didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return atLevelOne();
						}
					}, 250, 20);
				}
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
				final Npc Banker = ctx.npcs.select().id(ID_BANKER).nearest().poll();
				if (Banker.inViewport()) {
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
						STATUS = "Bank open";
						ctx.bank.open();
					}
				} else {
					STATUS = "Walk to bank";
					ctx.movement.newTilePath(PATH_GUILD).reverse().traverse();
					if (Random.nextInt(1, 20) == 10) {
						ctx.camera.turnTo(ctx.bank.nearest());
					}
				}
			}
			break;
		case GRAPES:
			if (atDoor()) {
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
				if (didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return atLevelTwo();
						}
					}, 250, 20);
				}
			} else if (atLevelTwo()) {
				goUp();
				if (didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return atLevelThree();
						}
					}, 250, 20);
				}
			} else if (atLevelThree()) {
				if (ctx.players.local().tile().distanceTo(TILE_LOOT) > 2) {
					STATUS = "Walk to grapes";
					ctx.camera.turnTo(TILE_LOOT);
					ctx.movement.step(ctx.movement.closestOnMap(TILE_LOOT));
					Condition.sleep();
					while (ctx.players.local().inMotion()) {
						Condition.sleep();
					}
				} else {
					final GroundItem Grapes = ctx.groundItems.select().id(ID_GRAPE).nearest().poll();
					if (Grapes.valid()) {
						STATUS = "Take grapes";
						take(Grapes);
					} else {
						STATUS = "Waiting for spawn..";
						antiBan();
					}
				}
			} else {
				if (ctx.bank.opened()) {
					STATUS = "Bank close";
					ctx.input.send("{VK_ESCAPE down}");
					Condition.sleep();
					ctx.input.send("{VK_ESCAPE up}");
				} else {
					STATUS = "Walk to guild";
					ctx.movement.newTilePath(PATH_GUILD).traverse();
				}
			}
			break;
		}
	}

	private State state() {
		if (ctx.camera.pitch() < 55) {
			return State.CAMERA;
		}
		if (ctx.backpack.select().count() == 28) {
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
		final Filter<Menu.Command> filter = new Filter<Menu.Command>() {
			@Override
			public boolean accept(Menu.Command arg0) {
				return arg0.action.equalsIgnoreCase("Take") && arg0.option.equalsIgnoreCase("Grapes");
			}

		};
		if (ctx.menu.click(filter)) {
			if (didInteract()) {
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return ctx.backpack.select().id(ID_GRAPE).count() != count;
					}
				}, 250, 20);
			}
		} else {
			if (TRIES > 2) {
				ctx.camera.turnTo(g);
			}
			ctx.input.move(p);
			TRIES++;
		}
		if (ctx.backpack.select().id(ID_GRAPE).count() == count + 1) {
			TRIES = 0;
			GRAPES_GAINED++;
			return true;
		}
		return false;
	}

	private void openDoor() {
		final int[] DoorBounds = { -216, 232, -876, -108, 148, 204 };
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS_UP).nearest().poll();
		final GameObject Door = ctx.objects.select().id(ID_DOOR).each(Interactive.doSetBounds(DoorBounds)).nearest().poll();
		if (Door.inViewport() && ctx.players.local().tile().distanceTo(Door.tile()) < 5) {
			STATUS = "Open door";
			ctx.camera.turnTo(Stairs);
			Door.interact("Open", "Door");
		} else {
			STATUS = "Walk to door";
			ctx.movement.step(ctx.movement.closestOnMap(Door));
		}
	}

	private void goDown() {
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS_DOWN).nearest().poll();
		if (Stairs.inViewport() && ctx.players.local().tile().distanceTo(Stairs.tile()) < 5) {
			STATUS = "Climb-down";
			Stairs.interact("Climb-down");
		} else {
			STATUS = "Walk to stairs";
			ctx.movement.step(ctx.movement.closestOnMap(Stairs.tile()));
			ctx.camera.turnTo(Stairs);
		}
	}

	private void goUp() {
		final GameObject Stairs = ctx.objects.select().id(ID_STAIRS_UP).nearest().poll();
		if (Stairs.inViewport() && ctx.players.local().tile().distanceTo(Stairs.tile()) < 4) {
			STATUS = "Climb-up";
			Stairs.interact("Climb-up");
		} else {
			STATUS = "Walk to stairs";
			ctx.movement.step(ctx.movement.closestOnMap(Stairs.tile()));
			ctx.camera.turnTo(Stairs);
		}
	}

	private boolean atLevelOne() {
		return AREA_IN_GUILD.contains(ctx.players.local().tile());
	}

	private boolean atLevelTwo() {
		return ctx.game.floor() == 1;
	}

	private boolean atLevelThree() {
		return ctx.game.floor() == 2;
	}

	private boolean atDoor() {
		final GameObject Door = ctx.objects.select().id(ID_DOOR).nearest().poll();
		return Door.inViewport() && ctx.players.local().tile().distanceTo(Door.tile()) < 7 && !atLevelOne() && !atLevelTwo() && !atLevelThree();
	}

	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}
	
	private void logIn() {
		final Component PLAY_NOW_WIDGET = ctx.widgets.component(906, 154);
		if (PLAY_NOW_WIDGET.valid()) {
			PLAY_NOW_WIDGET.click(true);
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return ctx.game.loggedIn();
				}
			}, 250, 20);
		}
	}

	
	private void breakHandler() {
		long millis = System.currentTimeMillis() - TIMER_SCRIPT;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);

		if (started) {
			if (ctx.game.loggedIn()) {
				if (hours == 2 && minutes < 30) {
					ctx.game.logout(true);
					return;
				} else if (hours == 5 && minutes < 30) {
					ctx.game.logout(true);
					return;
				} else if (hours == 8 && minutes < 30) {
					ctx.game.logout(true);
					return;
				} else if (hours == 11 && minutes < 30) {
					ctx.game.logout(true);
					return;
				} else if (hours == 14 && minutes < 30) {
					ctx.game.logout(true);
					return;
				}else if (hours == 17 && minutes < 30){
					ctx.game.logout(true);
					return;
				} else if (hours == 20){
					ctx.controller.stop();
					return;
				}
			} else {
				if (hours == 2 && minutes > 30) {
					logIn();
					return;
				} else if (hours == 5 && minutes > 30) {
					logIn();
					return;
				} else if (hours == 8 && minutes > 30) {
					logIn();
					return;
				} else if (hours == 11 && minutes > 30) {
					logIn();
					return;
				} else if (hours == 14 && minutes > 30){
					logIn();
					return;
				} else if (hours == 17 && minutes > 30){
					logIn();
					return;
				}
			}
		}
		return;
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
			ctx.input.move(Random.nextInt(0, 500), Random.nextInt(0, 500));
			break;
		case 6:
			ctx.camera.pitch(Random.nextInt(55, 65));
			ctx.camera.angle(Random.nextInt(0, 300));
			break;
		}
		return 0;
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
		g.drawString("rGrapeGrabber v0.3", 60, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Grapes Picked: " + NF.format(GRAPES_GAINED) + "(" + PerHour(GRAPES_GAINED) + "/h)", 10, 60);
		g.drawString("Profit: " + NF.format(PROFIT_GAINED) + "(" + PerHour(PROFIT_GAINED) + "/h)", 10, 80);
		g.drawString("Profit ea: " + (GRAPE_PRICE), 10, 100);
		g.drawString("Status: " + (STATUS), 10, 120);
		drawTiles(g);
	}
	
	private void drawTiles(Graphics2D g) {
		Point p = ctx.input.getLocation();
		final GroundItem Grapes = ctx.groundItems.select().id(ID_GRAPE).nearest().poll();
		if (Grapes.valid() && atLevelThree()) {
			TILE_LOOT.matrix(ctx).draw(g);
		}
		g.setColor(Color.MAGENTA);
		g.setStroke(new BasicStroke(2));
		g.fill(new Rectangle(p.x + 1, p.y - 4, 2, 15));
		g.fill(new Rectangle(p.x - 6, p.y + 2, 16, 2));
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

	private static int getGuidePrice(final int id) {
		return GeItem.price(id);
	}
}
