import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dragon Slayer RPG - Main Application Window
 * Designed for single-file execution in Main.java
 * Run directly with: java Main.java
 * Or compile: javac Main.java && java Main
 */
public class Main extends JFrame {

    public Main() {
        setTitle("Dragon Slayer RPG - Forge & Battle");
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

/**
 * Main game panel handling rendering, key listener inputs, and game state loops.
 */
class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener {

    public static final int WIDTH = 1000;
    public static final int HEIGHT = 600;
    public static final int GROUND_Y = 480;

    private Timer gameTimer;
    private Random random = new Random();

    // Input States
    private boolean keyLeft = false;
    private boolean keyRight = false;
    private boolean keySpace = false;
    private boolean keyCraft1 = false;
    private boolean keyCraft2 = false;
    private boolean keyCraft3 = false;
    private boolean keyPotion = false;
    private boolean keyRestart = false;

    // Overlay State
    private boolean showInventory = false;

    // Game Entities
    private Player player;
    private Dragon dragon;
    private List<ResourceNode> resourceNodes = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    private List<FloatingText> floatingTexts = new ArrayList<>();
    private List<Fireball> fireballs = new ArrayList<>();

    private enum GameState { PLAYING, GAME_OVER, VICTORY }
    private GameState currentState = GameState.PLAYING;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(20, 24, 35));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);

        initGame();

        // Request keyboard focus immediately on window launch
        SwingUtilities.invokeLater(this::requestFocusInWindow);

        // 60 FPS Game Loop (~16 ms per frame)
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    private void initGame() {
        player = new Player(80, GROUND_Y - 50);
        dragon = new Dragon(760, GROUND_Y - 140);
        currentState = GameState.PLAYING;

        // Initialize resource nodes across the arena
        resourceNodes.clear();
        resourceNodes.add(new ResourceNode(ResourceNode.Type.TREE, 180, GROUND_Y - 60));
        resourceNodes.add(new ResourceNode(ResourceNode.Type.IRON_ORE, 320, GROUND_Y - 40));
        resourceNodes.add(new ResourceNode(ResourceNode.Type.CRYSTAL, 460, GROUND_Y - 50));
        resourceNodes.add(new ResourceNode(ResourceNode.Type.TREE, 580, GROUND_Y - 60));

        particles.clear();
        floatingTexts.clear();
        fireballs.clear();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (currentState == GameState.PLAYING) {
            updatePlayer();
            updateDragon();
            updateFireballs();
            updateParticles();
            updateFloatingTexts();
            checkInteractions();
        } else {
            if (keyRestart) {
                initGame();
            }
        }
        repaint();
    }

    private void updatePlayer() {
        player.update();

        if (keyLeft && !showInventory) {
            player.moveLeft();
        } else if (keyRight && !showInventory) {
            player.moveRight();
        } else {
            player.stop();
        }

        if (keySpace && player.canAttack() && !showInventory) {
            performPlayerAction();
        }

        if (keyPotion && player.potionCooldown <= 0) {
            usePotion();
            keyPotion = false;
        }

        if (showInventory) {
            if (keyCraft1) { craftItem(1); keyCraft1 = false; }
            if (keyCraft2) { craftItem(2); keyCraft2 = false; }
            if (keyCraft3) { craftItem(3); keyCraft3 = false; }
        }
    }

