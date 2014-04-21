package rTanner;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.powerbot.script.Area;
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
import org.powerbot.script.rt6.GameObject;
import org.powerbot.script.rt6.Hud.Window;
import org.powerbot.script.rt6.Interactive;
import org.powerbot.script.rt6.Item;
import org.powerbot.script.rt6.Npc;

@Manifest(name = "rTanner", description = "Tans all hides in Al-Kharid & Burthorpe for (gp) [Supports all hides/potions]", properties = "topic=876982")
public class rTanner extends PollingScript<org.powerbot.script.rt6.ClientContext> implements PaintListener, MessageListener {

	private static long elapsedTime = 0;

	private static RenderingHints antialiasing = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

	private static String location;
	private static String status = "Starting...";
	
	private rTannerGUI g = new rTannerGUI();
	private static boolean guiWait = true;
	private static boolean usePotions = false;

	private static boolean atAlKharid = false;
	private static boolean atBurthorpe = false;
	private static boolean atVarrock = false;
	private static boolean usePreset = false;

	private static int hideCount, hidesLeft, potionsLeft;

	private static final int doorID = 24376, mangleID = 24920;
	
	private final Component make = ctx.widgets.widget(1370).component(20);
	private final Component achievements = ctx.widgets.widget(1477).component(74);
	private final Component collectionBox = ctx.widgets.widget(109).component(61);

	private static final int[] tannerID = { 14877, 2824, 2320 };
	private static final int[] hideID = { 1739, 1753, 1751, 24372, 6287, 7801, 1749, 1747 };
	private static final int[] leatherID = { 1741, 1743, 1745, 2505, 24374, 6289, 2507, 2509 };
	private static final int[] energyPotionID = { 3008, 3010, 3012, 3014, 23375, 23377, 23379, 23381, 
		                                          23383, 23385, 11453, 11455, 23387, 23389, 23391, 23393, 
		                                          23395, 23397, 11481, 11483, 3016, 3018, 3020, 3022 };
	
	private static final Tile doorTile = new Tile(3187, 3403, 0);
	private static Tile[] tilePath;
	
	private static final Tile[] pathToEllis = { new Tile(3272, 3168, 0), new Tile(3276, 3178, 0), new Tile(3280, 3187, 0), new Tile(3275, 3195, 0) };
	private static final Tile[] pathToJack = { new Tile(2884, 3535, 0), new Tile(2881, 3530, 0), new Tile(2882, 3523, 0), new Tile(2885, 3514, 0), new Tile(2889, 3510, 0), new Tile(2887, 3502, 0) };
	private static final Tile[] pathToTanner = new Tile[] { 
	    new Tile(3182, 3435, 0), new Tile(3184, 3430, 0),
		new Tile(3183, 3427, 0), new Tile(3182, 3423, 0),
		new Tile(3182, 3418, 0), new Tile(3182, 3413, 0),
		new Tile(3182, 3408, 0), new Tile(3187, 3403, 0) };
	
	private static final Area areaBurthorpe = new Area(new Tile[] { new Tile(2877, 3540, 0), new Tile(2900, 3540, 0), new Tile(2899, 3479, 0), new Tile(2875, 3479, 0) });
	private static final Area areaAlKharid = new Area(new Tile[] { new Tile(3239, 3154, 0), new Tile(3315, 3151, 0), new Tile(3319, 3224, 0), new Tile(3250, 3223, 0) });
	private static final Area areaVarrock = new Area(new Tile[] { new Tile(3166, 3445, 0), new Tile(3171, 3390, 0), new Tile(3214, 3397, 0), new Tile(3206, 3453, 0) });

	private static JobContainer container;

