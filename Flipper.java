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
import java.util.concurrent.Callable;

import org.powerbot.script.Condition;
import org.powerbot.script.MessageEvent;
import org.powerbot.script.MessageListener;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Random;
import org.powerbot.script.Script.Manifest;
import org.powerbot.script.Tile;
import org.powerbot.script.rt6.Component;
import org.powerbot.script.rt6.Game.Crosshair;
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.Npc;

@Manifest(name = "rFurFlipper", description = "Buys fur from Baraek in Varrock for money", properties = "topic=1135335")
public class Flipper extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {
	private static RenderingHints ANTIALIASING = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String STATUS = "Starting...";
	private static long TIMER_SCRIPT = 0;
	private static int FUR_PRICE, FUR_BOUGHT, FUR_STORED;
	private static int ID_BARAEK = 547, ID_FUR = 948;
	private final Component WIDGET_MENU = ctx.widgets.component(1188, 3);
	private static final Tile[] PATH_TO_NPC = { new Tile(3189, 3435, 0), new Tile(3200, 3429, 0), new Tile(3206, 3429, 0), new Tile(3215, 3433, 0) };

	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();	
		STATUS = "Get prices..";
		FUR_PRICE = getGuidePrice(ID_FUR) - 20;
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

	@Override
	public void poll() {
		if (!ctx.game.loggedIn())
			return;
		switch(state()){
		case CAMERA:
			STATUS = "Set Pitch";
			ctx.camera.pitch(Random.nextInt(48, 55));
			break;
		case STOP:
			STATUS = "Out of gold stopping script...";
			ctx.controller.stop();
			break;
		case FIX:
			STATUS = "Close Menu";
			close();
			break;
		case MENU:
			STATUS = "Select Option";
			ctx.input.send("1");
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !WIDGET_MENU.visible()
								&& ctx.chat.queryContinue();
					}
				}, 250, 20);
			break;
		case CONTINUE:
			STATUS = "Select Continue";
			if (ctx.widgets.component(1191, 6).text().contains("Can you sell me some furs?") || ctx.widgets.component(1191, 6).text().contains("Yeah, OK, here you go.")) {
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
			break;
		case TALKING:
			final Npc Baraek = ctx.npcs.select().id(ID_BARAEK).nearest().poll();
			if (Baraek.inViewport()) {
				STATUS = "Talk to Baraek";
				Baraek.interact("Talk-to", "Baraek");
					if (didInteract()) {
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception { 
								return WIDGET_MENU.valid();
							}
						}, 250, 20);
					}
			} else {
				if (ctx.bank.opened()) {
					STATUS = "Close Bank";
					FUR_STORED = ctx.bank.select().id(ID_FUR).count(true);
					if(Random.nextInt(1, 15) == 10)
					ctx.bank.close();
					else
						close();
				} else {
					STATUS = "Walk to Npc";
					if (!Baraek.inViewport() && ctx.players.local().tile().distanceTo(Baraek.tile()) < 8) {
						ctx.movement.step(ctx.movement.closestOnMap(Baraek.tile()));
						ctx.camera.turnTo(Baraek.tile());
					} else {
						ctx.movement.newTilePath(PATH_TO_NPC).traverse();
					}
				}
			}
			break;
		case BANKING:
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
					ctx.movement.newTilePath(PATH_TO_NPC).reverse().traverse();
				}
			}
			break;
		}
	}
	
	private State state() {
		
		if (ctx.camera.pitch() < 40){
			return State.CAMERA;
		}
		
		antiBan();
		
		if(ctx.widgets.component(1188, 12).text().contains("I can't afford that.")){
			return State.STOP;
	    }
		
		if(ctx.widgets.component(1188, 12).text().contains("Can I have a newspaper, please?")){
			return State.FIX;
		}
		
		if(WIDGET_MENU.valid()){
			return State.MENU;
		}
		
		if(ctx.widgets.component(1191, 6).valid() || ctx.widgets.component(1184, 10).valid()){
			return State.CONTINUE;
		}
		
		if(ctx.backpack.select().count() != 28){
			return State.TALKING;
		}
		

		return State.BANKING;
	}

	private enum State {
		CAMERA, STOP, FIX, MENU, CONTINUE, TALKING, BANKING
	}

	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}

	private void close() {
		ctx.input.send("{VK_ESCAPE down}");
		Condition.sleep(Random.nextInt(50, 400));
		ctx.input.send("{VK_ESCAPE up}");
	}
	
	private int antiBan() {
		int antiban = Random.nextInt(1, 600);
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
		case 8:
			ctx.input.move(Random.nextInt(0, 500), Random.nextInt(0, 500));
			break;
		case 6:
			ctx.camera.pitch(Random.nextInt(40, 55));
			ctx.camera.angle(Random.nextInt(0, 300));
			break;
		}
		return 0;
	}


	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();
		if (message.contains("20 coins have been removed from your money pouch."))
			FUR_BOUGHT++;
	}

	final static Color BLACK = new Color(25, 0, 0, 200);
	final static Font FONT = new Font("Comic Sans MS", 1, 12);
	final Font FONT_TWO = new Font("Comic Sans MS", 1, 9);
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
		g.drawString("Fur In Bank: " + NF.format(FUR_STORED), 10, 80);
		g.drawString("Fur Price: " + FUR_PRICE, 10, 100);
		g.drawString("Profit: " + NF.format(profit()) + "(" + PerHour(profit()) + "/h)", 13, 120);
		g.drawString("Status: " + (STATUS), 10, 140);
		g.setColor(Color.RED);
		g.setFont(FONT_TWO);
		g.drawString("v0.25", 165, 140);
		drawMouse(g);
		drawBaraekTile(g);
	}
	
	private void drawBaraekTile(final Graphics g) {
		final Npc Baraek = ctx.npcs.select().id(ID_BARAEK).nearest().poll();
			if (Baraek.inViewport() && ctx.backpack.select().count() != 28)
				Baraek.tile().matrix(ctx).draw(g);
	}
	
	private static int profit() {
		return FUR_BOUGHT * FUR_PRICE;
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
