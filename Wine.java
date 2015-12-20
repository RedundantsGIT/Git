package rWine;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.Callable;

import org.powerbot.script.Area;
import org.powerbot.script.Condition;
import org.powerbot.script.MessageEvent;
import org.powerbot.script.MessageListener;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Tile;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.rt6.Component;
import org.powerbot.script.rt6.GroundItem;
import org.powerbot.script.rt6.Bank.Amount;
import org.powerbot.script.rt6.Game.Crosshair;

@Manifest(name = "rWine", description = "Loots wine from falador", properties = "hidden=true")
public class Wine extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {
	private static long TIMER_SCRIPT = 0;
	private static String STATUS = "Starting...";
	private static int WINE_GAINED, WINE_STORED, TRIES;
	private static final int ID_WINE = 245, ID_LAW = 563;
	static final Tile BANK_TILE = new Tile(2946, 3368, 0), LOOT_TILE = new Tile(2952, 3474, 0), HOVER_TILE = new Tile(2952, 3473, 0);
	final Component LOBBY_WIDGET = ctx.widgets.component(1433, 9), FALADOR_WIDGET = ctx.widgets.component(1092, 16);
	private final Area AREA_TEMPLE = new Area(new Tile(2944, 3482, 0),
			new Tile(2944, 3471, 0), new Tile(2958, 3471, 0), new Tile(2958,3481, 0));
	private final Tile[] PATH_TEMPLE = new Tile[] { new Tile(2945, 3371, 0),
			new Tile(2945, 3374, 0), new Tile(2950, 3376, 0), new Tile(2952, 3378, 0), new Tile(2956, 3381, 0),
			new Tile(2961, 3383, 0), new Tile(2964, 3387, 0), new Tile(2964, 3392, 0), new Tile(2965, 3398, 0),
			new Tile(2965, 3402, 0), new Tile(2963, 3408, 0), new Tile(2961, 3411, 0), new Tile(2958, 3416, 0),
			new Tile(2956, 3419, 0), new Tile(2954, 3424, 0), new Tile(2952, 3428, 0), new Tile(2951, 3433, 0),
			new Tile(2950, 3438, 0), new Tile(2950, 3443, 0), new Tile(2948, 3448, 0), new Tile(2948, 3455, 0),
			new Tile(2949, 3460, 0), new Tile(2951, 3465, 0), new Tile(2953, 3468, 0), new Tile(2952, 3474, 0) };
	
	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
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
			STATUS = "Set pitch";
			ctx.camera.pitch(Random.nextInt(72, 82));
			break;
		case LOGOUT:
			if (LOBBY_WIDGET.visible()) {
				LOBBY_WIDGET.click(true);
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !ctx.game.loggedIn();
					}
				}, 280, 20);
				if (!ctx.game.loggedIn()) {
					TRIES = 0;
				}
			} else {
				close();
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return LOBBY_WIDGET.visible();
					}
				}, 250, 20);
			}
			break;
		case BANKING:
			if (atTemple()) {
				if (ctx.players.local().animation() == -1) {
					STATUS = "Teleporting";
					if (FALADOR_WIDGET.valid()) {
						ctx.input.send("f");
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.players.local().animation() != -1;
							}
						}, 300, 20);
						while (ctx.players.local().animation() != -1) {
							Condition.sleep(Random.nextInt(1200, 2200));
						}
					} else {
						ctx.input.send("1");
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return FALADOR_WIDGET.valid();
							}
						}, 250, 16);
					}
				}
			} else {
				if (ctx.bank.inViewport()) {
					if (ctx.bank.opened()) {
						STATUS = "Deposit";
						if (!ctx.backpack.select().id(ID_WINE).isEmpty()) {
							ctx.bank.depositInventory();
						}
					} else {
						STATUS = "Open bank";
						ctx.bank.open();
					}
				} else {
					STATUS = "Walk to bank";
					if (!ctx.players.local().inMotion() || ctx.players.local().tile().distanceTo(ctx.movement.destination()) < Random.nextInt(5, 8)) {
						ctx.movement.step(ctx.movement.closestOnMap(BANK_TILE));
					}
				}
			}
			break;
		case GRAB:
			if (atTemple()) {
				if (LOOT_TILE.matrix(ctx).inViewport() && ctx.players.local().tile().distanceTo(LOOT_TILE) > 0) {
					delay();
					LOOT_TILE.matrix(ctx).click(true);
					Condition.sleep(Random.nextInt(2300, 3500));
				} else {
					if (!ctx.client().isSpellSelected() && ctx.players.local().tile().distanceTo(LOOT_TILE) < 1) {
						STATUS = "Set spell";
						ctx.input.send("2");
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.client().isSpellSelected();
							}
						}, 250, 10);
					} else {
						if (!ctx.groundItems.select().id(ID_WINE).isEmpty()) {
							STATUS = "Take wine";
							take(ctx.groundItems.id(ID_WINE).poll());
						} else {
							int rand = Random.nextInt(97, 106);
							if (ctx.input.getLocation().distance(HOVER_TILE.matrix(ctx).point(rand)) > 10) {
								STATUS = "Hover";
								ctx.camera.angle(Random.nextInt(69, 92));
	                            delay();
								ctx.input.move(HOVER_TILE.matrix(ctx).point(rand));
							} else {
								STATUS = "Waiting";
								antiPattern();
							}
						}
					}
				}
			} else {
				if (ctx.bank.opened()) {
					if(ctx.bank.select().id(ID_LAW).isEmpty()){
					STATUS = "Ran out of Law Runes, stopping script.";
					ctx.controller.stop();
					}
					if (ctx.backpack.select().id(ID_LAW).isEmpty() && !ctx.bank.select().id(ID_LAW).isEmpty()) {
						STATUS = "Withdraw Law";
						ctx.bank.withdraw(ID_LAW, Amount.ALL_BUT_ONE);
					} else {
						STATUS = "Close bank";
						WINE_STORED = ctx.bank.select().id(ID_WINE).count(true);
						ctx.bank.close();
					}
				} else {
					STATUS = "Walk to temple";
					ctx.movement.newTilePath(PATH_TEMPLE).traverse();
				}
			}
			break;
		}
	}

	private State state() {

		if (ctx.camera.pitch() < 69) {
			return State.CAMERA;
		}

		if (TRIES > 2) {
			return State.LOGOUT;
		}

		if (ctx.backpack.select().count() == 28) {
			return State.BANKING;
		}

		return State.GRAB;
	}

	private enum State {
		CAMERA, LOGOUT, BANKING, GRAB
	}

	private boolean atTemple() {
		return AREA_TEMPLE.contains(ctx.players.local().tile());
	}

	private boolean take(GroundItem g) {
		final int count = ctx.backpack.select().id(ID_WINE).count();
		final Point p = g.tile().matrix(ctx).point(0.5, 0.5, -417);
			if (ctx.input.click(p, true)) {
				if (didInteract()) {
					TRIES++;
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.backpack.select().id(ID_WINE).count() != count;
					}
				}, 250, 12);
			}
		}
		if (ctx.backpack.select().id(ID_WINE).count() == count + 1) {
			WINE_GAINED++;
			Condition.sleep(Random.nextInt(25, 500));
			return true;
		}
		return false;
	}
	
	private void close(){
		ctx.input.send("{VK_ESCAPE down}");
		delay();
		ctx.input.send("{VK_ESCAPE up}");
		delay();
	}
	private int delay(){
		return Condition.sleep(Random.nextInt(25, 80));
	}

	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}

	private int antiPattern() {
		int antiban = Random.nextInt(1, 3600);
		switch (antiban) {
		case 1:
			ctx.camera.angle(Random.nextInt(21, 40));
			break;
		case 2:
			ctx.camera.angle(Random.nextInt(0, 325));
			break;
		case 3:
			ctx.input.move(Random.nextInt(0, 500), Random.nextInt(0, 500));
			break;
		}
		return 0;
	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();
		if (message.contains("You do not")) {
			ctx.controller.stop();
		}
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
		g.setColor(BLACK);
		g.fillRect(5, 5, 190, 105);
		g.setColor(Color.RED);
		g.drawRect(5, 5, 190, 105);
		g.setFont(FONT);
		g.drawString("rWine", 75, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Gained: " + NF.format(WINE_GAINED) + "(" + PerHour(WINE_GAINED) + "/h)", 10, 60);
		g.drawString("Stored: " + NF.format(WINE_STORED), 10, 80);
		g.drawString("Status: " + (STATUS), 10, 100);
		g.drawString("v0.1", 165, 100);
		drawMouse(g);
	}
	
	/*public void drawMouse(Graphics2D g) {
		Point p = ctx.input.getLocation();
		g.setColor(Color.RED);
		g.fill(new Rectangle(p.x + 1, p.y - 4, 2, 15));
		g.fill(new Rectangle(p.x - 6, p.y + 2, 16, 2));
	}*/
	
	Point p;

	private void drawMouse(Graphics g1) {
		Color[] gradient = new Color[] { new Color(255, 0, 0), new Color(255, 0, 255), new Color(0, 0, 255),
				new Color(0, 255, 255), new Color(0, 255, 0), new Color(255, 255, 0), new Color(255, 0, 0) };
		Color outerCircle = gradient[0];
		g1.setColor(gradient[0]);
		int circleRadius = 7;
		int circleDiameter = circleRadius * 2;
		g1.drawLine(p.x + circleRadius, p.y, p.x + 2000, p.y);
		g1.drawLine(p.x - 2000, p.y, p.x - circleRadius, p.y);
		// Vertical
		g1.drawLine(p.x, p.y + circleRadius, p.x, p.y + 2000);
		g1.drawLine(p.x, p.y - 2000, p.x, p.y - circleRadius);
		for (int r = gradient.length - 1; r > 0; r--) {
			int steps = 200 / ((gradient.length - 1) * 2);
			for (int i = steps; i > 0; i--) {
				float ratio = (float) i / (float) steps;
				int red = (int) (gradient[r].getRed() * ratio + gradient[r - 1].getRed() * (1 - ratio));
				int green = (int) (gradient[r].getGreen() * ratio + gradient[r - 1].getGreen() * (1 - ratio));
				int blue = (int) (gradient[r].getBlue() * ratio + gradient[r - 1].getBlue() * (1 - ratio));
				Color stepColor = new Color(red, green, blue);
				g1.setColor(stepColor);
				// Horizontal
				g1.drawLine(p.x + circleRadius, p.y, p.x + ((i * 5) + (100 * r)), p.y);
				g1.drawLine(p.x - ((i * 5) + (100 * r)), p.y, p.x - circleRadius, p.y);
				// Vertical
				g1.drawLine(p.x, p.y + circleRadius, p.x, p.y + ((i * 5) + (100 * r)));
				g1.drawLine(p.x, p.y - ((i * 5) + (100 * r)), p.x, p.y - circleRadius);
			}
		}
		g1.setColor(outerCircle);
		final long mpt = System.currentTimeMillis() - ctx.input.getPressWhen();
		if (ctx.input.getPressWhen() == -1 || mpt >= 200) {
			g1.drawOval(p.x - circleRadius / 3, p.y - circleRadius / 3, circleDiameter / 3, circleDiameter / 3);
		}
		if (mpt < 200) {
			g1.drawLine(p.x - circleRadius, p.y + circleRadius, p.x + circleRadius, p.y - circleRadius);
			g1.drawLine(p.x - circleRadius, p.y - circleRadius, p.x + circleRadius, p.y + circleRadius);
		}
		g1.setColor(outerCircle);
		g1.drawOval(p.x - circleRadius, p.y - circleRadius, circleDiameter, circleDiameter);
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
}