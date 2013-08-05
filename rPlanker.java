package rPlanker;

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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;

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
import org.powerbot.script.util.Random;
import org.powerbot.script.util.Timer;
import org.powerbot.script.wrappers.Item;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;

@Manifest(authors = { "Redundant" }, name = "rPlanker", version = 0.3, description = "Testing", hidden = true)
public class rPlanker extends PollingScript implements PaintListener,
		MessageListener {
	rPlankerGUI g = new rPlankerGUI();
	public boolean guiWait = true;
	private static String status = "Waiting";
	private static final Timer ELAPSED_TIMER = new Timer(0);
	private static boolean USE_POTIONS = false;
	private static boolean USE_TELETABS = false;
	private static Timer WAIT;
	private static final int WIDGET_PLANK_MENU = 403;
	private static final int WIDGET_MAKE_NORMAL_PLANKS = 16;
	private static final int WIDGET_MAKE_OAK_PLANKS = 17;
	private static final int WIDGET_MAKE_TEAK_PLANKS = 18;
	private static final int WIDGET_MAKE_MAHOGANY_PLANKS = 19;
	private static final int[] ALL_PLANK_ID = { 960, 8778, 8780, 8782 };
	private static final int[] ENERGY_POTION_ID = { 3008, 3010, 3012, 3014,
			23375, 23377, 23379, 23381, 23383, 23385, 11453, 11455, 23387,
			23389, 23391, 23393, 23395, 23397, 11481, 11483, 3016, 3018, 3020,
			3022 };
	private static int WIDGET_CHOSEN, LOG_CHOSEN, PLANKS_GAINED, LOGS_LEFT,
			LOG_PRICE, PLANK_PRICE, PROFIT_TOTAL, POUCH_AMOUNT;
	private static int NORMAL_PLANK_ID = 960, OAK_PLANK_ID = 8778,
			TEAK_PLANK_ID = 8780, MAHOGANY_PLANK_ID = 8782,
			VARROCK_TELEPORT_ID = 8007;
	private static final int NORMAL_LOG_ID = 1511, OAK_LOG_ID = 1521,
			TEAK_LOG_ID = 6333, MAHOGANY_LOG_ID = 6332;
	private static final int BANKER_ID = 553, OPERATOR_ID = 4250;

	private static final Tile[] WALK_TO_OPERATOR_PATH = {
			new Tile(3253, 3420, 0), new Tile(3265, 3428, 0),
			new Tile(3278, 3431, 0), new Tile(3286, 3441, 0),
			new Tile(3287, 3452, 0), new Tile(3294, 3461, 0),
			new Tile(3296, 3473, 0), new Tile(3301, 3482, 0),
			new Tile(3302, 3489, 0) };

	public rPlanker() {
		getExecQueue(State.START).add(new Runnable() {
			@Override
			public void run() {
				/* Makes the gui appear on startup */
				g.setVisible(true);
				while (guiWait) {
					sleep(100);
				}
			}
		});
	}

	@Override
	public int poll() {
		/* Game isn't logged in/map isn't loaded */
		if (!ctx.game.isLoggedIn()
				|| ctx.game.getClientState() != org.powerbot.script.methods.Game.INDEX_MAP_LOADED) {
			return 1000;
		}

		 /* Use energy potion if energy level is too low && inventory contains an
		 * energy potion
		 */
		if (potionUsable()) {
			usePotion();
		} else {
			/* If inventory contains logs */
			if (InventoryContainsLogs()) {
				Plank();
			} else {/* if inventory doesn't contains logs */
				if (USE_TELETABS) {
					Teletab();
				} else {
					Bank();
				}
			}
		}
		return Random.nextInt(50, 100);
	}

	private void usePotion() {
		final Item EnergyPotion = ctx.backpack.select().id(ENERGY_POTION_ID)
				.first().isEmpty() ? null : ctx.backpack.iterator().next();
		if (EnergyPotion != null) {
			status = "Use Potion";
			EnergyPotion.interact("Drink");
			final Timer potionTimer = new Timer(6500);
			while (potionTimer.isRunning()
					&& ctx.movement.getEnergyLevel() < 50)
				;
			sleep(Random.nextInt(150, 250));
		}
	}

	private void Plank() {
		final Npc Planker = ctx.npcs.select().id(OPERATOR_ID).first().isEmpty() ? null
				: ctx.npcs.iterator().next();

		final org.powerbot.script.wrappers.Widget PlankMenu = ctx.widgets
				.get(WIDGET_PLANK_MENU);
		if (ctx.backpack.getMoneyPouch() < POUCH_AMOUNT) {
			getController().stop();
		} else {
			if (!nearMillOperator()) {
				status = "Walk to npc";
				ctx.movement.newTilePath(WALK_TO_OPERATOR_PATH).traverse();
			} else {
				if (!PlankMenu.getComponent(WIDGET_CHOSEN).isOnScreen()) {
					status = "Buy";
					Planker.interact("Buy");
					final Timer buyTimer = new Timer(Random.nextInt(2500, 3500));
					while (buyTimer.isRunning()
							&& !PlankMenu.getComponent(WIDGET_CHOSEN)
									.isOnScreen())
					sleep(Random.nextInt(50, 75));
				} else {
					status = "Buy all";
					PlankMenu.getComponent(WIDGET_CHOSEN).interact("Buy All");
					final Timer buyAllTimer = new Timer(Random.nextInt(2500,
							3500));
					while (buyAllTimer.isRunning()
							&& PlankMenu.getComponent(WIDGET_CHOSEN)
									.isOnScreen())
						sleep(Random.nextInt(50, 75));
				}
			}

		}
	}

	private void Bank() {
		if (!nearBanker()) {
			status = "Walk to bank";
			ctx.movement.newTilePath(WALK_TO_OPERATOR_PATH).reverse()
					.traverse();
		} else {
			if (!ctx.bank.isOpen()) {
				status = "Bank open";
				ctx.bank.open();
				sleep(Random.nextInt(500, 750));
			} else {
				Banking();
			}
		}

	}

	private void Banking() {
		status = "Banking...";
		Item Logs = ctx.bank.select().id(LOG_CHOSEN).first().isEmpty() ? null
				: ctx.bank.iterator().next();

		if (!ctx.bank.select().contains(Logs)) {
			ctx.bank.close();
			getController().stop();
		} else {
			if (ctx.backpack.select().count() == 28) {
				if (InventoryContainsPotions() && USE_POTIONS) {
					deposit(0, ALL_PLANK_ID);
				} else {
					ctx.bank.depositInventory();
					ctimer(!ctx.backpack.isEmpty(), 0, 0);
				}
			} else {
				if (BankContainsTeletab() && !InventoryContainsTeletab()
						&& USE_TELETABS) {
					ctx.bank.withdraw(VARROCK_TELEPORT_ID, 28);
				} else if (BankContainsPotions() && !InventoryContainsPotions()
						&& USE_POTIONS) {
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
		final Item VarrockTeleport = ctx.backpack.select()
				.id(VARROCK_TELEPORT_ID).first().isEmpty() ? null
				: ctx.backpack.iterator().next();
		if (nearMillOperator()) {
			status = "Use teletab";
			if (VarrockTeleport != null) {
				VarrockTeleport.interact("Break");
				ctimer(nearMillOperator(), 4300, 4500);
			}
		} else {
			if (!nearBanker()) {
				status = "Walk to bank";
				ctx.movement.newTilePath(WALK_TO_OPERATOR_PATH).reverse()
						.traverse();
			} else {
				if (!ctx.bank.isOpen()) {
					status = "Bank open";
					ctx.bank.open();

				} else {
					Banking();
				}
			}
		}

	}

	private boolean potionUsable() {
		return ctx.movement.getEnergyLevel() < 51 && !ctx.bank.isOpen()
				&& !ctx.widgets.get(WIDGET_PLANK_MENU).isValid()
				&& InventoryContainsPotions()
				&& ctx.players.local().isInMotion() && USE_POTIONS;
	}

	private boolean nearBanker() {
		final Npc Banker = ctx.npcs.select().id(BANKER_ID).first().isEmpty() ? null
				: ctx.npcs.iterator().next();
		return Banker != null && Banker.isOnScreen()
				&& ctx.players.local().getLocation().distanceTo(Banker) < 7;
	}

	public boolean withdraw(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.withdraw(i, count)) {
				// System.out.println("Succesfully withdrew all of " + i);
				break;
			}
		}
		return true;
	}

	public boolean deposit(final int count, final int... items) {
		for (int i : items) {
			if (ctx.bank.deposit(i, count)) {
				// System.out.println("Succesfully deposited all of " + i);
				break;
			}
		}
		return true;
	}

	private boolean BankContainsTeletab() {
		Item Tab = ctx.bank.select().id(VARROCK_TELEPORT_ID).first().isEmpty() ? null
				: ctx.bank.iterator().next();
		return ctx.bank.select().contains(Tab);
	}

	private boolean InventoryContainsTeletab() {
		Item Tab = ctx.backpack.select().id(VARROCK_TELEPORT_ID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(Tab);
	}

	private boolean BankContainsPotions() {
		Item Potions = ctx.bank.select().id(ENERGY_POTION_ID).first().isEmpty() ? null
				: ctx.bank.iterator().next();
		return ctx.bank.select().contains(Potions);
	}

	private boolean InventoryContainsPotions() {
		Item Potions = ctx.backpack.select().id(ENERGY_POTION_ID).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(Potions);
	}

	private boolean InventoryContainsLogs() {
		Item LogsChosen = ctx.backpack.select().id(LOG_CHOSEN).first()
				.isEmpty() ? null : ctx.backpack.iterator().next();
		return ctx.backpack.select().contains(LogsChosen);
	}

	private boolean nearMillOperator() {
		final Npc Planker = ctx.npcs.select().id(OPERATOR_ID).first().isEmpty() ? null
				: ctx.npcs.iterator().next();

		return Planker != null && Planker.isOnScreen();
	}

	private void ctimer(boolean wait4, int int1, int int2) {
		if (int1 == 0 || int2 == 0) {
			int1 = 1200;
			int2 = 1500;
		}
		WAIT = new Timer(Random.nextInt(int1, int2));
		while (WAIT.isRunning() && wait4) {
		sleep(Random.nextInt(5, 10));
		}
	}

	@Override
	public void messaged(MessageEvent msg) {
		// TODO Auto-generated method stub
		String message = msg.getMessage();
		if (message.contains(" coins have been removed from your money pouch")) {
			PLANKS_GAINED++;
		}
	}

	@Override
	public void repaint(Graphics g) {
		// TODO Auto-generated method stub
		final Color Black = new Color(0, 0, 0, 200);
		final Font Font = new Font("Times New Roman", 0, 13);
		final Font FontTwo = new Font("Arial", 1, 12);
		final NumberFormat nf = new DecimalFormat("###,###,###,###");

		g.setColor(Black);
		g.fillRoundRect(340, 65, 175, 125, 10, 10);
		g.drawRoundRect(340, 65, 175, 125, 10, 10);
		g.setFont(FontTwo);
		g.setColor(Color.GREEN);
		g.drawString("rPlanker", 395, 78);
		g.setFont(Font);
		g.setColor(Color.WHITE);
		g.drawString("Runtime: " + ELAPSED_TIMER.toElapsedString(), 351, 100);
		g.drawString("Planks Made: " + nf.format(PLANKS_GAINED) + "("
				+ perHour(PLANKS_GAINED) + "/h)", 351, 120);
		g.drawString("Profit: " + nf.format(profitGained()) + "("
				+ perHour(profitGained()) + "/h)", 351, 140);
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

	private String perHour(long arg) {
		return NumberFormat.getIntegerInstance().format(
				arg * 3600000D / (ELAPSED_TIMER.getElapsed()));
	}

	private static int getGuidePrice(final int id) {
		final String add = "http://scriptwith.us/api/?return=text&item=" + id;
		try (final BufferedReader in = new BufferedReader(
				new InputStreamReader(new URL(add).openConnection()
						.getInputStream()))) {
			final String line = in.readLine();
			return Integer.parseInt(line.split("[:]")[1]);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	public class rPlankerGUI extends JFrame {
		private static final long serialVersionUID = 1L;

		public rPlankerGUI() {
			initComponents();
		}

		private void startButtonActionPerformed(ActionEvent e) {
			String chosen = PlanksToMake.getSelectedItem().toString();
			/* User has chose to use energy potions */
			if (checkBox1.isSelected()) {
				USE_POTIONS = true;
			}
			/* User has chose to use varrock teletabs */
			if (checkBox2.isSelected()) {
				USE_TELETABS = true;
			}
			/* Normal logs */
			if (chosen.equals("Normal")) {
				LOG_CHOSEN = NORMAL_LOG_ID;
				WIDGET_CHOSEN = WIDGET_MAKE_NORMAL_PLANKS;
				PLANK_PRICE = getGuidePrice(NORMAL_PLANK_ID) - 100;
				LOG_PRICE = getGuidePrice(LOG_CHOSEN);
				PROFIT_TOTAL = PLANK_PRICE - LOG_PRICE;
				POUCH_AMOUNT = 2800;
				/* Oak logs */
			} else if (chosen.equals("Oak")) {
				LOG_CHOSEN = OAK_LOG_ID;
				WIDGET_CHOSEN = WIDGET_MAKE_OAK_PLANKS;
				PLANK_PRICE = getGuidePrice(OAK_PLANK_ID) - 250;
				LOG_PRICE = getGuidePrice(LOG_CHOSEN);
				PROFIT_TOTAL = PLANK_PRICE - LOG_PRICE;
				POUCH_AMOUNT = 7000;
				/* Teak logs */
			} else if (chosen.equals("Teak")) {
				LOG_CHOSEN = TEAK_LOG_ID;
				WIDGET_CHOSEN = WIDGET_MAKE_TEAK_PLANKS;
				PLANK_PRICE = getGuidePrice(TEAK_PLANK_ID) - 500;
				LOG_PRICE = getGuidePrice(LOG_CHOSEN);
				PROFIT_TOTAL = PLANK_PRICE - LOG_PRICE;
				POUCH_AMOUNT = 14000;
				/* Mahogany logs */
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
			PlanksToMake = new JComboBox<>();
			startButton = new JButton();
			checkBox1 = new JCheckBox();
			checkBox2 = new JCheckBox();

			// ======== this ========
			setTitle("rPlanker GUI");
			Container contentPane = getContentPane();
			contentPane.setLayout(null);

			// ---- ScriptTitle ----
			ScriptTitle.setText("rPlanker Setup");
			ScriptTitle.setFont(new Font("Segoe Print", Font.BOLD, 14));
			contentPane.add(ScriptTitle);
			ScriptTitle.setBounds(35, 5, 110,
					ScriptTitle.getPreferredSize().height);

			// ---- TypeOfPlankLabel ----
			TypeOfPlankLabel.setText("Type of plank:");
			TypeOfPlankLabel.setFont(TypeOfPlankLabel.getFont().deriveFont(
					TypeOfPlankLabel.getFont().getStyle() | Font.BOLD));
			contentPane.add(TypeOfPlankLabel);
			TypeOfPlankLabel.setBounds(new Rectangle(new Point(0, 50),
					TypeOfPlankLabel.getPreferredSize()));

			// ---- PlanksToMake ----
			PlanksToMake.setModel(new DefaultComboBoxModel<>(new String[] {
					"Normal", "Oak", "Teak", "Mahogany" }));
			contentPane.add(PlanksToMake);
			PlanksToMake.setBounds(85, 45, 100,
					PlanksToMake.getPreferredSize().height);

			// ---- startButton ----
			startButton.setText("Start");
			startButton.setFont(startButton.getFont().deriveFont(
					startButton.getFont().getStyle() | Font.BOLD));
			startButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					startButtonActionPerformed(e);
				}
			});
			contentPane.add(startButton);
			startButton.setBounds(new Rectangle(new Point(60, 105), startButton
					.getPreferredSize()));

			// ---- checkBox1 ----
			checkBox1.setText("Use potions?");
			checkBox1.setFont(checkBox1.getFont().deriveFont(
					checkBox1.getFont().getStyle() | Font.BOLD));
			contentPane.add(checkBox1);
			checkBox1.setBounds(new Rectangle(new Point(0, 75), checkBox1
					.getPreferredSize()));

			// ---- checkBox2 ----
			checkBox2.setText("Use teletabs?");
			checkBox2.setFont(checkBox2.getFont().deriveFont(
					checkBox2.getFont().getStyle() | Font.BOLD));
			contentPane.add(checkBox2);
			checkBox2.setBounds(new Rectangle(new Point(95, 75), checkBox2
					.getPreferredSize()));

			{
				Dimension preferredSize = new Dimension();
				for (int i = 0; i < contentPane.getComponentCount(); i++) {
					Rectangle bounds = contentPane.getComponent(i).getBounds();
					preferredSize.width = Math.max(bounds.x + bounds.width,
							preferredSize.width);
					preferredSize.height = Math.max(bounds.y + bounds.height,
							preferredSize.height);
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