	@Override
	public void start() {
		elapsedTime = System.currentTimeMillis();
		log.info("start()");
		g.setVisible(true);
		while (guiWait) {
			status = "GUI";
			final Timer guiTimer = new Timer(Random.nextInt(500, 1000));
			while (guiTimer.isRunning());
		}
		ctx.camera.pitch(false);
		rTanner.container = new JobContainer(new Job[] { new GetPlayerArea(ctx), new Pitch(ctx), new CloseInterfaces(ctx), new Door(ctx), 
				new UseEnergyPotion(ctx), new Tan(ctx), new Banking(ctx) });
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
		log.info("[rTanner]: -Total Hides Tanned: " + hideCount);
	}

	public abstract class Job extends ClientAccessor {
		public Job(ClientContext ctx) {
			super(ctx);
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
		final Job job = container.get();
		if (job != null) {
			job.execute();
		}
	}


	private class GetPlayerArea extends Job {
		public GetPlayerArea(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return !atAlKharid && !atBurthorpe && !atVarrock;
		}

		@Override
		public void execute() {
			if (areaBurthorpe.contains(ctx.players.local().tile())) {
				location = "Burthorpe";
				tilePath = randomizePath(pathToJack, 2, 2);
				atBurthorpe = true;
			} else if (areaAlKharid.contains(ctx.players.local().tile())) {
				location = "Al Kharid";
				tilePath = randomizePath(pathToEllis, 2, 2);
				atAlKharid = true;
			} else if (areaVarrock.contains(ctx.players.local().tile())) {
				location = "Varrock";
				tilePath = randomizePath(pathToTanner, 2, 2); 
				atVarrock = true;
			}
		}
	}

	private class Pitch extends Job {
		public Pitch(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.camera.pitch() < 33;
		}

		@Override
		public void execute() {
			status = "Set Pitch";
			ctx.camera.pitch(Random.nextInt(35, 40));
		}
	}

	private class CloseInterfaces extends Job {
		public CloseInterfaces(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return achievements.visible() || collectionBox.visible();
		}

		@Override
		public void execute() {
			status = "Close";
			close();
		}
	}

	private class UseEnergyPotion extends Job {
		public UseEnergyPotion(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return ctx.players.local().inMotion() && ctx.movement.energyLevel() < 50 && hasPotion() && !ctx.bank.opened() && !make.visible();
		}

