package rBeer;

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

import org.powerbot.script.Condition;
import org.powerbot.script.MessageEvent;
import org.powerbot.script.MessageListener;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.Tile;
import org.powerbot.script.rt6.GameObject;
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.Interactive;
import org.powerbot.script.rt6.Npc;
import org.powerbot.script.rt6.Game.Crosshair;

@Manifest(name = "rBeer", description = "Buys beer in falador for money.", properties = "hidden=true")
public class Beer extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {
	private static RenderingHints ANTIALIASING = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String STATUS = "Starting...";
	private static long SCRIPT_TIMER = 0;
	
	private static int BEER_BOUGHT, BEER_PRICE, BEER_COUNT;
	private static final int[] FAIL_SAFE_STAIRS_IDS = { 26149, 26151 };
	private static final int BEER_ID = 1917, MEGAN_ID = 661, STAIRS_UP_ID = 26144, STAIRS_DOWN_ID = 26148;
	
	private static final Tile STAIRS_TILE_UP = new Tile(3038, 3382, 0);
	private static final Tile STAIRS_TILE_DOWN = new Tile(3039, 3383, 1);
	
	private static final Tile[] PATH_TO_MEGAN = { 
		    new Tile(3012, 3355, 0), new Tile(3020, 3363, 0), 
			new Tile(3027, 3367, 0), new Tile(3034, 3368, 0), 
			new Tile(3040, 3378, 0), new Tile(3038, 3382, 0)};
	