    private void performPlayerAction() {
        player.triggerAttack();

        boolean harvested = false;
        for (ResourceNode node : resourceNodes) {
            if (node.isAlive() && Math.abs(player.getCenterX() - node.getCenterX()) < 65) {
                String resourceName = node.harvest();
                if (resourceName != null) {
                    player.inventory.put(resourceName, player.inventory.getOrDefault(resourceName, 0) + 1);
                    floatingTexts.add(new FloatingText("+1 " + resourceName, node.getCenterX(), node.y - 10, Color.GREEN));
                    spawnParticles(node.getCenterX(), node.y + 20, Color.ORANGE, 8);
                    harvested = true;
                    break;
                }
            }
        }

        if (!harvested) {
            int attackRange = player.getEquippedWeapon().range;
            if (dragon.isAlive() && Math.abs(player.getRightX() - dragon.x) < attackRange) {
                int damage = player.getEquippedWeapon().damage + random.nextInt(6);
                dragon.takeDamage(damage);
                floatingTexts.add(new FloatingText("-" + damage, dragon.x + 30, dragon.y + 40, Color.RED));
                spawnParticles(dragon.x + 20, dragon.y + 60, Color.YELLOW, 12);

                if (!dragon.isAlive()) {
                    currentState = GameState.VICTORY;
                    spawnParticles(dragon.x + 80, dragon.y + 60, Color.MAGENTA, 50);
                }
            }
        }
    }

    private void craftItem(int choice) {
        Map<String, Integer> inv = player.inventory;
        if (choice == 1) {
            if (inv.getOrDefault("Wood", 0) >= 5 && inv.getOrDefault("Iron", 0) >= 5) {
                inv.put("Wood", inv.get("Wood") - 5);
                inv.put("Iron", inv.get("Iron") - 5);
                player.equipWeapon(Weapon.IRON_SWORD);
                floatingTexts.add(new FloatingText("Crafted Iron Sword!", player.x, player.y - 20, Color.CYAN));
            } else {
                floatingTexts.add(new FloatingText("Missing Materials!", player.x, player.y - 20, Color.ORANGE));
            }
        } else if (choice == 2) {
            if (inv.getOrDefault("Iron", 0) >= 10 && inv.getOrDefault("Crystal", 0) >= 6) {
                inv.put("Iron", inv.get("Iron") - 10);
                inv.put("Crystal", inv.get("Crystal") - 6);
                player.equipWeapon(Weapon.DRAGON_SPEAR);
                floatingTexts.add(new FloatingText("Crafted Dragon Spear!", player.x, player.y - 20, Color.MAGENTA));
            } else {
                floatingTexts.add(new FloatingText("Missing Materials!", player.x, player.y - 20, Color.ORANGE));
            }
        } else if (choice == 3) {
            if (inv.getOrDefault("Wood", 0) >= 2 && inv.getOrDefault("Crystal", 0) >= 2) {
                inv.put("Wood", inv.get("Wood") - 2);
                inv.put("Crystal", inv.get("Crystal") - 2);
                player.potions++;
                floatingTexts.add(new FloatingText("+1 Health Potion!", player.x, player.y - 20, Color.GREEN));
            } else {
                floatingTexts.add(new FloatingText("Missing Materials!", player.x, player.y - 20, Color.ORANGE));
            }
        }
    }

    private void usePotion() {
        if (player.potions > 0 && player.hp < player.maxHp) {
            player.potions--;
            player.hp = Math.min(player.maxHp, player.hp + 45);
            player.potionCooldown = 30;
            floatingTexts.add(new FloatingText("+45 HP", player.x, player.y - 20, Color.GREEN));
            spawnParticles(player.getCenterX(), player.y + 20, Color.GREEN, 15);
        }
    }

    private void updateDragon() {
        dragon.update();

        if (dragon.shouldLaunchFireball()) {
            fireballs.add(new Fireball(dragon.x - 20, dragon.y + 40, -7.5f));
            spawnParticles(dragon.x - 20, dragon.y + 40, Color.ORANGE, 10);
        }

        if (dragon.isAttackingClose() && Math.abs(player.getCenterX() - dragon.x) < 80) {
            if (dragon.damagePlayerTimer <= 0) {
                player.takeDamage(18);
                floatingTexts.add(new FloatingText("-18", player.x, player.y - 10, Color.RED));
                spawnParticles(player.x, player.y + 20, Color.RED, 10);
                dragon.damagePlayerTimer = 40;
                if (!player.isAlive()) currentState = GameState.GAME_OVER;
            }
        }
    }

