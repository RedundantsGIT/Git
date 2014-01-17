package VEPlanker;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.Callable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.powerbot.event.MessageEvent;
import org.powerbot.event.MessageListener;
import org.powerbot.event.PaintListener;
import org.powerbot.script.Manifest;
import org.powerbot.script.PollingScript;
import org.powerbot.script.methods.Hud;
import org.powerbot.script.methods.Game.Crosshair;
import org.powerbot.script.methods.Hud.Window;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.GeItem;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Item;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(name = "rPlanker", description = "Testing", hidden = true)
public class rPlanker extends PollingScript implements PaintListener, MessageListener {
	rPlankerGUI g = new rPlankerGUI();
	public boolean guiWait = true;
	private static String status = "Waiting";
	private static long ELAPSED_TIMER = 0;
	private static boolean USE_POTIONS = false;
	private static boolean USE_TELETABS = false;
	private static final int WIDGET_PLANK_MENU = 403;
	private static final int WIDGET_MAKE_NORMAL_PLANKS = 16;
	private static final int WIDGET_MAKE_OAK_PLANKS = 17;
	private static final int WIDGET_MAKE_TEAK_PLANKS = 18;
	private static final int WIDGET_MAKE_MAHOGANY_PLANKS = 19;
	private static final int[] ALL_PLANK_ID = { 960, 8778, 8780, 8782 };
	private static final int[] ENERGY_POTION_ID = { 3008, 3010, 3012, 3014,
			23375, 23377, 23379, 23381, 23383, 23385, 11453, 11455, 23387,
			23389, 23391, 23393, 23395, 23397, 11481, 11483, 3016, 3018, 3020, 3022 };
	private static int WIDGET_CHOSEN, LOG_CHOSEN, PLANKS_GAINED, LOGS_LEFT, LOG_PRICE, PLANK_PRICE, PROFIT_TOTAL, POUCH_AMOUNT;
	private static int NORMAL_PLANK_ID = 960, OAK_PLANK_ID = 8778, TEAK_PLANK_ID = 8780, MAHOGANY_PLANK_ID = 8782, VARROCK_TELEPORT_ID = 8007;
	private static final int NORMAL_LOG_ID = 1511, OAK_LOG_ID = 1521, TEAK_LOG_ID = 6333, MAHOGANY_LOG_ID = 6332;
	private static final int BANKER_ID = 553, OPERATOR_ID = 4250;
	
	private final Tile[] WALK_TO_BANK_PATH = new Tile[] {
			new Tile(3213, 3425, 0), new Tile(3219, 3427, 0),
			new Tile(3222, 3428, 0), new Tile(3226, 3429, 0),
			new Tile(3232, 3430, 0), new Tile(3237, 3429, 0),
			new Tile(3242, 3429, 0), new Tile(3249, 3429, 0),
			new Tile(3253, 3429, 0), new Tile(3252, 3421, 0) };

	private final Tile[] WALK_TO_OPERATOR_PATH = new Tile[] {
			new Tile(3253, 3420, 0), new Tile(3255, 3425, 0),
			new Tile(3258, 3429, 0), new Tile(3267, 3430, 0),
			new Tile(3277, 3430, 0), new Tile(3277, 3437, 0),
			new Tile(3277, 3442, 0), new Tile(3277, 3448, 0),
			new Tile(3278, 3453, 0), new Tile(3280, 3459, 0),
			new Tile(3284, 3465, 0), new Tile(3286, 3469, 0),
			new Tile(3287, 3476, 0), new Tile(3287, 3481, 0),
			new Tile(3293, 3486, 0), new Tile(3303, 3490, 0) };

	public rPlanker() {
		getExecQueue(State.START).add(new Runnable() {
			@Override
			public void run() {
				ELAPSED_TIMER = System.currentTimeMillis();
				g.setVisible(true);
				while (guiWait) {
					sleep(100);
				}
			}
		});
	}

	@Override
	public int poll() {
		if (!ctx.game.isLoggedIn()
				|| ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) {
			return 1000;
		}
		if (potionUsable()) {
			usePotion();
		} else {
			if (InventoryContainsLogs()) {
				Plank();
			} else {
				if (USE_TELETABS) {
					Teletab();
				} else {
					Bank();
				}
			}
		}
		return 25;
	}

