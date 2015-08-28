package rWine;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.concurrent.Callable;

import org.powerbot.script.Area;
import org.powerbot.script.Condition;
import org.powerbot.script.Filter;
import org.powerbot.script.MessageEvent;
import org.powerbot.script.MessageListener;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Tile;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.rt6.Component;
import org.powerbot.script.rt6.GroundItem;
import org.powerbot.script.rt6.Menu;
import org.powerbot.script.rt6.Bank.Amount;
import org.powerbot.script.rt6.Game.Crosshair;

@Manifest(name = "rWine", description = "Loots wine from falador", properties = "hidden=true")
public class Wine extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {
	private static long TIMER_SCRIPT = 0;
	private static RenderingHints ANTIALIASING = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String STATUS = "Starting...";
	private static final int ID_WINE = 245;
	private static int WINE_GAINED, WINE_STORED;
	private static final Tile TILE_LOOT = new Tile(2952, 3474, 0);
	private final Tile[] PATH_BANK = new Tile[] { new Tile(2967, 3403, 0),
			new Tile(2966, 3399, 0), new Tile(2965, 3396, 0),
			new Tile(2965, 3391, 0), new Tile(2964, 3386, 0),
			new Tile(2962, 3382, 0), new Tile(2958, 3381, 0),
			new Tile(2954, 3381, 0), new Tile(2952, 3379, 0),
			new Tile(2949, 3376, 0), new Tile(2945, 3368, 0) };
	private final Tile[] PATH_TEMPLE = new Tile[] { new Tile(2945, 3371, 0),
			new Tile(2945, 3374, 0), new Tile(2950, 3376, 0),
			new Tile(2952, 3378, 0), new Tile(2956, 3381, 0),
			new Tile(2961, 3383, 0), new Tile(2964, 3387, 0),
			new Tile(2964, 3392, 0), new Tile(2965, 3398, 0),
			new Tile(2965, 3402, 0), new Tile(2963, 3408, 0),
			new Tile(2961, 3411, 0), new Tile(2958, 3416, 0),
			new Tile(2956, 3419, 0), new Tile(2954, 3424, 0),
			new Tile(2952, 3428, 0), new Tile(2951, 3433, 0),
			new Tile(2950, 3438, 0), new Tile(2950, 3443, 0),
			new Tile(2948, 3448, 0), new Tile(2948, 3455, 0),
			new Tile(2949, 3460, 0), new Tile(2951, 3465, 0),
			new Tile(2953, 3468, 0), new Tile(2952, 3474, 0)};
	private final Area AREA_TEMPLE = new Area(new Tile(2944, 3482, 0),
			new Tile(2944, 3471, 0), new Tile(2958, 3471, 0), new Tile(2958, 3481, 0));
	
	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
		ctx.properties.put("login.disable", "true");
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
		antiPatternBreak();
		if (!ctx.game.loggedIn())
			return;
		switch (state()) {
		case CAMERA:
			STATUS = "Set pitch";
			ctx.camera.pitch(Random.nextInt(55, 65));
			break;
		case BANKING:
			if (ctx.bank.inViewport()) {
				if (ctx.bank.opened()) {
					STATUS = "Deposit";
					ctx.bank.deposit(ID_WINE, Amount.ALL);
					Condition.sleep();
				} else {
					STATUS = "Open bank";
					ctx.bank.open();
				}
			} else {
				if (AREA_TEMPLE.contains(ctx.players.local().tile())) {
					final Component TELEPORT_WIDGET = ctx.widgets.component(1465, 50);
					final Component VARROCK_WIDGET = ctx.widgets.component(1092, 16);
					if (ctx.players.local().animation() == -1) {
						STATUS = "Teleporting";
						if (VARROCK_WIDGET.valid()) {
							VARROCK_WIDGET.click(true);
							Condition.wait(new Callable<Boolean>() {
								@Override
								public Boolean call() throws Exception {
									return ctx.players.local().animation() != -1;
								}
							}, 325, 20);
							while(ctx.players.local().animation() != -1){
								Condition.sleep(Random.nextInt(1000, 3200));
							}
						} else {
							TELEPORT_WIDGET.click(true);
							Condition.wait(new Callable<Boolean>() {
								@Override
								public Boolean call() throws Exception {
									return VARROCK_WIDGET.valid();
								}
							}, 250, 20);
							Condition.sleep();
						}
					}
				} else {
					STATUS = "Walk to bank";
					ctx.movement.newTilePath(PATH_BANK).traverse();
				}
			}
			break;
		case GRAB:
			final GroundItem Wine = ctx.groundItems.select().id(ID_WINE).nearest().poll();
				if (AREA_TEMPLE.contains(ctx.players.local().tile())) {
					if(ctx.players.local().tile().distanceTo(TILE_LOOT) > 0){
						STATUS = "Walk to tile";
						TILE_LOOT.matrix(ctx).interact("Walk here");
						Condition.sleep();
						ctx.camera.angle(Random.nextInt(0, 350));
						while(ctx.players.local().inMotion()){
							Condition.sleep();
						}
					}else{
					if (!ctx.client().isSpellSelected()) {
						STATUS = "Set spell";
						Condition.sleep();
						ctx.input.send("2");
						Condition.sleep();
					} else {
						if (Wine.valid()) {
							STATUS = "Take wine";
							take(Wine);
						} else {
							STATUS = "Waiting";
							antiBan();
							if(Random.nextInt(1, 100) == 50){
								Condition.sleep();
							}
						}
					}
				}
			} else {
				if (ctx.bank.opened()) {
					STATUS = "Close bank";
					WINE_STORED = ctx.bank.select().id(ID_WINE).count(true);
						ctx.bank.close();
						Condition.sleep();
				} else {
					STATUS = "Walk to temple";
					ctx.movement.newTilePath(PATH_TEMPLE).traverse();
				}
			}
			break;
		}
	}
	
	private State state() {
		
		if(ctx.camera.pitch() < 52){
			return State.CAMERA;
		}

		if (ctx.backpack.select().count() == 28) {
			return State.BANKING;
		}

		return State.GRAB;
	}

	private enum State {
		CAMERA, BANKING, GRAB
	}
	
	private boolean take(GroundItem g) {
		final int count = ctx.backpack.select().id(ID_WINE).count();
		final Point p = g.tile().matrix(ctx).point(0.5, 0.5, -417);
		final Filter<Menu.Command> filter = new Filter<Menu.Command>() {
			@Override
			public boolean accept(Menu.Command arg0) {
				return arg0.action.equalsIgnoreCase("Cast") && arg0.option.equalsIgnoreCase("Telekinetic Grab -> Wine of Zamorak");
			}

		};
		if (ctx.menu.click(filter)) {
			if (didInteract()) {
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return ctx.backpack.select().id(ID_WINE).count() != count;
					}
				}, 250, 20);
			}
		} else {
			ctx.input.move(p);
		}
		if (ctx.backpack.select().id(ID_WINE).count() == count + 1) {
			WINE_GAINED++;
			return true;
		}
		return false;
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
			}, 300, 20);
		}
	}

	private void antiPatternBreak() {
		long millis = System.currentTimeMillis() - TIMER_SCRIPT;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		if (ctx.game.loggedIn()) {
			if (hours == 1 && minutes < 4 || hours > 1 && minutes < 4) {
				ctx.game.logout(true);
			}
		} else {
			if (minutes > 4) {
				logIn();
			}
		}
	}
	
	private int antiBan() {
		int antiban = Random.nextInt(1, 3500);
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
		case 5:
			final Component REST_WIDGET = ctx.widgets.component(1465, 40);
			if(ctx.players.local().animation() == -1){
			  REST_WIDGET.interact("Rest");
			  Condition.sleep();
			}
			break;
		}
		return 0;
	}
	
	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();
		if (message.contains("You can only attack") || message.contains("You can't") || message.contains("Nothing")) {
			ctx.camera.angle(Random.nextInt(0, 180));
		}
		
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
		
		g.setRenderingHints(ANTIALIASING);
		g.setColor(BLACK);
		g.fillRect(5, 5, 190, 145);
		g.setColor(Color.MAGENTA);
		g.drawRect(5, 5, 190, 145);
		g.setFont(FONT);
		g.drawString("rWine", 75, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Gained: " + NF.format(WINE_GAINED) + "(" + PerHour(WINE_GAINED) + "/h)", 10, 60);
		g.drawString("Stored: " + NF.format(WINE_STORED), 10, 80);
		g.drawString("Status: " + (STATUS), 10, 100);
		drawMouse(g);
	}
	
	private void drawMouse(Graphics2D g) {
		int mouseY = (int) ctx.input.getLocation().getY();
		int mouseX = (int) ctx.input.getLocation().getX();
		g.setColor(Color.MAGENTA);
		g.drawLine(mouseX - 5, mouseY + 5, mouseX + 5, mouseY - 5);
		g.drawLine(mouseX + 5, mouseY + 5, mouseX - 5, mouseY - 5);
		while (!mousePath.isEmpty() && mousePath.peek().isUp()) mousePath.remove();
		Point clientCursor = ctx.input.getLocation();
		MousePathPoint mpp = new MousePathPoint(clientCursor.x, clientCursor.y, 600);
		if (mousePath.isEmpty() || !mousePath.getLast().equals(mpp)) mousePath.add(mpp);
		MousePathPoint lastPoint = null;
		for (MousePathPoint a : mousePath) {
			if (lastPoint != null) {
				g.drawLine(a.x, a.y, lastPoint.x, lastPoint.y);
			}
			lastPoint = a;
		}
	}
	
	private final LinkedList<MousePathPoint> mousePath = new LinkedList<MousePathPoint>();
	@SuppressWarnings("serial")
	private class MousePathPoint extends Point {
		private long finishTime;
		public MousePathPoint(int x, int y, int lastingTime) {
			super(x, y);
			finishTime = System.currentTimeMillis() + lastingTime;
		}
		public boolean isUp() {
			return System.currentTimeMillis() > finishTime;
		}
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