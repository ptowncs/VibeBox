import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
    private List<LootDrop> lootDrops = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    private List<FloatingText> floatingTexts = new ArrayList<>();
    private List<Fireball> fireballs = new ArrayList<>();

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

        lootDrops.clear();
        particles.clear();
        floatingTexts.clear();
        fireballs.clear();
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
            updateFireballs();
            updateLootDrops();
            updateParticles();
            updateFloatingTexts();
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
            floatingTexts.add(new FloatingText("-" + damage, dragon.x + 30, dragon.y + 40, Color.RED));
            spawnParticles(dragon.x + 20, dragon.y + 60, Color.YELLOW, 10);

            if (!dragon.isAlive()) {
                handleBossDefeat();
            }
        }
    }

    private void handleBossDefeat() {
        currentState = GameState.LEVEL_CLEAR;
        spawnParticles(dragon.x + 80, dragon.y + 60, Color.MAGENTA, 40);

        // Drop materials where the dragon dies
        int woodDrop = 3 + random.nextInt(4);
        int ironDrop = 2 + random.nextInt(4);
        int crystalDrop = 1 + random.nextInt(3);

        lootDrops.add(new LootDrop("Wood", woodDrop, dragon.x + 20, GROUND_Y - 20));
        lootDrops.add(new LootDrop("Iron", ironDrop, dragon.x + 70, GROUND_Y - 20));
        lootDrops.add(new LootDrop("Crystal", crystalDrop, dragon.x + 120, GROUND_Y - 20));
    }

    private void updateLootDrops() {
        Iterator<LootDrop> it = lootDrops.iterator();
        while (it.hasNext()) {
            LootDrop drop = it.next();
            if (Math.abs(player.getCenterX() - drop.x) < 40) {
                player.inventory.put(drop.type, player.inventory.getOrDefault(drop.type, 0) + drop.amount);
                floatingTexts.add(
                        new FloatingText("+" + drop.amount + " " + drop.type, player.x, player.y - 20, Color.GREEN));
                spawnParticles(drop.x, drop.y, Color.CYAN, 8);
                it.remove();
            }
        }
    }

    private void craftItem(int choice) {
        Map<String, Integer> inv = player.inventory;
        if (choice == 1) {
            if (inv.getOrDefault("Wood", 0) >= 4 && inv.getOrDefault("Iron", 0) >= 4) {
                inv.put("Wood", inv.get("Wood") - 4);
                inv.put("Iron", inv.get("Iron") - 4);
                player.equipWeapon(Weapon.IRON_SWORD);
                floatingTexts.add(new FloatingText("Crafted Iron Sword!", player.x, player.y - 20, Color.CYAN));
            } else {
                floatingTexts.add(new FloatingText("Missing Materials!", player.x, player.y - 20, Color.ORANGE));
            }
        } else if (choice == 2) {
            if (inv.getOrDefault("Iron", 0) >= 8 && inv.getOrDefault("Crystal", 0) >= 5) {
                inv.put("Iron", inv.get("Iron") - 8);
                inv.put("Crystal", inv.get("Crystal") - 5);
                player.equipWeapon(Weapon.DRAGON_SPEAR);
                floatingTexts.add(new FloatingText("Crafted Dragon Spear!", player.x, player.y - 20, Color.MAGENTA));
            } else {
                floatingTexts.add(new FloatingText("Missing Materials!", player.x, player.y - 20, Color.ORANGE));
            }
        }
    }

    private void updateDragon() {
        dragon.update();

        if (dragon.shouldLaunchFireball()) {
            fireballs.add(new Fireball(dragon.x - 20, dragon.y + 40, -7.5f));
            spawnParticles(dragon.x - 20, dragon.y + 40, Color.ORANGE, 8);
        }

        if (dragon.isAttackingClose() && Math.abs(player.getCenterX() - dragon.x) < 80) {
            if (dragon.damagePlayerTimer <= 0) {
                int baseDamage = 15 + (currentLevel * 3);
                player.takeDamage(baseDamage);
                floatingTexts.add(new FloatingText("-" + baseDamage, player.x, player.y - 10, Color.RED));
                spawnParticles(player.x, player.y + 20, Color.RED, 8);
                dragon.damagePlayerTimer = 40;
                if (!player.isAlive())
                    currentState = GameState.GAME_OVER;
            }
        }
    }

    private void updateFireballs() {
        Iterator<Fireball> it = fireballs.iterator();
        while (it.hasNext()) {
            Fireball fb = it.next();
            fb.update();
            spawnParticles((int) fb.x, (int) fb.y, Color.ORANGE, 1);

            if (Math.abs(fb.x - player.getCenterX()) < 30 && Math.abs(fb.y - (player.y + 30)) < 35) {
                int fireballDamage = 18 + (currentLevel * 4);
                player.takeDamage(fireballDamage);
                floatingTexts.add(new FloatingText("-" + fireballDamage, player.x, player.y - 10, Color.RED));
                spawnParticles((int) fb.x, (int) fb.y, Color.RED, 12);
                it.remove();
                if (!player.isAlive())
                    currentState = GameState.GAME_OVER;
                continue;
            }

            if (fb.x < 0)
                it.remove();
        }
    }

    private void updateParticles() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (p.isDead())
                it.remove();
        }
    }

    private void updateFloatingTexts() {
        Iterator<FloatingText> it = floatingTexts.iterator();
        while (it.hasNext()) {
            FloatingText ft = it.next();
            ft.update();
            if (ft.isDead())
                it.remove();
        }
    }

    private void spawnParticles(int x, int y, Color color, int count) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, color));
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

        // Draw Loot Drops
        for (LootDrop drop : lootDrops) {
            drop.draw(g2d);
        }

        // Draw Dragon Sprite
        if (dragon.isAlive() && dragon.getSprite() != null) {
            g2d.drawImage(dragon.getSprite(), dragon.x, dragon.y, dragon.width, dragon.height, this);
        }

        // Draw Player Sprite Animation
        if (player.isAlive()) {
            Image playerImg = player.getCurrentImage();
            if (playerImg != null) {
                g2d.drawImage(playerImg, player.x, player.y, null);
            } else {
                // Fallback if image fails to load
                g2d.setColor(Color.BLUE);
                g2d.fillRect(player.x, player.y, player.width, player.height);
            }
        }

        for (Fireball fb : fireballs)
            fb.draw(g2d);
        for (Particle p : particles)
            p.draw(g2d);
        for (FloatingText ft : floatingTexts)
            ft.draw(g2d);

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