    private void updateFireballs() {
        Iterator<Fireball> it = fireballs.iterator();
        while (it.hasNext()) {
            Fireball fb = it.next();
            fb.update();
            spawnParticles((int)fb.x, (int)fb.y, Color.ORANGE, 1);

            if (Math.abs(fb.x - player.getCenterX()) < 25 && Math.abs(fb.y - (player.y + 25)) < 30) {
                player.takeDamage(22);
                floatingTexts.add(new FloatingText("-22", player.x, player.y - 10, Color.RED));
                spawnParticles((int)fb.x, (int)fb.y, Color.RED, 15);
                it.remove();
                if (!player.isAlive()) currentState = GameState.GAME_OVER;
                continue;
            }

            if (fb.x < 0) it.remove();
        }
    }

    private void updateParticles() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (p.isDead()) it.remove();
        }
    }

    private void updateFloatingTexts() {
        Iterator<FloatingText> it = floatingTexts.iterator();
        while (it.hasNext()) {
            FloatingText ft = it.next();
            ft.update();
            if (ft.isDead()) it.remove();
        }
    }

    private void checkInteractions() {
        for (ResourceNode node : resourceNodes) {
            node.update();
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

        drawEnvironment(g2d);

        for (ResourceNode node : resourceNodes) {
            node.draw(g2d);
            if (node.isAlive() && Math.abs(player.getCenterX() - node.getCenterX()) < 65) {
                g2d.setColor(Color.YELLOW);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2d.drawString("[SPACE] Harvest", node.getCenterX() - 40, node.y - 18);
            }
        }

        dragon.draw(g2d);
        player.draw(g2d);

        for (Fireball fb : fireballs) fb.draw(g2d);
        for (Particle p : particles) p.draw(g2d);
        for (FloatingText ft : floatingTexts) ft.draw(g2d);

        drawHUD(g2d);

        if (showInventory) {
            drawInventoryOverlay(g2d);
        }

        if (currentState != GameState.PLAYING) {
            drawEndScreen(g2d);
        }
    }

    private void drawEnvironment(Graphics2D g2) {
        GradientPaint skyGradient = new GradientPaint(0, 0, new Color(15, 20, 35), 0, GROUND_Y, new Color(40, 30, 50));
        g2.setPaint(skyGradient);
        g2.fillRect(0, 0, WIDTH, GROUND_Y);

        g2.setColor(new Color(30, 25, 45));
        Polygon mountain1 = new Polygon(new int[]{0, 200, 400}, new int[]{GROUND_Y, 220, GROUND_Y}, 3);
        Polygon mountain2 = new Polygon(new int[]{300, 550, 800}, new int[]{GROUND_Y, 180, GROUND_Y}, 3);
        Polygon mountain3 = new Polygon(new int[]{650, 850, 1050}, new int[]{GROUND_Y, 250, GROUND_Y}, 3);
        g2.fill(mountain1);
        g2.fill(mountain2);
        g2.fill(mountain3);

        GradientPaint groundGradient = new GradientPaint(0, GROUND_Y, new Color(60, 45, 35), 0, HEIGHT, new Color(25, 18, 12));
        g2.setPaint(groundGradient);
        g2.fillRect(0, GROUND_Y, WIDTH, HEIGHT - GROUND_Y);

        g2.setColor(new Color(80, 120, 50));
        g2.fillRect(0, GROUND_Y, 650, 8);
        g2.setColor(new Color(180, 60, 30));
        g2.fillRect(650, GROUND_Y, 350, 8);
    }

    private void drawHUD(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(15, 15, 250, 75, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("HERO HEALTH", 25, 32);

        g2.setColor(Color.DARK_GRAY);
        g2.fillRoundRect(25, 38, 200, 16, 8, 8);
        float hpPercent = (float) player.hp / player.maxHp;
        g2.setColor(hpPercent > 0.4f ? Color.GREEN : Color.RED);
        g2.fillRoundRect(25, 38, (int) (200 * hpPercent), 16, 8, 8);

        g2.setColor(Color.WHITE);
        g2.drawString(player.hp + " / " + player.maxHp, 105, 51);
        g2.drawString("Weapon: " + player.getEquippedWeapon().name + " (" + player.getEquippedWeapon().damage + " DMG)", 25, 75);

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(350, 15, 300, 55, 12, 12);
        g2.setColor(Color.RED);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString("ANCIENT DRAGON BOSS", 420, 32);

        g2.setColor(Color.DARK_GRAY);
        g2.fillRoundRect(370, 38, 260, 18, 8, 8);
        float dragonHpPercent = Math.max(0, (float) dragon.hp / dragon.maxHp);
        g2.setColor(new Color(220, 40, 40));
        g2.fillRoundRect(370, 38, (int) (260 * dragonHpPercent), 18, 8, 8);

        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, HEIGHT - 35, WIDTH, 35);
        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString("Controls: [A/D or Arrows] Move | [SPACE] Attack / Mine | [I] Inventory & Crafting | [H] Drink Potion (" + player.potions + " left)", 20, HEIGHT - 12);
    }

    private void drawInventoryOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        int panelW = 550, panelH = 380;
        int panelX = (WIDTH - panelW) / 2;
        int panelY = (HEIGHT - panelH) / 2;

        g2.setColor(new Color(35, 42, 60));
        g2.fillRoundRect(panelX, panelY, panelW, panelH, 15, 15);
        g2.setColor(new Color(100, 130, 180));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(panelX, panelY, panelW, panelH, 15, 15);

        g2.setColor(Color.CYAN);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.drawString("INVENTORY & CRAFTING FORGE", panelX + 110, panelY + 40);

        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(Color.WHITE);
        g2.drawString("Raw Materials Collected:", panelX + 30, panelY + 80);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.drawString("• Wood: " + player.inventory.getOrDefault("Wood", 0), panelX + 40, panelY + 110);
        g2.drawString("• Iron Ore: " + player.inventory.getOrDefault("Iron", 0), panelX + 180, panelY + 110);
        g2.drawString("• Crystals: " + player.inventory.getOrDefault("Crystal", 0), panelX + 320, panelY + 110);

        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Available Recipes (Press Key to Craft):", panelX + 30, panelY + 155);

        drawRecipeBox(g2, panelX + 30, panelY + 170, "[1] Iron Sword", "5 Wood, 5 Iron", "+35 Damage", player.getEquippedWeapon() == Weapon.IRON_SWORD);
        drawRecipeBox(g2, panelX + 30, panelY + 230, "[2] Dragon Spear", "10 Iron, 6 Crystals", "+70 Damage (High Range)", player.getEquippedWeapon() == Weapon.DRAGON_SPEAR);
        drawRecipeBox(g2, panelX + 30, panelY + 290, "[3] Health Potion", "2 Wood, 2 Crystals", "Restores 45 HP", false);

        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("SansSerif", Font.ITALIC, 13));
        g2.drawString("Press [I] to Close Inventory", panelX + 180, panelY + 360);
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

        g2.setFont(new Font("SansSerif", Font.BOLD, 42));
        if (currentState == GameState.VICTORY) {
            g2.setColor(Color.GREEN);
            g2.drawString("VICTORY! THE DRAGON IS SLAIN!", 160, 260);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g2.setColor(Color.WHITE);
            g2.drawString("You successfully forged powerful weapons and saved the realm!", 210, 310);
        } else {
            g2.setColor(Color.RED);
            g2.drawString("GAME OVER - YOU WERE VANQUISHED", 130, 260);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g2.setColor(Color.WHITE);
            g2.drawString("Gather raw materials and craft stronger gear before attacking!", 200, 310);
        }

        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.YELLOW);
        g2.drawString("Press [R] to Restart", 410, 380);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) keyLeft = true;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) keyRight = true;
        if (code == KeyEvent.VK_SPACE) keySpace = true;
        if (code == KeyEvent.VK_I || code == KeyEvent.VK_E) showInventory = !showInventory;
        if (code == KeyEvent.VK_1) keyCraft1 = true;
        if (code == KeyEvent.VK_2) keyCraft2 = true;
        if (code == KeyEvent.VK_3) keyCraft3 = true;
        if (code == KeyEvent.VK_H) keyPotion = true;
        if (code == KeyEvent.VK_R) keyRestart = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) keyLeft = false;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) keyRight = false;
        if (code == KeyEvent.VK_SPACE) keySpace = false;
        if (code == KeyEvent.VK_1) keyCraft1 = false;
        if (code == KeyEvent.VK_2) keyCraft2 = false;
        if (code == KeyEvent.VK_3) keyCraft3 = false;
        if (code == KeyEvent.VK_H) keyPotion = false;
        if (code == KeyEvent.VK_R) keyRestart = false;
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); }
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
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

