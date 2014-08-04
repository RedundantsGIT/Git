package rEmptyJug;

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
import org.powerbot.script.rt6.Bank.Amount;
import org.powerbot.script.rt6.GeItem;
import org.powerbot.script.rt6.Item;
import org.powerbot.script.rt6.Hud.Window;

@Manifest(name = "rEmptyJug (Beta)", description = "Drinks jugs of wine for profit", properties = "hidden=true")
public class rEmptyJug extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {
	private static RenderingHints antialiasing = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	private static long TIMER_SCRIPT = 0;
	private static String STATUS = "Starting...";
	private static final int ID_JUG_OF_WINE = 1993, ID_JUG = 1935;
	private static int JUGS_EMPTIED, PRICE_JUG, PRICE_JUG_OF_WINE;

	@Override
	public void start() {
		TIMER_SCRIPT = System.currentTimeMillis();
		PRICE_JUG_OF_WINE = getGuidePrice(ID_JUG_OF_WINE);
		PRICE_JUG = getGuidePrice(ID_JUG) - PRICE_JUG_OF_WINE;
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
		case ANTIPATTERN:
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
				ctx.input.move(Random.nextInt(0, (int) (ctx.game.dimensions().getWidth() - 1)), 0);
				break;
			}
			break;
		case EMPTY:
			if (ctx.bank.opened()) {
				STATUS = "Close bank";
				ctx.bank.close();
			} else {
				final Item Wine = ctx.backpack.poll();
				if (ctx.hud.opened(Window.BACKPACK)) {
					STATUS = "Drink wine";
					Wine.interact("Drink", "Jug of wine");
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !ctx.players.local().idle();
						}
					}, 200, 20);
				} else {
					STATUS = "Open Backpack";
					ctx.hud.open(Window.BACKPACK);
				}
			}
			break;
		case BANKING:
			if (ctx.bank.opened()) {
				if (ctx.bank.select().id(ID_JUG_OF_WINE).isEmpty()) {
					STATUS = "Stop script...";
					log.info("Out of wine to drink stop script....");
					ctx.controller.stop();
				} else {
					if (ctx.backpack.select().isEmpty()) {
						STATUS = "Withdraw wine";
						ctx.bank.withdraw(ID_JUG_OF_WINE, Amount.ALL);
					} else {
						STATUS = "Depsoit all";
						ctx.bank.depositInventory();
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.backpack.select().isEmpty();
							}
						}, 200, 20);

					}
				}
			} else {
				if (ctx.bank.inViewport()) {
					STATUS = "Open bank";
					if(ctx.camera.pitch() < 35)
						ctx.camera.pitch(40);
						ctx.bank.open();
				} else {
					STATUS = "Walk to bank";
					ctx.camera.turnTo(ctx.bank.nearest());
					ctx.movement.step(ctx.movement.closestOnMap(ctx.bank.nearest()));
					while (ctx.players.local().inMotion());
				}
			}
			break;
		}
	}

	private State state() {

		if(!ctx.players.local().idle()){
			return State.ANTIPATTERN;
		}

		if (ctx.players.local().idle() && !ctx.backpack.select().id(ID_JUG_OF_WINE).isEmpty()) {
			return State.EMPTY;
		}

		return State.BANKING;
	}

	private enum State {
		ANTIPATTERN, EMPTY, BANKING
	}

	@Override
	public void messaged(MessageEvent msg) {
		String m = msg.text();
		if (m.contains("You drink the wine.")) {
			JUGS_EMPTIED++;
		}

	}

	final static Color black = new Color(25, 0, 0, 200);
	final static Font fontTwo = new Font("Comic Sans MS", 1, 12);
	final static NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {

		final Graphics2D g = (Graphics2D) g1;

		long millis = System.currentTimeMillis() - TIMER_SCRIPT;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setRenderingHints(antialiasing);
		g.setColor(black);
		g.fillRect(5, 5, 190, 105);
		g.setColor(Color.RED);
		g.drawRect(5, 5, 190, 105);
		g.setFont(fontTwo);
		g.drawString("rEmptyJug", 70, 20);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Emptied: " + nf.format(JUGS_EMPTIED) + "(" + perHour(JUGS_EMPTIED) + ")", 10, 60);
		g.drawString("Profit: " + nf.format(profit()) + "(" + perHour(profit()) + ")", 10, 80);
		g.drawString("Status: " + (STATUS), 10, 100);
		g.setColor(Color.RED);
		g.drawString("v0.1", 165, 100);
		drawMouse(g);
	}

	private static int profit() {
		return PRICE_JUG * JUGS_EMPTIED;
	}

	public String perHour(int gained) {
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