	private void usePotion() {
		final Item EnergyPotion = ctx.backpack.select().id(ENERGY_POTION_ID).poll();
		status = "Use Potion";
		if(!ctx.hud.isVisible(Window.BACKPACK)){
			ctx.hud.view(Hud.Window.BACKPACK);
		}else{
		if (EnergyPotion.interact("Drink")) {
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return ctx.movement.getEnergyLevel() > 50;
				}
			}, 80, 20);
		}
		}
	}

	private void Plank() {
		final Npc Planker = ctx.npcs.select().id(OPERATOR_ID).poll();
		final org.powerbot.script.wrappers.Widget PlankMenu = ctx.widgets.get(WIDGET_PLANK_MENU);
		if (ctx.backpack.getMoneyPouch() < POUCH_AMOUNT) {
			getController().stop();
		} else {
			if (!nearMillOperator()) {
				status = "Walk to npc";
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(9, 10)) {
					ctx.movement.newTilePath(WALK_TO_OPERATOR_PATH).traverse();
				}
			} else {
				if (!PlankMenu.getComponent(WIDGET_CHOSEN).isOnScreen()) {
					status = "Buy";
					Planker.interact("Buy");
					if(didInteract()){
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return PlankMenu.getComponent(WIDGET_CHOSEN).isOnScreen();
						}
					}, 250, 20);
					}
				} else {
					status = "Buy all";
					PlankMenu.getComponent(WIDGET_CHOSEN).interact("Buy All");
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return !PlankMenu.getComponent(WIDGET_CHOSEN).isOnScreen();
						}
					}, 250, 20);
				}
			}

		}
	}

	private void Bank() {
		if (!nearBanker()) {
			status = "Walk to bank";
			if (!ctx.players.local().isInMotion()
					|| ctx.players.local().getLocation()
							.distanceTo(ctx.movement.getDestination()) < Random
							.nextInt(9, 10)) {
				ctx.movement.newTilePath(WALK_TO_OPERATOR_PATH).reverse().traverse();
			}
		} else {
			if (!ctx.bank.isOpen()) {
				status = "Bank open";
				ctx.bank.open();
			} else {
				Banking();
			}
		}

	}

	private void Banking() {
		status = "Banking...";
		final Item Logs = ctx.bank.select().id(LOG_CHOSEN).poll();
		if (!ctx.bank.select().contains(Logs)) {
			ctx.bank.close();
			getController().stop();
		} else {
			if (ctx.backpack.select().count() == 28) {
				if (InventoryContainsPotions() && USE_POTIONS) {
					deposit(0, ALL_PLANK_ID);
				} else {
					ctx.bank.depositInventory();
					Condition.wait(new Callable<Boolean>() {
						@Override
						public Boolean call() throws Exception {
							return ctx.backpack.select().isEmpty();
						}
					}, 250, 20);
				}
			} else {
				if (BankContainsTeletab() && !InventoryContainsTeletab()
						&& USE_TELETABS) {
					ctx.bank.withdraw(VARROCK_TELEPORT_ID, 28);
				} else if (BankContainsPotions() && !InventoryContainsPotions() && USE_POTIONS) {
					withdraw(1, ENERGY_POTION_ID);
				} else {
					withdraw(28, LOG_CHOSEN);
					LOGS_LEFT = ctx.bank.select().id(LOG_CHOSEN).count(true);
					ctx.bank.close();
				}
			}
		}
	}

	private void Teletab() {
		final Item VarrockTeleport = ctx.backpack.select().id(VARROCK_TELEPORT_ID).poll();
		if (nearMillOperator()) {
			status = "Use teletab";
			if (VarrockTeleport.interact("Break")) {
				Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return !nearMillOperator();
					}
				}, 250, 20);
			}
		} else {
			if (!nearBanker()) {
				status = "Walk to bank";
				if (!ctx.players.local().isInMotion() || ctx.players.local().getLocation().distanceTo(ctx.movement.getDestination()) < Random.nextInt(7, 8)) {
					ctx.movement.newTilePath( WALK_TO_BANK_PATH).traverse();
				}
			} else {
				if (ctx.bank.isOpen()) {
					Banking();
				} else {
					status = "Bank open";
					ctx.bank.open();
				}
			}
		}

	}
	
	public boolean didInteract() {
		return ctx.game.getCrosshair() == Crosshair.ACTION;
	}

	private boolean potionUsable() {
		return ctx.movement.getEnergyLevel() < 51 && !ctx.bank.isOpen() && !ctx.widgets.get(WIDGET_PLANK_MENU).isValid() && InventoryContainsPotions()
				&& ctx.players.local().isInMotion() && USE_POTIONS;
	}

	private boolean nearBanker() {
		final Npc Banker = ctx.npcs.select().id(BANKER_ID).poll();
		return Banker != null && Banker.isOnScreen() && ctx.players.local().getLocation().distanceTo(Banker) < 7;
	}

	public boolean withdraw(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.withdraw(i, count)) {
				break;
			}
		}
		return true;
	}

	public boolean deposit(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.deposit(i, count)) {
				break;
			}
		}
		return true;
	}

	private boolean BankContainsTeletab() {
		final Item Tab = ctx.bank.select().id(VARROCK_TELEPORT_ID).poll();
		return ctx.bank.select().contains(Tab);
	}

	private boolean InventoryContainsTeletab() {
		final Item Tab = ctx.backpack.select().id(VARROCK_TELEPORT_ID).poll();
		return ctx.backpack.select().contains(Tab);
	}

	private boolean BankContainsPotions() {
		final Item Potions = ctx.bank.select().id(ENERGY_POTION_ID).poll();
		return ctx.bank.select().contains(Potions);
	}

	private boolean InventoryContainsPotions() {
		final Item Potions = ctx.backpack.select().id(ENERGY_POTION_ID).poll();
		return ctx.backpack.select().contains(Potions);
	}

	private boolean InventoryContainsLogs() {
		final Item LogsChosen = ctx.backpack.select().id(LOG_CHOSEN).poll();
		return ctx.backpack.select().contains(LogsChosen);
	}

	private boolean nearMillOperator() {
		final Npc Planker = ctx.npcs.select().id(OPERATOR_ID).poll();
		return Planker.isOnScreen();
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

	@Override
	public void messaged(MessageEvent msg) {
		String message = msg.getMessage();
		if (message.contains(" coins have been removed from your money pouch")) {
			PLANKS_GAINED++;
		}
	}

	final Color Black = new Color(0, 0, 0, 200);
	final Font Font = new Font("Times New Roman", 0, 13);
	final Font FontTwo = new Font("Arial", 1, 12);
	final NumberFormat nf = new DecimalFormat("###,###,###,###");

	@Override
	public void repaint(Graphics g) {

		long millis = System.currentTimeMillis() - ELAPSED_TIMER;
		long hours = millis / (1000 * 60 * 60);
		millis -= hours * (1000 * 60 * 60);
		long minutes = millis / (1000 * 60);
		millis -= minutes * (1000 * 60);
		long seconds = millis / 1000;

		g.setColor(Black);
		g.fillRoundRect(340, 65, 175, 125, 10, 10);
		g.drawRoundRect(340, 65, 175, 125, 10, 10);
		g.setFont(FontTwo);
		g.setColor(Color.GREEN);
		g.drawString("rPlanker", 395, 78);
		g.setFont(Font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 351, 100);
		g.drawString("Planks Made: " + nf.format(PLANKS_GAINED) + "(" + perHour(PLANKS_GAINED) + "/h)", 351, 120);
		g.drawString("Profit: " + nf.format(profitGained()) + "(" + perHour(profitGained()) + "/h)", 351, 140);
		g.drawString("Logs Left: " + nf.format(LOGS_LEFT), 351, 160);
		g.drawString("Status: " + status, 351, 180);
		g.drawString("v0.3", 490, 180);
		drawMouse(g);

	}

	private void drawMouse(Graphics g) {
		g.setColor(ctx.mouse.isPressed() ? Color.RED : Color.YELLOW);
		final Point m = ctx.mouse.getLocation();
		g.drawLine(m.x - 5, m.y + 5, m.x + 5, m.y - 5);
		g.drawLine(m.x - 5, m.y - 5, m.x + 5, m.y + 5);

	}

	private static int profitGained() {
		return PLANKS_GAINED * PROFIT_TOTAL;
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

	private String perHour(int gained) {
		return formatNumber((int) ((gained) * 3600000D / (System
				.currentTimeMillis() - ELAPSED_TIMER)));
	}

	private static int getGuidePrice(final int id) {
		return GeItem.getPrice(id);
	}

	public class rPlankerGUI extends JFrame {
		private static final long serialVersionUID = 1L;

		public rPlankerGUI() {
			initComponents();
		}

		private void startButtonActionPerformed(ActionEvent e) {
			String chosen = PlanksToMake.getSelectedItem().toString();
			if (checkBox1.isSelected()) {
				USE_POTIONS = true;
			}
			if (checkBox2.isSelected()) {
				USE_TELETABS = true;
			}
			if (chosen.equals("Normal")) {
				LOG_CHOSEN = NORMAL_LOG_ID;
				WIDGET_CHOSEN = WIDGET_MAKE_NORMAL_PLANKS;
				PLANK_PRICE = getGuidePrice(NORMAL_PLANK_ID) - 100;
				LOG_PRICE = getGuidePrice(LOG_CHOSEN);
				PROFIT_TOTAL = PLANK_PRICE - LOG_PRICE;
				POUCH_AMOUNT = 2800;
			} else if (chosen.equals("Oak")) {
				LOG_CHOSEN = OAK_LOG_ID;
				WIDGET_CHOSEN = WIDGET_MAKE_OAK_PLANKS;
				PLANK_PRICE = getGuidePrice(OAK_PLANK_ID) - 250;
				LOG_PRICE = getGuidePrice(LOG_CHOSEN);
				PROFIT_TOTAL = PLANK_PRICE - LOG_PRICE;
				POUCH_AMOUNT = 7000;
			} else if (chosen.equals("Teak")) {
				LOG_CHOSEN = TEAK_LOG_ID;
				WIDGET_CHOSEN = WIDGET_MAKE_TEAK_PLANKS;
				PLANK_PRICE = getGuidePrice(TEAK_PLANK_ID) - 500;
				LOG_PRICE = getGuidePrice(LOG_CHOSEN);
				PROFIT_TOTAL = PLANK_PRICE - LOG_PRICE;
				POUCH_AMOUNT = 14000;
			} else if (chosen.equals("Mahogany")) {
				LOG_CHOSEN = MAHOGANY_LOG_ID;
				WIDGET_CHOSEN = WIDGET_MAKE_MAHOGANY_PLANKS;
				PLANK_PRICE = getGuidePrice(MAHOGANY_PLANK_ID) - 1500;
				LOG_PRICE = getGuidePrice(LOG_CHOSEN);
				PROFIT_TOTAL = PLANK_PRICE - LOG_PRICE;
				POUCH_AMOUNT = 42000;
			}
			guiWait = false;
			g.dispose();
		}

		private void initComponents() {
			ScriptTitle = new JLabel();
			TypeOfPlankLabel = new JLabel();
			PlanksToMake = new JComboBox<String>();
			startButton = new JButton();
			checkBox1 = new JCheckBox();
			checkBox2 = new JCheckBox();

			setTitle("rPlanker GUI");
			Container contentPane = getContentPane();
			contentPane.setLayout(null);

			ScriptTitle.setText("rPlanker Setup");
			ScriptTitle.setFont(new Font("Segoe Print", java.awt.Font.BOLD, 14));
			contentPane.add(ScriptTitle);
			ScriptTitle.setBounds(35, 5, 110, ScriptTitle.getPreferredSize().height);

			TypeOfPlankLabel.setText("Type of plank:");
			TypeOfPlankLabel.setFont(TypeOfPlankLabel.getFont().deriveFont(TypeOfPlankLabel.getFont().getStyle() | java.awt.Font.BOLD));
			contentPane.add(TypeOfPlankLabel);
			TypeOfPlankLabel.setBounds(new Rectangle(new Point(0, 50), TypeOfPlankLabel.getPreferredSize()));

			PlanksToMake.setModel(new DefaultComboBoxModel<String>(new String[] { "Normal", "Oak", "Teak", "Mahogany" }));
			contentPane.add(PlanksToMake);
			PlanksToMake.setBounds(85, 45, 100, PlanksToMake.getPreferredSize().height);

			startButton.setText("Start");
			startButton.setFont(startButton.getFont().deriveFont(startButton.getFont().getStyle() | java.awt.Font.BOLD));
			startButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					startButtonActionPerformed(e);
				}
			});
			contentPane.add(startButton);
			startButton.setBounds(new Rectangle(new Point(60, 105), startButton.getPreferredSize()));

			checkBox1.setText("Use potions?");
			checkBox1.setFont(checkBox1.getFont().deriveFont(checkBox1.getFont().getStyle() | java.awt.Font.BOLD));
			contentPane.add(checkBox1);
			checkBox1.setBounds(new Rectangle(new Point(0, 75), checkBox1.getPreferredSize()));

			checkBox2.setText("Use teletabs?");
			checkBox2.setFont(checkBox2.getFont().deriveFont(checkBox2.getFont().getStyle() | java.awt.Font.BOLD));
			contentPane.add(checkBox2);
			checkBox2.setBounds(new Rectangle(new Point(95, 75), checkBox2.getPreferredSize()));

			{
				Dimension preferredSize = new Dimension();
				for (int i = 0; i < contentPane.getComponentCount(); i++) {
					Rectangle bounds = contentPane.getComponent(i).getBounds();
					preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
					preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
				}
				Insets insets = contentPane.getInsets();
				preferredSize.width += insets.right;
				preferredSize.height += insets.bottom;
				contentPane.setMinimumSize(preferredSize);
				contentPane.setPreferredSize(preferredSize);
			}
			pack();
			setLocationRelativeTo(getOwner());
		}

		private JLabel ScriptTitle;
		private JLabel TypeOfPlankLabel;
		private JComboBox<String> PlanksToMake;
		private JButton startButton;
		private JCheckBox checkBox1;
		private JCheckBox checkBox2;
	}
}