class Player {
    public int x, y;
    public int width = 36, height = 50;
    public int hp = 100, maxHp = 100;
    public int potions = 1;
    public int potionCooldown = 0;

    private float vx = 0;
    private int attackAnimTimer = 0;
    private int attackCooldown = 0;
    private Weapon equippedWeapon = Weapon.WOODEN_CLUB;
    public Map<String, Integer> inventory = new HashMap<>();

    public Player(int x, int y) {
        this.x = x;
        this.y = y;
        inventory.put("Wood", 0);
        inventory.put("Iron", 0);
        inventory.put("Crystal", 0);
    }

    public void update() {
        x += vx;
        if (x < 10) x = 10;
        if (x > GamePanel.WIDTH - 120) x = GamePanel.WIDTH - 120;

        if (attackAnimTimer > 0) attackAnimTimer--;
        if (attackCooldown > 0) attackCooldown--;
        if (potionCooldown > 0) potionCooldown--;
    }

    public void moveLeft() { vx = -4.5f; }
    public void moveRight() { vx = 4.5f; }
    public void stop() { vx = 0; }

    public boolean canAttack() { return attackCooldown <= 0; }

    public void triggerAttack() {
        attackAnimTimer = 12;
        attackCooldown = 18;
    }

    public void equipWeapon(Weapon w) { this.equippedWeapon = w; }
    public Weapon getEquippedWeapon() { return equippedWeapon; }