class LootDrop {
    public String type;
    public int amount;
    public int x, y;

    public LootDrop(String type, int amount, int x, int y) {
        this.type = type;
        this.amount = amount;
        this.x = x;
        this.y = y;
    }

    public void draw(Graphics2D g2) {
        g2.setColor(Color.YELLOW);
        g2.fillOval(x, y, 16, 16);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.drawString(type.substring(0, 1), x + 5, y + 12);
    }
}

class Fireball {
    public float x, y;
    public float vx;

    public Fireball(float x, float y, float vx) {
        this.x = x;
        this.y = y;
        this.vx = vx;
    }

    public void update() {
        x += vx;
    }

    public void draw(Graphics2D g2) {
        g2.setColor(Color.ORANGE);
        g2.fillOval((int) x, (int) y, 18, 18);
        g2.setColor(Color.RED);
        g2.fillOval((int) x + 3, (int) y + 3, 12, 12);
    }
}

class Particle {
    public float x, y, vx, vy;
    public Color color;
    public int life = 20;

    public Particle(int x, int y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
        Random r = new Random();
        this.vx = (r.nextFloat() - 0.5f) * 6;
        this.vy = (r.nextFloat() - 0.5f) * 6;
    }

    public void update() {
        x += vx;
        y += vy;
        life--;
    }

    public boolean isDead() {
        return life <= 0;
    }

    public void draw(Graphics2D g2) {
        g2.setColor(color);
        g2.fillRect((int) x, (int) y, 4, 4);
    }
}

class FloatingText {
    public String text;
    public float x, y;
    public Color color;
    public int life = 35;

    public FloatingText(String text, float x, float y, Color color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public void update() {
        y -= 0.8f;
        life--;
    }

    public boolean isDead() {
        return life <= 0;
    }

    public void draw(Graphics2D g2) {
        g2.setColor(color);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString(text, x, y);
    }
}