package rBeerFlipper;

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
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.Interactive;
import org.powerbot.script.rt6.Npc;

@Manifest(name = "rBeerFlipper", description = "Buys beer in varrock for money.", properties = "hidden=true")
public class Beer extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {
	private static RenderingHints ANTIALIASING = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	private static String STATUS = "Starting...";
	private static long TIMER_SCRIPT = 0;
	private static int ID_BARTENDER = 733;
	private static int BEER_BOUGHT, BEER_PRICE;
	
	private static final Tile[] PATH_TO_BARTENDER = { 
		    new Tile(3189, 3435, 0), new Tile(3194, 3430, 0), 
			new Tile(3197, 3426, 0), new Tile(3199, 3419, 0), 
			new Tile(3204, 3411, 0), new Tile(3211, 3404, 0), 
			new Tile(3211, 3398, 0), new Tile(3215, 3395, 0), 
			new Tile(3223, 3399, 0) };

	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
		STATUS = "Get prices..";
		BEER_PRICE = getGuidePrice(1917);
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
		case STOP:
				ctx.controller.stop();
			break;
		case BUY:
			if (ctx.chat.queryContinue()) {
				STATUS = "Select continue";
				ctx.chat.clickContinue(true);
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return ctx.widgets.component(1188, 12).visible() 
							|| ctx.widgets.component(1184, 9).text().contains("No problemo. That'll be 2 coins.")
							|| !ctx.chat.queryContinue();
					}
				}, 250, 20);
			} else {
				if (ctx.chat.select().text("A glass of your finest ale please.").poll().valid()) {
					STATUS = "Select option";
					ctx.input.send("1");
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.chat.queryContinue();
						}
					}, 250, 20);
				} else {
					final int[] BartenderBounds = {240, 184, -660, -320, -136, 76};
					final Npc Bartender = ctx.npcs.select().id(ID_BARTENDER).each(Interactive.doSetBounds(BartenderBounds)).nearest().poll();
					if (ctx.players.local().tile().distanceTo(Bartender.tile()) < 8) {
						if (Bartender.inViewport()) {
							if (!ctx.players.local().inMotion()) {
								STATUS = "Talk to NPC";
								Bartender.interact("Talk-to", "Bartender");
								Condition.wait(new Callable<Boolean>() {
									@Override
									public Boolean call() throws Exception {
										return ctx.chat.queryContinue();
									}
								}, 250, 20);
							}
						} else {
							ctx.camera.turnTo(Bartender);
						}
					} else {
						if (ctx.bank.opened()) {
							STATUS = "Closing bank";
							ctx.bank.close();
						} else {
							STATUS = "Path to NPC";
							ctx.movement.newTilePath(PATH_TO_BARTENDER).traverse();
						}
					}
				}
			}
			break;
		case BANKING:
			if (ctx.players.local().tile().distanceTo(ctx.bank.nearest().tile()) < 4){
				if (ctx.bank.inViewport()) {
					if (ctx.bank.open()) {
						STATUS = "Deposit";
						ctx.bank.depositInventory();
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.backpack.select().isEmpty();
							}
						}, 250, 20);
					} else {
						STATUS = "Opening bank";
						ctx.bank.open();
					}
				}
			} else {
				STATUS = "Path to Bank";
				ctx.movement.newTilePath(PATH_TO_BARTENDER).reverse().traverse();
			}
			break;
		}
	}
	
	private State state() {
		
		if(ctx.camera.pitch() < 52){
			ctx.camera.pitch(Random.nextInt(55, 60));
		}
		
		if(ctx.backpack.moneyPouchCount() < 2){
			return State.STOP;
		}
		
		if(ctx.backpack.select().count() != 28){
			return State.BUY;
		}

		return State.BANKING;
	}

	private enum State {
		STOP, BANKING, BUY
	}

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.text();

		if (message.contains("You can't reach that.")){
			ctx.movement.newTilePath(PATH_TO_BARTENDER).traverse();
		}
		
		if (message.contains("2 coins have")){
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

		long millis = System.currentTimeMillis() - TIMER_SCRIPT;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setRenderingHints(ANTIALIASING);
		g.setColor(BLACK);
		g.fillRect(5, 5, 180, 105);
		g.setColor(Color.RED);
		g.drawRect(5, 5, 180, 105);
		g.setFont(FONT);
		g.drawString("rBeerFlipper", 65, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Beer Bought: " + NF.format(BEER_BOUGHT) + "(" + PerHour(BEER_BOUGHT) + "/h)", 10, 60);
		g.drawString("Profit: " + NF.format(profit()) + "(" + PerHour(profit()) + "/h)", 13, 80);
		g.drawString("Status: " + (STATUS), 10, 100);
		g.setColor(Color.RED);
		g.setFont(FONT_TWO);
		g.drawString("v0.01", 160, 125);
		drawMouse(g);
	}
	
	private int profit(){
		return BEER_BOUGHT * BEER_PRICE;
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
