import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.Random;

public class Main extends JFrame {

    public Main() {
        setTitle("Dragon Slayer RPG - Level Progression");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel gamePanel = new GamePanel();
        add(gamePanel);
        pack();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main());
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener {

    public static final int WIDTH = 1000;
    public static final int HEIGHT = 600;
    public static final int GROUND_Y = 480;

    private Timer gameTimer;
    private Random random = new Random();

    private Image backgroundArena;

    // Game Progression
    private int currentLevel = 1;

    // Input States
    private boolean keyLeft = false;
    private boolean keyRight = false;
    private boolean keySpace = false;
    private boolean keyCraft1 = false;
    private boolean keyCraft2 = false;
    private boolean keyRestart = false;

    // Overlay State
    private boolean showInventory = false;

    // Game Entities
    private Player player;
    private Dragon dragon;
    private enum GameState {
        PLAYING, GAME_OVER, LEVEL_CLEAR
    }

    private GameState currentState = GameState.PLAYING;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(20, 24, 35));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);

        loadAssets();

        // Initial setup for Level 1
        player = new Player(80, GROUND_Y - 80);
        startLevel(1);

        SwingUtilities.invokeLater(this::requestFocusInWindow);

        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    private void loadAssets() {
        backgroundArena = new ImageIcon("sprites/DragonArenaScaled.png").getImage();
    }

    private void startLevel(int lvl) {
        currentLevel = lvl;
        currentState = GameState.PLAYING;

        // Reset Player Position & Heal on New Level
        player.x = 80;
        player.y = GROUND_Y - 80;
        player.hp = player.maxHp;

        // Scale Dragon HP and Damage based on level
        dragon = new Dragon(700, GROUND_Y - 150);
        dragon.hp = 250 + (lvl * 100);
        dragon.maxHp = dragon.hp;

    }

    private void resetGameFull() {
        Weapon savedWeapon = Weapon.WOODEN_CLUB;
        player = new Player(80, GROUND_Y - 80);
        player.equipWeapon(savedWeapon);
        startLevel(1);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (currentState == GameState.PLAYING) {
            updatePlayer();
            updateDragon();
        } else if (currentState == GameState.LEVEL_CLEAR) {
            if (keyRestart) {
                startLevel(currentLevel + 1);
                keyRestart = false;
            }
        } else if (currentState == GameState.GAME_OVER) {
            if (keyRestart) {
                resetGameFull();
                keyRestart = false;
            }
        }
        repaint();
    }

    private void updatePlayer() {
        player.update(WIDTH);

        if (keyLeft && !showInventory) {
            player.moveLeft();
        } else if (keyRight && !showInventory) {
            player.moveRight();
        } else {
            player.stop();
        }

        if (keySpace && player.canAttack() && !showInventory) {
            performPlayerAttack();
        }

        if (showInventory) {
            if (keyCraft1) {
                craftItem(1);
                keyCraft1 = false;
            }
            if (keyCraft2) {
                craftItem(2);
                keyCraft2 = false;
            }
        }
    }

    private void performPlayerAttack() {
        player.triggerAttack();

        // Calculate attack range using fixed coordinates without altering player
        // dimensions
        int attackRange = player.getEquippedWeapon().range;
        int playerRightEdge = player.x + player.width;
        int dragonRightEdge = dragon.x + dragon.width;

        if (dragon.isAlive() && (playerRightEdge + attackRange >= dragon.x) && (player.x <= dragonRightEdge)) {
            int damage = player.getEquippedWeapon().damage + random.nextInt(8);
            dragon.takeDamage(damage);
            if (!dragon.isAlive()) {
                handleBossDefeat();
            }
        }
    }

    private void handleBossDefeat() {
        currentState = GameState.LEVEL_CLEAR;
    }

    private void craftItem(int choice) {
        Map<String, Integer> inv = player.inventory;
        if (choice == 1) {
            if (inv.getOrDefault("Wood", 0) >= 4 && inv.getOrDefault("Iron", 0) >= 4) {
                inv.put("Wood", inv.get("Wood") - 4);
                inv.put("Iron", inv.get("Iron") - 4);
                player.equipWeapon(Weapon.IRON_SWORD);
            } else {
            }
        } else if (choice == 2) {
            if (inv.getOrDefault("Iron", 0) >= 8 && inv.getOrDefault("Crystal", 0) >= 5) {
                inv.put("Iron", inv.get("Iron") - 8);
                inv.put("Crystal", inv.get("Crystal") - 5);
                player.equipWeapon(Weapon.DRAGON_SPEAR);
            } else {
            }
        }
    }

    private void updateDragon() {
        dragon.update();

        if (dragon.isAttackingClose() && Math.abs(player.getCenterX() - dragon.x) < 80) {
            if (dragon.damagePlayerTimer <= 0) {
                int baseDamage = 15 + (currentLevel * 3);
                player.takeDamage(baseDamage);
                dragon.damagePlayerTimer = 40;
                if (!player.isAlive())
                    currentState = GameState.GAME_OVER;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Arena Background
        if (backgroundArena != null) {
            g2d.drawImage(backgroundArena, 0, 0, WIDTH, HEIGHT, this);
        } else {
            drawEnvironmentFallback(g2d);
        }

        // Draw Dragon Sprite
        if (dragon.isAlive() && dragon.getSprite() != null) {
            g2d.drawImage(dragon.getSprite(), dragon.x, dragon.y, dragon.width, dragon.height, this);
        }

        // Draw Player Sprite Animation
        if (player.isAlive()) {
            Image playerImg = player.getCurrentImage();
            if (playerImg != null) {
                g2d.drawImage(playerImg, player.x + player.getCurrentImageXOffset(), player.y,
                    playerImg.getWidth(this), playerImg.getHeight(this), this);
            } else {
                // Fallback if image fails to load
                g2d.setColor(Color.BLUE);
                g2d.fillRect(player.x, player.y, player.width, player.height);
            }
        }

        drawHUD(g2d);

        if (showInventory) {
            drawInventoryOverlay(g2d);
        }

        if (currentState != GameState.PLAYING) {
            drawEndScreen(g2d);
        }
    }

    private void drawEnvironmentFallback(Graphics2D g2) {
        GradientPaint skyGradient = new GradientPaint(0, 0, new Color(15, 20, 35), 0, GROUND_Y, new Color(40, 30, 50));
        g2.setPaint(skyGradient);
        g2.fillRect(0, 0, WIDTH, GROUND_Y);

        GradientPaint groundGradient = new GradientPaint(0, GROUND_Y, new Color(60, 45, 35), 0, HEIGHT,
                new Color(25, 18, 12));
        g2.setPaint(groundGradient);
        g2.fillRect(0, GROUND_Y, WIDTH, HEIGHT - GROUND_Y);
    }

    private void drawHUD(Graphics2D g2) {
        // Player HUD
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(15, 15, 260, 75, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("HERO HEALTH - LEVEL " + currentLevel, 25, 32);

        g2.setColor(Color.DARK_GRAY);
        g2.fillRoundRect(25, 38, 200, 16, 8, 8);
        float hpPercent = (float) player.hp / player.maxHp;
        g2.setColor(hpPercent > 0.4f ? Color.GREEN : Color.RED);
        g2.fillRoundRect(25, 38, (int) (200 * hpPercent), 16, 8, 8);

        g2.setColor(Color.WHITE);
        g2.drawString(player.hp + " / " + player.maxHp, 105, 51);
        g2.drawString("Weapon: " + player.getEquippedWeapon().name + " (" + player.getEquippedWeapon().damage + " DMG)",
                25, 75);

        // Dragon HUD
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(350, 15, 300, 55, 12, 12);
        g2.setColor(Color.RED);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString("DRAGON BOSS (STAGE " + currentLevel + ")", 410, 32);

        g2.setColor(Color.DARK_GRAY);
        g2.fillRoundRect(370, 38, 260, 18, 8, 8);
        float dragonHpPercent = Math.max(0, (float) dragon.hp / dragon.maxHp);
        g2.setColor(new Color(220, 40, 40));
        g2.fillRoundRect(370, 38, (int) (260 * dragonHpPercent), 18, 8, 8);

        // Control Hints
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, HEIGHT - 35, WIDTH, 35);
        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString("Controls: [A/D or Arrows] Move | [SPACE] Attack | [I] Inventory & Forge", 20, HEIGHT - 12);
    }

    private void drawInventoryOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        int panelW = 550, panelH = 340;
        int panelX = (WIDTH - panelW) / 2;
        int panelY = (HEIGHT - panelH) / 2;

        g2.setColor(new Color(35, 42, 60));
        g2.fillRoundRect(panelX, panelY, panelW, panelH, 15, 15);
        g2.setColor(new Color(100, 130, 180));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(panelX, panelY, panelW, panelH, 15, 15);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.drawString("INVENTORY & FORGE", panelX + 160, panelY + 40);

        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(Color.WHITE);
        g2.drawString("Defeated Boss Drops Stockpile:", panelX + 30, panelY + 80);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.drawString("- Wood: " + player.inventory.getOrDefault("Wood", 0), panelX + 40, panelY + 110);
        g2.drawString("- Iron: " + player.inventory.getOrDefault("Iron", 0), panelX + 180, panelY + 110);
        g2.drawString("- Crystals: " + player.inventory.getOrDefault("Crystal", 0), panelX + 320, panelY + 110);

        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Craft Next Tier Weapon:", panelX + 30, panelY + 155);

        drawRecipeBox(g2, panelX + 30, panelY + 170, "[1] Iron Sword", "4 Wood, 4 Iron", "+35 Damage",
                player.getEquippedWeapon() == Weapon.IRON_SWORD);
        drawRecipeBox(g2, panelX + 30, panelY + 230, "[2] Dragon Spear", "8 Iron, 5 Crystals", "+70 Damage",
                player.getEquippedWeapon() == Weapon.DRAGON_SPEAR);

        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("SansSerif", Font.ITALIC, 13));
        g2.drawString("Press [I] to Close Inventory", panelX + 180, panelY + 315);
    }

    private void drawRecipeBox(Graphics2D g2, int x, int y, String name, String reqs, String effect, boolean equipped) {
        g2.setColor(new Color(20, 25, 38));
        g2.fillRoundRect(x, y, 490, 50, 8, 8);
        g2.setColor(equipped ? Color.GREEN : Color.GRAY);
        g2.drawRoundRect(x, y, 490, 50, 8, 8);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString(name + (equipped ? " (Equipped)" : ""), x + 15, y + 22);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(Color.ORANGE);
        g2.drawString("Requires: " + reqs, x + 15, y + 40);

        g2.setColor(Color.CYAN);
        g2.drawString("Effect: " + effect, x + 280, y + 30);
    }

    private void drawEndScreen(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        g2.setFont(new Font("SansSerif", Font.BOLD, 36));
        if (currentState == GameState.LEVEL_CLEAR) {
            g2.setColor(Color.GREEN);
            g2.drawString("STAGE " + currentLevel + " CLEARED!", 320, 240);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g2.setColor(Color.WHITE);
            g2.drawString("The boss dropped crafting materials! Open [I] Inventory to upgrade.", 220, 290);
            g2.setColor(Color.YELLOW);
            g2.drawString("Press [R] to Start Stage " + (currentLevel + 1), 370, 360);
        } else {
            g2.setColor(Color.RED);
            g2.drawString("GAME OVER", 390, 240);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g2.setColor(Color.WHITE);
            g2.drawString("You reached Stage " + currentLevel + ".", 400, 290);
            g2.setColor(Color.YELLOW);
            g2.drawString("Press [R] to Restart From Level 1", 340, 360);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT)
            keyLeft = true;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT)
            keyRight = true;
        if (code == KeyEvent.VK_SPACE)
            keySpace = true;
        if (code == KeyEvent.VK_I || code == KeyEvent.VK_E)
            showInventory = !showInventory;
        if (code == KeyEvent.VK_1)
            keyCraft1 = true;
        if (code == KeyEvent.VK_2)
            keyCraft2 = true;
        if (code == KeyEvent.VK_R)
            keyRestart = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT)
            keyLeft = false;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT)
            keyRight = false;
        if (code == KeyEvent.VK_SPACE)
            keySpace = false;
        if (code == KeyEvent.VK_1)
            keyCraft1 = false;
        if (code == KeyEvent.VK_2)
            keyCraft2 = false;
        if (code == KeyEvent.VK_R)
            keyRestart = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}

enum Weapon {
    WOODEN_CLUB("Wooden Club", 10, 50),
    IRON_SWORD("Iron Sword", 35, 65),
    DRAGON_SPEAR("Dragon Spear", 70, 95);

    public final String name;
    public final int damage;
    public final int range;

    Weapon(String name, int damage, int range) {
        this.name = name;
        this.damage = damage;
        this.range = range;
    }
}