	@Override
	public void start() {
		SCRIPT_TIMER = System.currentTimeMillis();
		STATUS = "Get prices..";
		BEER_PRICE = getGuidePrice(BEER_ID);
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
			STATUS = "Setting camera";
			ctx.camera.pitch(Random.nextInt(52, 60));
			break;
		case STOP:
				ctx.controller.stop();
			break;
		case FAILSAFE:
			final GameObject FailsafeStairs = ctx.objects.select().id(FAIL_SAFE_STAIRS_IDS).nearest().poll();
			if (FailsafeStairs.inViewport()) {
				ctx.camera.angle(Random.nextInt(0, 300));
				FailsafeStairs.interact("Climb-down", "Staircase");
				ctx.input.move(FailsafeStairs.centerPoint());
				if (didInteract()) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return atFloor();
						}
					}, 250, 20);
				}
			} else {
				ctx.camera.turnTo(FailsafeStairs);
			}
			break;
		case BUY:
			final GameObject StairsUp = ctx.objects.select().id(STAIRS_UP_ID).nearest().poll();
			if (ctx.players.local().tile().distanceTo(StairsUp) < 8) {
				if (StairsUp.inViewport() && ctx.players.local().tile().distanceTo(StairsUp.tile()) < 8) {
					STATUS = "Climb-up";
					StairsUp.interact("Climb-up");
					if(didInteract()){
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return atFloor();
						}
					}, 250, 20);
					}
				} else {
					STATUS = "Walk to stairs";
					ctx.movement.step(ctx.movement.closestOnMap(STAIRS_TILE_UP));
					ctx.camera.turnTo(StairsUp);
				}
			} else {
				if (atFloor()) {
					if (ctx.chat.queryContinue()) {
						STATUS = "Select Continue";
						ctx.chat.clickContinue(true);
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.widgets.component(1188, 5).valid()
								    || ctx.widgets.component(1184, 10).valid()
								    || ctx.widgets.component(1189, 6).valid();
							}
						}, 250, 20);
					}else if (ctx.widgets.component(1188, 5).valid()) {
						STATUS = "Select Option";
						ctx.input.send("1");
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.chat.queryContinue();
							}
						}, 250, 20);
					}else{
						final int[] MeganBounds = { -104, 88, -728, -92, -124, 120 };
						final Npc Megan = ctx.npcs.select().id(MEGAN_ID).each(Interactive.doSetBounds(MeganBounds)).nearest().poll();
							if (Megan.inViewport() && ctx.players.local().tile().distanceTo(Megan) < 8) {
								STATUS = "Talk-to Megan";
								Megan.interact("Talk-to", "Megan");
								if (didInteract()) {
									Condition.wait(new Callable<Boolean>() {
										@Override
										public Boolean call() throws Exception {
											return ctx.chat.queryContinue();
										}
									}, 250, 20);
								}
							} else {
								ctx.movement.step(ctx.movement.closestOnMap(Megan));
								ctx.camera.turnTo(Megan);
						}
					}
				} else {
					if (ctx.bank.opened()) {
						BEER_COUNT = ctx.bank.select().id(BEER_ID).count(true);
						STATUS = "Close bank";
						ctx.bank.close();
					} else {
						STATUS = "Path to stairs";
						ctx.movement.newTilePath(PATH_TO_MEGAN).traverse();
					}

				}
			}
			break;
		case BANKING:
			if (ctx.bank.inViewport()) {
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
					STATUS = "Open bank";
					ctx.bank.open();
				}
			} else {
				if (atFloor()) {
					final int[] StairBounds = {116, 500, 56, 0, -448, -88};
					final GameObject StairsDown = ctx.objects.select().id(STAIRS_DOWN_ID).each(Interactive.doSetBounds(StairBounds)).nearest().poll();
					if (StairsDown.inViewport() && ctx.players.local().tile().distanceTo(StairsDown.tile()) < 8) {
						STATUS = "Climb-down";
						StairsDown.interact("Climb-down");
						if(didInteract()){
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return !atFloor();
							}
						}, 250, 20);
						}
					} else {
						STATUS = "Walk to stairs";
						ctx.movement.step(ctx.movement.closestOnMap(STAIRS_TILE_DOWN));
						ctx.camera.turnTo(StairsDown);
					}
				} else {
					STATUS = "Path to bank";
					ctx.movement.newTilePath(PATH_TO_MEGAN).reverse().traverse();
				}
			}
			break;
		}
	}
	
	private State state() {
		
		if(ctx.camera.pitch() < 52){
			return State.CAMERA;
		}
		
		if(ctx.backpack.moneyPouchCount() < 2){
			return State.STOP;
		}
		
		if(ctx.game.floor() == 2){
			return State.FAILSAFE;
		}
		
		if(ctx.backpack.select().count() != 28){
			return State.BUY;
		}

		return State.BANKING;
	}

	private enum State {
		CAMERA, STOP, FAILSAFE, BUY, BANKING
	}
	
	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}

	private boolean atFloor() {
		return ctx.game.floor() == 1;
	}
	
	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();
		if (message.contains("2 coins have")) {
			BEER_BOUGHT++;
		}
	}

	final static Color BLACK = new Color(25, 0, 0, 200);
	final static Font FONT = new Font("Comic Sans MS", 1, 12);
	final Font FONT_TWO = new Font("Comic Sans MS", 1, 9);
	final static NumberFormat NF = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {

		final Graphics2D g = (Graphics2D) g1;

		long millis = System.currentTimeMillis() - SCRIPT_TIMER;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;
		
		if(hours == 2){
			ctx.controller.stop();
			log.info("Timer reached stopping script....");
		}

		g.setRenderingHints(ANTIALIASING);
		g.setColor(BLACK);
		g.fillRect(5, 5, 180, 125);
		g.setColor(Color.ORANGE);
		g.drawRect(5, 5, 180, 125);
		g.setFont(FONT);
		g.drawString("rBeer", 80, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Beer Bought: " + NF.format(BEER_BOUGHT) + "(" + PerHour(BEER_BOUGHT) + "/h)", 10, 60);
		g.drawString("Beer Stored: " + NF.format(BEER_COUNT), 10, 80);
		g.drawString("Profit: " + NF.format(profit()) + "(" + PerHour(profit()) + "/h)", 13, 100);
		g.drawString("Status: " + (STATUS), 10, 120);
		g.setFont(FONT_TWO);
		g.setColor(Color.ORANGE);
		g.drawString("v0.02", 160, 145);
		drawMouse(g);
	}
	
	private int profit(){
		return BEER_BOUGHT * BEER_PRICE;
	}

	public String PerHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System.currentTimeMillis() - SCRIPT_TIMER)));
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

	public void drawMouse(Graphics g) {
		int mouseY = (int) ctx.input.getLocation().getY();
		int mouseX = (int) ctx.input.getLocation().getX();
		g.drawLine(mouseX - 5, mouseY + 5, mouseX + 5, mouseY - 5);
		g.drawLine(mouseX + 5, mouseY + 5, mouseX - 5, mouseY - 5);

		while (!mousePath.isEmpty() && mousePath.peek().isUp()) mousePath.remove();
		Point clientCursor = ctx.input.getLocation();
		MousePathPoint mpp = new MousePathPoint(clientCursor.x, clientCursor.y, 600); // 1000 = lasting time/MS
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
	
	private static int getGuidePrice(final int id) {
		return GeItem.price(id);
	}
}