    public void takeDamage(int amt) { hp = Math.max(0, hp - amt); }
    public boolean isAlive() { return hp > 0; }

    public int getCenterX() { return x + width / 2; }
    public int getRightX() { return x + width; }

    public void draw(Graphics2D g2) {
        g2.setColor(new Color(50, 110, 210));
        g2.fillRoundRect(x, y + 15, width, height - 15, 8, 8);

        g2.setColor(new Color(240, 190, 150));
        g2.fillOval(x + 6, y, 24, 24);

        g2.setColor(Color.GRAY);
        g2.fillRect(x + 4, y - 2, 28, 10);

        if (attackAnimTimer > 0) {
            g2.setColor(Color.YELLOW);
            g2.setStroke(new BasicStroke(4));
            g2.drawArc(x + 20, y - 10, equippedWeapon.range, 60, -30, 90);
        } else {
            g2.setColor(Color.LIGHT_GRAY);
            g2.fillRect(x + width, y + 20, 15, 4);
        }
    }
}

class Dragon {
    public int x, y;
    public int width = 180, height = 140;
    public int hp = 350, maxHp = 350;

    public int attackTimer = 0;
    public int damagePlayerTimer = 0;
    private int wingFlapOffset = 0;

    public Dragon(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update() {
        attackTimer++;
        wingFlapOffset = (int)(Math.sin(System.currentTimeMillis() * 0.005) * 12);
        if (damagePlayerTimer > 0) damagePlayerTimer--;
    }

    public boolean shouldLaunchFireball() {
        if (attackTimer >= 140) {
            attackTimer = 0;
            return true;
        }
        return false;
    }

    public boolean isAttackingClose() { return damagePlayerTimer <= 0; }
    public void takeDamage(int amt) { hp = Math.max(0, hp - amt); }
    public boolean isAlive() { return hp > 0; }

    public void draw(Graphics2D g2) {
        if (!isAlive()) return;

        g2.setColor(new Color(120, 20, 30));
        Polygon leftWing = new Polygon(
                new int[]{x + 40, x - 30, x + 30},
                new int[]{y + 40, y - 40 + wingFlapOffset, y + 60}, 3
        );
        g2.fill(leftWing);

        g2.setColor(new Color(160, 25, 35));
        g2.fillOval(x, y + 30, width - 40, height - 30);

        g2.fillOval(x - 30, y, 60, 50);

        g2.setColor(Color.YELLOW);
        g2.fillOval(x - 20, y + 12, 10, 10);
        g2.setColor(Color.BLACK);
        g2.fillOval(x - 18, y + 14, 4, 6);

        g2.setColor(Color.DARK_GRAY);
        Polygon horn = new Polygon(new int[]{x + 10, x + 25, x + 5}, new int[]{y + 5, y - 25, y}, 3);
        g2.fill(horn);

        g2.setColor(new Color(140, 20, 30));
        g2.setStroke(new BasicStroke(12));
        g2.drawLine(x + width - 50, y + height - 30, x + width + 20, y + height - 10);
    }
}

class ResourceNode {
    public enum Type { TREE, IRON_ORE, CRYSTAL }