		@Override
		public void execute() {
			final Item EnergyPotion = ctx.backpack.select().id(energyPotionID).poll();
			if (ctx.hud.opened(Window.BACKPACK)) {
				status = "Use Potion";
				if(EnergyPotion.interact("Drink")) {
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.movement.energyLevel() > 50;
						}
					}, 250, 20);
				}
			} else {
				status = "Open Backpack";
				ctx.hud.open(Window.BACKPACK);
			}
		}

	}

	private class Door extends Job {
		public Door(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return tileContainsDoor() && !make.visible();
		}

		@Override
		public void execute() {
			final int[] doorBounds = {-200, 150, -1000, 0, 0, 0};
			final GameObject Door = ctx.objects.select().id(doorID).each(Interactive.doSetBounds(doorBounds)).at(doorTile).poll();
			final GameObject Mangle = ctx.objects.select().id(mangleID).nearest().poll();
				if (Door.inViewport()) {
					status = "Door";
					ctx.camera.turnTo(Mangle.tile());
					if(Door.interact("Open", "Door")){
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !tileContainsDoor();
						}
					}, 250, 20);
				}
			} else {
				ctx.movement.step(ctx.movement.closestOnMap(doorTile));
			}
		}
	}

	private class Tan extends Job {
		public Tan(ClientContext ctx) {
			super(ctx);
		}

		@Override
		public boolean activate() {
			return hasHide();
		}

		@Override
		public void execute() {
			final Component CloseButton = ctx.widgets.widget(1370).component(30); 
			final Npc Tanner = ctx.npcs.select().id(tannerID).nearest().poll();
			if (ctx.backpack.moneyPouchCount() < 600) {
				log.info("[rTanner]: -Gold dropped below 600, logging out...");
				logOut();
			} else {
				if (atTanner()) {
					if (make.valid()) {
						if (make.interact("Make")) {
							Condition.wait(new Callable<Boolean>() {
								@Override
								public Boolean call() throws Exception {
									return !make.visible();
								}
							}, 100, 20);
						}
						if (CloseButton.visible()) 
							CloseButton.interact("Close");
					} else {
						if (Tanner.inViewport()) {
							status = "Talk to Tanner";
							if (atAlKharid) {
								Tanner.interact("Tan hides", "Ellis");
							} else if (atBurthorpe) {
								Tanner.interact("Tan hide", "Jack Oval");
							} else {
								Tanner.interact("Trade", "Tanner");
							}
							if(didInteract()){
							Condition.wait(new Callable<Boolean>() {
									@Override
									public Boolean call() throws Exception {
										return make.visible() || hasLeather();
									}
								}, 250, 20);
								while (ctx.players.local().inMotion() && !make.visible());
							}
						} else {
							if (!ctx.players.local().inMotion() || ctx.players.local().tile().distanceTo(ctx.movement.destination()) < Random.nextInt(2, 3))
								ctx.movement.step(ctx.movement.closestOnMap(Tanner.tile()));
							    ctx.camera.turnTo(Tanner.tile());
						}
					}
				} else {
					if (ctx.bank.opened()) {
						status = "Close Bank";
						if(Random.nextInt(1, 15) == 10)
						ctx.bank.close();
						else
							close();
					} else {
						status = "Walking to Tanner";
						if (!ctx.players.local().inMotion() || ctx.players.local().tile().distanceTo(ctx.movement.destination()) < Random.nextInt(7, 8)) {
							ctx.movement.step(getNextTile(randomizePath(tilePath , 3, 3)));
							if(Random.nextInt(1, 5) == 3)
								cameraTurnToTanner();
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
			return !hasHide();
		}

		@Override
		public void execute() {
			if (atBank()) {
				if (ctx.bank.opened()) {
					hidesLeft = ctx.bank.select().id(hideID).count(true);
					potionsLeft = ctx.bank.select().id(energyPotionID).count(true);
					if (ctx.backpack.select().count() == 28) {
						if (hasLeather() && hasPotion()) {
							deposit(0, leatherID);
						} else {
							if(usePreset)
								usePreset();
							else
							depositInventory();
						}
					} else {
						if (bankHasHide()) {
							if (hasPotion() && !hasHide() && ctx.backpack.select().count() > 1  || hasLeather() && !hasPotion() 
									|| !ctx.backpack.select().isEmpty() && !hasHide() && !hasPotion()) {
								depositInventory();
							} else if (hasLeather() && hasPotion()) {
								status = "Depositing Leather";
								deposit(0, leatherID);
							} else if (usePotions && bankHasPotion() && !hasPotion() && !hasHide()) {
								status = "Withdraw Potion";
								withdraw(1, energyPotionID);
							} else {
								status = "Withdraw Hides";
								withdraw(0, hideID);
							}
						} else {
							logOut();
						}
					}
				} else {
					status = "Opening Bank";
					if(!ctx.players.local().inMotion())
					ctx.camera.turnTo(ctx.bank.nearest());
					ctx.bank.open();
				}
			} else {
				status = "Walking to Bank";
				if (!ctx.players.local().inMotion() || ctx.players.local().tile().distanceTo(ctx.movement.destination()) < Random.nextInt(7, 8)) {
					ctx.movement.step(getNextTile(randomizePath(reversePath(tilePath), 3, 3)));
					if(Random.nextInt(1, 3) == 2)
					ctx.camera.turnTo(ctx.bank.nearest());
				}
			}
		}
	}
	
	private boolean didInteract() {
		return ctx.game.crosshair() == Crosshair.ACTION;
	}
	
	private boolean hasPotion() {
		return !ctx.backpack.select().id(energyPotionID).isEmpty();
	}

	private boolean hasLeather() {
		return !ctx.backpack.select().id(leatherID).isEmpty();
	}

	private boolean hasHide() {
			return !ctx.backpack.select().id(hideID).isEmpty();
	}

	private boolean bankHasHide() {
		return !ctx.bank.select().id(hideID).isEmpty();
	}

	private boolean bankHasPotion() {
		return !ctx.bank.select().id(energyPotionID).isEmpty();
	}
	
	private boolean atBank() {
		return ctx.bank.inViewport() && ctx.players.local().tile().distanceTo(ctx.bank.nearest()) < 10;
	}
	
	private boolean tileContainsDoor() {
		final GameObject Door = ctx.objects.select().select().id(doorID).at(doorTile).poll();
			return Door.valid() && ctx.players.local().tile().distanceTo(Door.tile()) < 14;
	}
	
	private boolean atTanner() {
		final Npc Tanner = ctx.npcs.select().id(tannerID).nearest().poll();
		return ctx.players.local().tile().distanceTo(Tanner.tile()) < 10;
	}
	
	private void close() {
		ctx.keyboard.send("{VK_ESCAPE down}");
		final Timer DelayTimer = new Timer(Random.nextInt(50, 600));
		while (DelayTimer.isRunning());
		ctx.keyboard.send("{VK_ESCAPE up}");
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
	
	private boolean logOut() {
		status = "Logout";
		if (ctx.bank.opened() && !ctx.backpack.select().isEmpty()) {
			depositInventory();
			ctx.bank.close();
		}
		if (ctx.game.logout(true)) {
		ctx.controller().stop();
			return true;
		}
		return false;
	}
	
	private boolean depositInventory() {
		status = "Depositing Backpack";
			if (ctx.bank.depositInventory()) {
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return ctx.backpack.select().isEmpty();
					}
				}, 250, 20);
				return true;
			}
		return false;
	}
	
	private boolean usePreset(){
		final Component present = ctx.widgets.widget(762).component(169);
		status = "Withdraw";
		if(present.visible()){
			present.interact("Withdraw");
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return hasHide();
				}
			}, 250, 20);
			return true;
		}
		return false;
	}
	
	private boolean cameraTurnToTanner() {
		final Npc Tanner = ctx.npcs.select().id(tannerID).nearest().poll();
		ctx.camera.turnTo(Tanner.tile());
		if (Tanner.inViewport())
			return true;
		return false;
	}

	private boolean deposit(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.deposit(i, count)) 
				break;
		}
		return true;
	}

	private boolean withdraw(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.withdraw(i, count)) 
				break;
		}
		return true;
	}
	
	public Tile[] randomizePath(Tile[] path, int maxXDeviation, int maxYDeviation) {
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
	
	final Color black = new Color(0, 0, 0, 200);
	final Font font = new Font("Comic Sans MS", 0, 13);
	final Font fontTwo = new Font("Comic Sans MS", 1, 13);
	final Font fontThree = new Font("Comic Sans MS", 1, 9);
	final Font fontFour = new Font("Comic Sans MS", 4, 11);
	final NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g1) {
		
		long millis = System.currentTimeMillis() - elapsedTime;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		final Graphics2D g = (Graphics2D) g1;

		g.setRenderingHints(antialiasing);
		g.setColor(black);
		g.fillRect(5, 5, 190, 125);
		g.setColor(Color.RED);
		g.drawRect(5, 5, 190, 125);
		g.setFont(fontTwo);
		g.drawString("rTanner", 76, 20);
		g.setFont(font);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 10, 40);
		g.drawString("Tanned: " + nf.format(hideCount) + "(" + perHour(hideCount) + ")", 10, 60);
		g.drawString("Hides Left: " + nf.format(hidesLeft), 10, 80);
		g.drawString("Potions Left: " + nf.format(potionsLeft), 10, 100);
		g.drawString("Location: " + (location), 10, 120);
		g.setFont(fontFour);
		g.setColor(Color.GREEN);
		g.drawString("*" + (status) + "*", 10, 140);
		g.setFont(fontThree);
		g.setColor(Color.RED);
		g.drawString("v5.4", 165, 120);
		drawMouse(g);
	}
	
	public void drawMouse(Graphics2D g) {
		Point p = ctx.mouse.getLocation();
		g.setColor(Color.RED);
		g.setStroke(new BasicStroke(2));
		g.fill(new Rectangle(p.x + 1, p.y - 4, 2, 15));
		g.fill(new Rectangle(p.x - 6, p.y + 2, 16, 2));
	}

	private String perHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System.currentTimeMillis() - elapsedTime)));
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

	@Override
	public void messaged(MessageEvent msg) {
		String m = msg.text().toLowerCase();
		if (m.contains("tanner")) {
			int count = Integer.parseInt(m.replaceAll("\\D", ""));
			hideCount += count;
		}
	}
	
	public class rTannerGUI extends JFrame {
		
		private static final long serialVersionUID = 1L;
		
		public rTannerGUI() {
			initComponents();
		}

		private void button1ActionPerformed(ActionEvent e) {
			if(checkBox1.isSelected())
				usePotions = true;
			if(checkBox2.isSelected())
				usePreset = true;
			guiWait = false;
			g.dispose();
		}

		private void initComponents() {
			label1 = new JLabel();
			button1 = new JButton();
			checkBox1 = new JCheckBox();
			checkBox2 = new JCheckBox();

			setTitle("rTannerGUI");
			Container contentPane = getContentPane();

			label1.setText("rTanner");
			label1.setFont(new Font("Comic Sans MS", Font.BOLD, 20));

			button1.setText("Start");
			button1.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
			button1.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					button1ActionPerformed(e);
				}
			});

			checkBox1.setText("Use Potions");

			checkBox2.setText("Use Preset");

			GroupLayout contentPaneLayout = new GroupLayout(contentPane);
			contentPane.setLayout(contentPaneLayout);
			contentPaneLayout.setHorizontalGroup(
				contentPaneLayout.createParallelGroup()
					.addGroup(contentPaneLayout.createSequentialGroup()
						.addGroup(contentPaneLayout.createParallelGroup()
							.addGroup(contentPaneLayout.createSequentialGroup()
								.addGap(21, 21, 21)
								.addComponent(button1))
							.addGroup(contentPaneLayout.createSequentialGroup()
								.addContainerGap()
								.addComponent(checkBox2))
							.addGroup(contentPaneLayout.createSequentialGroup()
								.addContainerGap()
								.addComponent(checkBox1))
							.addGroup(contentPaneLayout.createSequentialGroup()
								.addContainerGap()
								.addComponent(label1)))
						.addContainerGap(18, Short.MAX_VALUE))
			);
			contentPaneLayout.setVerticalGroup(
				contentPaneLayout.createParallelGroup()
					.addGroup(GroupLayout.Alignment.TRAILING, contentPaneLayout.createSequentialGroup()
						.addContainerGap()
						.addComponent(label1, GroupLayout.PREFERRED_SIZE, 17, GroupLayout.PREFERRED_SIZE)
						.addGap(18, 18, 18)
						.addComponent(checkBox1, GroupLayout.PREFERRED_SIZE, 12, GroupLayout.PREFERRED_SIZE)
						.addGap(14, 14, 14)
						.addComponent(checkBox2, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
						.addGap(11, 11, 11)
						.addComponent(button1, GroupLayout.DEFAULT_SIZE, 21, Short.MAX_VALUE))
			);
			pack();
			setLocationRelativeTo(getOwner());
		}

		private JLabel label1;
		private JButton button1;
		private JCheckBox checkBox1;
		private JCheckBox checkBox2;
	}
}