    public Type type;
    public int x, y;
    public int remainingYield = 3;
    public int respawnTimer = 0;

    public ResourceNode(Type type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public void update() {
        if (remainingYield <= 0) {
            respawnTimer++;
            if (respawnTimer >= 400) {
                remainingYield = 3;
                respawnTimer = 0;
            }
        }
    }

    public boolean isAlive() { return remainingYield > 0; }
    public int getCenterX() { return x + 20; }

    public String harvest() {
        if (remainingYield > 0) {
            remainingYield--;
            switch (type) {
                case TREE: return "Wood";
                case IRON_ORE: return "Iron";
                case CRYSTAL: return "Crystal";
            }
        }
        return null;
    }

    public void draw(Graphics2D g2) {
        if (remainingYield <= 0) {
            g2.setColor(Color.GRAY);
            g2.fillRect(x + 10, y + 30, 20, 10);
            return;
        }

        switch (type) {
            case TREE:
                g2.setColor(new Color(100, 60, 30));
                g2.fillRect(x + 15, y + 20, 12, 30);
                g2.setColor(new Color(40, 140, 50));
                g2.fillOval(x, y - 10, 42, 42);
                break;
            case IRON_ORE:
                g2.setColor(new Color(110, 115, 125));
                g2.fillOval(x, y + 10, 40, 25);
                g2.setColor(Color.LIGHT_GRAY);
                g2.fillOval(x + 10, y + 15, 10, 8);
                break;
            case CRYSTAL:
                g2.setColor(new Color(0, 220, 255));
                Polygon crystal = new Polygon(
                        new int[]{x + 20, x + 32, x + 20, x + 8},
                        new int[]{y - 5, y + 20, y + 35, y + 20}, 4
                );
                g2.fill(crystal);
                break;
        }
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

    public void update() { x += vx; }

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

    public boolean isDead() { return life <= 0; }

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

    public boolean isDead() { return life <= 0; }

    public void draw(Graphics2D g2) {
        g2.setColor(color);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString(text, x, y);
    }
}
