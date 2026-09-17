import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class DragonSlayerRPG extends JPanel implements ActionListener {

    private static final int VIEW_WIDTH = 800;
    private static final int VIEW_HEIGHT = 600;
    private static final int WORLD_WIDTH = 2600;
    private static final int GROUND_Y = 440;

    private static final int GRAVITY = 1;
    private static final int JUMP_STRENGTH = -14;

    private int cameraX = 0;

    // Player State
    private int playerX = 100;
    private int playerY = GROUND_Y - 48;
    private int playerWidth = 36;
    private int playerHeight = 48;
    private int velocityY = 0;
    private boolean isGrounded = true;

    private int playerHp = 100;
    private final int maxHp = 100;
    private int attackPower = 12;
    private int bowPower = 16;
    private int defense = 0;
    private int facingDir = 1;

    // Inventory
    private int woodCount = 0;
    private int ironCount = 0;
    private int herbCount = 0;
    private int potionCount = 1;
    private int arrowCount = 15;
    private boolean hasIronSword = false;
    private boolean hasSteelArmor = false;
    private boolean hasBow = false;

    private boolean left, right;
    private boolean showCraftingMenu = false;

    // Animation States
    private int playerFrameIndex = 0;
    private int animTimer = 0;
    private long meleeSwingStartTime = 0;
    private static final long SWING_DURATION = 200;

    // Dragon Boss State
    private int dragonX = 2200;
    private int dragonY = GROUND_Y - 110;
    private int dragonWidth = 150;
    private int dragonHeight = 120;
    private int dragonHp = 350;
    private final int dragonMaxHp = 350;
    private static final int DRAGON_AGGRO_RANGE = 650;
    private long lastDragonAttackTime = 0;
    private int dragonFrameIndex = 0;

    // Game Objects
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Fireball> fireballs = new ArrayList<>();
    private final List<Arrow> arrows = new ArrayList<>();
    private final List<ResourceNode> resources = new ArrayList<>();
    private final List<FloatingLoot> floatingLoots = new ArrayList<>();
    private final Random rng = new Random();

    // Generated Pixel Art Sprites
    private BufferedImage[] playerIdleFrames;
    private BufferedImage[] playerRunFrames;
    private BufferedImage[] playerAttackFrames;
    private BufferedImage[] goblinFrames;
    private BufferedImage[] dragonFrames;
    private BufferedImage treeSprite;
    private BufferedImage ironSprite;
    private BufferedImage herbSprite;

    private String statusMessage = "Gather starter resources [E] & Craft [C] before fighting!";
    private long messageTime = System.currentTimeMillis();
    private Timer gameTimer;

    public DragonSlayerRPG() {
        this.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        this.setBackground(new Color(24, 28, 36));
        this.setFocusable(true);
        this.requestFocusInWindow();

        generatePixelArtSprites();

        enemies.add(new Enemy(800, 40, 10));
        enemies.add(new Enemy(1200, 45, 12));
        enemies.add(new Enemy(1550, 55, 14));
        enemies.add(new Enemy(1850, 70, 16));

        resources.add(new ResourceNode(180, ResourceType.WOOD));
        resources.add(new ResourceNode(350, ResourceType.IRON));
        resources.add(new ResourceNode(420, ResourceType.HERB));
        resources.add(new ResourceNode(550, ResourceType.WOOD));
        resources.add(new ResourceNode(700, ResourceType.IRON));
        resources.add(new ResourceNode(1050, ResourceType.WOOD));
        resources.add(new ResourceNode(1400, ResourceType.HERB));
        resources.add(new ResourceNode(1700, ResourceType.IRON));

        setupKeyBindings();

        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    private static class Enemy {
        int x, y, hp, maxHp, attack;
        int width = 32, height = 40;
        int speed = 1;
        boolean alive = true;
        long lastAttackTime = 0;
        int frameIndex = 0;
        int facing = 1;

        Enemy(int x, int hp, int attack) {
            this.x = x;
            this.y = GROUND_Y - 40;
            this.hp = hp;
            this.maxHp = hp;
            this.attack = attack;
        }
    }

    private static class ResourceNode {
        int x;
        ResourceType type;
        boolean depleted = false;

        ResourceNode(int x, ResourceType type) {
            this.x = x;
            this.type = type;
        }
    }

    private enum ResourceType { WOOD, IRON, HERB }

    private static class FloatingLoot {
        int x, y;
        ResourceType type;
        int amount;
        long startTime;

        FloatingLoot(int x, int y, ResourceType type, int amount) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.amount = amount;
            this.startTime = System.currentTimeMillis();
        }
    }

    private static class Fireball {
        int x, y, speedX, speedY;
        int size = 18;

        Fireball(int x, int y, int targetX, int targetY) {
            this.x = x;
            this.y = y;
            double angle = Math.atan2(targetY - y, targetX - x);
            this.speedX = (int) (Math.cos(angle) * 8);
            this.speedY = (int) (Math.sin(angle) * 8);
        }

        void update() {
            x += speedX;
            y += speedY;
        }
    }

    private static class Arrow {
        double x, y;
        double speedX, speedY;
        int width = 18, height = 4;

        Arrow(int startX, int startY, int dir) {
            this.x = startX;
            this.y = startY;
            this.speedX = dir * 12;
            this.speedY = -1.5;
        }

        void update() {
            x += speedX;
            y += speedY;
            speedY += 0.08;
        }
    }

    // --- PIXEL ART GENERATION ENGINE ---
    private BufferedImage renderPixelMatrix(String[] matrix, Color[] palette, int scale) {
        int height = matrix.length;
        int width = 0;
        for (String row : matrix) {
            width = Math.max(width, row.length());
        }

        BufferedImage img = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char c = x < matrix[y].length() ? matrix[y].charAt(x) : ' ';
                if (c != ' ') {
                    int index = c - 'A';
                    if (index >= 0 && index < palette.length) {
                        g.setColor(palette[index]);
                        g.fillRect(x * scale, y * scale, scale, scale);
                    }
                }
            }
        }
        g.dispose();
        return img;
    }

    private void generatePixelArtSprites() {
        // Color Palette definitions
        Color skin = new Color(235, 180, 140);
        Color hair = new Color(90, 50, 20);
        Color tunic = new Color(40, 110, 220);
        Color pants = new Color(30, 40, 60);
        Color steel = new Color(190, 200, 210);
        Color steelDark = new Color(110, 120, 135);
        Color gold = new Color(240, 190, 40);

        Color[] playerPalette = { skin, hair, tunic, pants, steel, steelDark, gold };

        // Player Idle Frame 1
        String[] idle1 = {
            "  BBBB  ",
            "  BAAB  ",
            "  CCCC  ",
            " CCCCC  ",
            " CCCCC  ",
            "  DDDD  ",
            "  D  D  ",
            "  D  D  "
        };
        // Player Idle Frame 2
        String[] idle2 = {
            "  BBBB  ",
            "  BAAB  ",
            "  CCCC  ",
            " CCCCC  ",
            " CCCCC  ",
            "  DDDD  ",
            "  DD DD ",
            "  D   D "
        };

        // Player Run Frames
        String[] run1 = {
            "  BBBB  ",
            "  BAAB  ",
            "  CCCC  ",
            " CCCCC  ",
            " CCCCC  ",
            "  DDDD  ",
            " D   D  ",
            "D     D "
        };
        String[] run2 = {
            "  BBBB  ",
            "  BAAB  ",
            "  CCCC  ",
            " CCCCC  ",
            " CCCCC  ",
            "  DDDD  ",
            "  DD    ",
            "  DD    "
        };

        // Player Melee Swing Frame
        String[] swing = {
            "  BBBB EE ",
            "  BAABEEEE",
            "  CCCC EE ",
            " CCCCCCC  ",
            " CCCCC    ",
            "  DDDD    ",
            "  D  D    ",
            "  D  D    "
        };

        playerIdleFrames = new BufferedImage[]{
            renderPixelMatrix(idle1, playerPalette, 4),
            renderPixelMatrix(idle2, playerPalette, 4)
        };

        playerRunFrames = new BufferedImage[]{
            renderPixelMatrix(run1, playerPalette, 4),
            renderPixelMatrix(run2, playerPalette, 4)
        };

        playerAttackFrames = new BufferedImage[]{
            renderPixelMatrix(swing, playerPalette, 4)
        };

        // Goblin Pixel Art
        Color gSkin = new Color(40, 160, 50);
        Color gEye = Color.RED;
        Color gCloth = new Color(110, 70, 30);
        Color[] goblinPalette = { gSkin, gEye, gCloth };

        String[] gob1 = {
            "  AAAA  ",
            " ABAAA  ",
            "  CCCC  ",
            " CCCCCC ",
            "  AAAA  ",
            "  A  A  "
        };
        String[] gob2 = {
            "  AAAA  ",
            " ABAAA  ",
            "  CCCC  ",
            " CCCCCC ",
            "  AAAA  ",
            " A  A   "
        };

        goblinFrames = new BufferedImage[]{
            renderPixelMatrix(gob1, goblinPalette, 4),
            renderPixelMatrix(gob2, goblinPalette, 4)
        };

        // Simple geometric dragon built from shapes for a clear dragon silhouette.
        Color dDark = new Color(70, 20, 12);
        Color dBody = new Color(185, 55, 28);
        Color dWing = new Color(230, 110, 22);
        Color dBelly = new Color(245, 210, 120);
        Color dEye = new Color(255, 245, 160);
        Color[] dragonPalette = { dDark, dBody, dWing, dBelly, dEye };

        dragonFrames = new BufferedImage[] {
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        };

        // World Objects
        treeSprite = new BufferedImage(40, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = treeSprite.createGraphics();
        g.setColor(new Color(100, 60, 20)); g.fillRect(15, 25, 10, 25);
        g.setColor(new Color(34, 139, 34)); g.fillOval(0, 0, 40, 32);
        g.dispose();

        ironSprite = new BufferedImage(36, 36, BufferedImage.TYPE_INT_ARGB);
        g = ironSprite.createGraphics();
        g.setColor(new Color(110, 115, 125)); g.fillOval(2, 6, 32, 26);
        g.setColor(new Color(190, 200, 210)); g.fillRect(10, 12, 8, 8);
        g.dispose();

        herbSprite = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        g = herbSprite.createGraphics();
        g.setColor(new Color(50, 205, 50)); g.fillOval(5, 10, 10, 18); g.fillOval(15, 10, 10, 18);
        g.setColor(new Color(255, 105, 180)); g.fillOval(10, 4, 10, 10);
        g.dispose();
    }

    private void setupKeyBindings() {
        InputMap im = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = this.getActionMap();

        bind(im, am, "pressed W", "jump_w", this::jump);
        bind(im, am, "pressed SPACE", "jump_sp", this::jump);
        bind(im, am, "pressed UP", "jump_up", this::jump);

        bind(im, am, "pressed A", "l_p", () -> { left = true; facingDir = -1; });
        bind(im, am, "released A", "l_r", () -> left = false);
        bind(im, am, "pressed LEFT", "l_p2", () -> { left = true; facingDir = -1; });
        bind(im, am, "released LEFT", "l_r2", () -> left = false);

        bind(im, am, "pressed D", "r_p", () -> { right = true; facingDir = 1; });
        bind(im, am, "released D", "r_r", () -> right = false);
        bind(im, am, "pressed RIGHT", "r_p2", () -> { right = true; facingDir = 1; });
        bind(im, am, "released RIGHT", "r_r2", () -> right = false);

        bind(im, am, "pressed E", "interact", this::interactOrGather);
        bind(im, am, "pressed C", "toggle_craft", () -> {
            showCraftingMenu = !showCraftingMenu;
            this.requestFocusInWindow();
            if (showCraftingMenu) {
                showMessage("Crafting Menu Open - Press 1-5 to craft");
            } else {
                showMessage("Crafting Menu Closed");
            }
        });
        bind(im, am, "pressed H", "heal", this::usePotion);
        bind(im, am, "pressed F", "attack_melee", this::performMeleeAttack);
        bind(im, am, "pressed R", "attack_bow", this::shootBow);

        bind(im, am, "pressed 1", "craft_1", () -> craftItem(1));
        bind(im, am, "pressed 2", "craft_2", () -> craftItem(2));
        bind(im, am, "pressed 3", "craft_3", () -> craftItem(3));
        bind(im, am, "pressed 4", "craft_4", () -> craftItem(4));
        bind(im, am, "pressed 5", "craft_5", () -> craftItem(5));
    }

    private void bind(InputMap im, ActionMap am, String key, String id, Runnable action) {
        im.put(KeyStroke.getKeyStroke(key), id);
        am.put(id, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void jump() {
        if (isGrounded && playerHp > 0) {
            velocityY = JUMP_STRENGTH;
            isGrounded = false;
        }
    }

    private void showMessage(String msg) {
        this.statusMessage = msg;
        this.messageTime = System.currentTimeMillis();
    }

    private void interactOrGather() {
        Rectangle pRect = new Rectangle(playerX, playerY, playerWidth, playerHeight);
        for (ResourceNode node : resources) {
            if (!node.depleted) {
                Rectangle nRect = new Rectangle(node.x, GROUND_Y - 35, 40, 40);
                if (pRect.intersects(nRect)) {
                    node.depleted = true;
                    if (node.type == ResourceType.WOOD) {
                        woodCount += 3;
                        floatingLoots.add(new FloatingLoot(node.x + 10, GROUND_Y - 50, ResourceType.WOOD, 3));
                        showMessage("Gathered 3 Wood!");
                    } else if (node.type == ResourceType.IRON) {
                        ironCount += 2;
                        floatingLoots.add(new FloatingLoot(node.x + 10, GROUND_Y - 50, ResourceType.IRON, 2));
                        showMessage("Gathered 2 Iron Ore!");
                    } else if (node.type == ResourceType.HERB) {
                        herbCount += 2;
                        floatingLoots.add(new FloatingLoot(node.x + 10, GROUND_Y - 50, ResourceType.HERB, 2));
                        showMessage("Gathered 2 Medicinal Herbs!");
                    }
                    return;
                }
            }
        }
    }

    private void usePotion() {
        if (potionCount <= 0) showMessage("No Potions remaining! Craft via [C].");
        else if (playerHp >= maxHp) showMessage("Health is full!");
        else {
            potionCount--;
            playerHp = Math.min(maxHp, playerHp + 45);
            showMessage("Used Health Potion (+45 HP)!");
        }
    }

    private void dropEnemyLoot(Enemy mob) {
        int dropType = rng.nextInt(3);
        ResourceType type;
        if (dropType == 0) {
            woodCount += 2;
            type = ResourceType.WOOD;
            showMessage("Goblin Defeated! Dropped 2 Wood!");
        } else if (dropType == 1) {
            ironCount += 2;
            type = ResourceType.IRON;
            showMessage("Goblin Defeated! Dropped 2 Iron Ore!");
        } else {
            herbCount += 2;
            type = ResourceType.HERB;
            showMessage("Goblin Defeated! Dropped 2 Herbs!");
        }
        floatingLoots.add(new FloatingLoot(mob.x + mob.width / 2, mob.y, type, 2));
    }

    private void craftItem(int choice) {
        if (!showCraftingMenu) return;

        if (choice == 1) {
            if (hasIronSword) showMessage("Iron Sword already equipped!");
            else if (woodCount >= 3 && ironCount >= 4) {
                woodCount -= 3; ironCount -= 4;
                hasIronSword = true; attackPower += 15;
                showMessage("Crafted Iron Sword! (+15 Attack)");
            } else showMessage("Requires 3 Wood & 4 Iron!");
        } else if (choice == 2) {
            if (hasSteelArmor) showMessage("Steel Armor already equipped!");
            else if (woodCount >= 2 && ironCount >= 6) {
                woodCount -= 2; ironCount -= 6;
                hasSteelArmor = true; defense += 6;
                showMessage("Crafted Steel Armor! (+6 Defense)");
            } else showMessage("Requires 2 Wood & 6 Iron!");
        } else if (choice == 3) {
            if (hasBow) showMessage("Hunting Bow already equipped!");
            else if (woodCount >= 5 && herbCount >= 2) {
                woodCount -= 5; herbCount -= 2;
                hasBow = true;
                showMessage("Crafted Hunting Bow! Press [R] to Shoot.");
            } else showMessage("Requires 5 Wood & 2 Herbs!");
        } else if (choice == 4) {
            if (woodCount >= 2 && ironCount >= 1) {
                woodCount -= 2; ironCount -= 1;
                arrowCount += 10;
                showMessage("Crafted 10 Arrows!");
            } else showMessage("Requires 2 Wood & 1 Iron!");
        } else if (choice == 5) {
            if (herbCount >= 2) {
                herbCount -= 2; potionCount++;
                showMessage("Crafted Health Potion!");
            } else showMessage("Requires 2 Herbs!");
        }
    }

    private void performMeleeAttack() {
        if (playerHp <= 0) return;

        meleeSwingStartTime = System.currentTimeMillis();

        int attackX = (facingDir == 1) ? playerX : playerX - 35;
        Rectangle attackArea = new Rectangle(attackX, playerY - 10, playerWidth + 35, playerHeight + 20);

        boolean hitTarget = false;
        for (Enemy mob : enemies) {
            if (mob.alive) {
                Rectangle mobRect = new Rectangle(mob.x, mob.y, mob.width, mob.height);
                if (attackArea.intersects(mobRect)) {
                    mob.hp -= attackPower;
                    hitTarget = true;
                    if (mob.hp <= 0) {
                        mob.alive = false;
                        dropEnemyLoot(mob);
                    }
                }
            }
        }

        if (dragonHp > 0) {
            Rectangle dRect = new Rectangle(dragonX, dragonY, dragonWidth, dragonHeight);
            if (attackArea.intersects(dRect)) {
                dragonHp -= attackPower;
                hitTarget = true;
                if (dragonHp <= 0) dragonHp = 0;
            }
        }

        if (!hitTarget) {
            showMessage("Swung weapon!");
        }
    }

    private void shootBow() {
        if (playerHp <= 0) return;

        if (!hasBow) {
            showMessage("You need to craft a Bow [C] first!");
            return;
        }
        if (arrowCount <= 0) {
            showMessage("Out of Arrows! Craft more in menu [C].");
            return;
        }

        arrowCount--;
        int startX = (facingDir == 1) ? playerX + playerWidth : playerX - 10;
        arrows.add(new Arrow(startX, playerY + 20, facingDir));
        showMessage("Fired Bow! (" + arrowCount + " Arrows left)");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (playerHp <= 0 || dragonHp <= 0) {
            repaint();
            return;
        }

        animTimer++;
        if (animTimer % 8 == 0) {
            playerFrameIndex = (playerFrameIndex + 1) % 2;
            dragonFrameIndex = (dragonFrameIndex + 1) % dragonFrames.length;
            for (Enemy mob : enemies) {
                mob.frameIndex = (mob.frameIndex + 1) % 2;
            }
        }

        int moveSpeed = 5;
        if (left && playerX > 10) playerX -= moveSpeed;
        if (right && playerX < WORLD_WIDTH - playerWidth - 10) playerX += moveSpeed;

        velocityY += GRAVITY;
        playerY += velocityY;

        if (playerY >= GROUND_Y - playerHeight) {
            playerY = GROUND_Y - playerHeight;
            velocityY = 0;
            isGrounded = true;
        }

        cameraX = playerX - VIEW_WIDTH / 3;
        cameraX = Math.max(0, Math.min(cameraX, WORLD_WIDTH - VIEW_WIDTH));

        Rectangle pRect = new Rectangle(playerX, playerY, playerWidth, playerHeight);
        long now = System.currentTimeMillis();

        for (Enemy mob : enemies) {
            if (mob.alive) {
                int distToPlayer = playerX - mob.x;

                if (Math.abs(distToPlayer) < 300 && Math.abs(distToPlayer) > 10) {
                    if (distToPlayer > 0) {
                        mob.x += mob.speed;
                        mob.facing = 1;
                    } else {
                        mob.x -= mob.speed;
                        mob.facing = -1;
                    }
                }

                Rectangle mRect = new Rectangle(mob.x, mob.y, mob.width, mob.height);
                if (pRect.intersects(mRect)) {
                    if (now - mob.lastAttackTime > 1100) {
                        int damage = Math.max(2, mob.attack - defense);
                        playerHp = Math.max(0, playerHp - damage);
                        mob.lastAttackTime = now;
                        showMessage("Goblin slashed you for " + damage + " damage!");
                    }
                }
            }
        }

        int distanceToDragon = Math.abs((dragonX + dragonWidth / 2) - (playerX + playerWidth / 2));
        if (dragonHp > 0 && distanceToDragon <= DRAGON_AGGRO_RANGE) {
            if (now - lastDragonAttackTime > 1800) {
                fireballs.add(new Fireball(dragonX, dragonY + 30, playerX, playerY + 20));
                lastDragonAttackTime = now;
            }
        }

        Iterator<Arrow> arrowIt = arrows.iterator();
        while (arrowIt.hasNext()) {
            Arrow arr = arrowIt.next();
            arr.update();
            Rectangle aRect = new Rectangle((int) arr.x, (int) arr.y, arr.width, arr.height);

            boolean removed = false;
            for (Enemy mob : enemies) {
                if (mob.alive && aRect.intersects(new Rectangle(mob.x, mob.y, mob.width, mob.height))) {
                    mob.hp -= bowPower;
                    if (mob.hp <= 0) {
                        mob.alive = false;
                        dropEnemyLoot(mob);
                    } else {
                        showMessage("Arrow hit Goblin for " + bowPower + " damage!");
                    }
                    arrowIt.remove();
                    removed = true;
                    break;
                }
            }

            if (removed) continue;

            if (dragonHp > 0 && aRect.intersects(new Rectangle(dragonX, dragonY, dragonWidth, dragonHeight))) {
                dragonHp -= bowPower;
                if (dragonHp <= 0) dragonHp = 0;
                showMessage("Arrow hit Dragon for " + bowPower + " damage!");
                arrowIt.remove();
                continue;
            }

            if (arr.y >= GROUND_Y - 2 || arr.x < 0 || arr.x > WORLD_WIDTH) {
                arrowIt.remove();
            }
        }

        Iterator<Fireball> fbIt = fireballs.iterator();
        while (fbIt.hasNext()) {
            Fireball fb = fbIt.next();
            fb.update();

            Rectangle fbRect = new Rectangle(fb.x, fb.y, fb.size, fb.size);
            if (fbRect.intersects(pRect)) {
                int damage = Math.max(4, 22 - defense);
                playerHp = Math.max(0, playerHp - damage);
                showMessage("Hit by Dragon Fireball for " + damage + " damage!");
                fbIt.remove();
            } else if (fb.x < 0 || fb.x > WORLD_WIDTH || fb.y < 0 || fb.y > VIEW_HEIGHT) {
                fbIt.remove();
            }
        }

        floatingLoots.removeIf(loot -> now - loot.startTime > 1500);

        repaint();
    }

    private void drawPixelSprite(Graphics2D g, BufferedImage img, int x, int y, int facing) {
        if (facing == -1) {
            g.drawImage(img, x + img.getWidth(), y, -img.getWidth(), img.getHeight(), null);
        } else {
            g.drawImage(img, x, y, null);
        }
    }

    private void drawDragonShape(Graphics2D g, int x, int y) {
        // body
        g.setColor(new Color(185, 55, 28));
        g.fillOval(x + 25, y + 28, 150, 48);

        // wings
        g.setColor(new Color(230, 110, 22));
        g.fillPolygon(new int[]{x + 35, x + 90, x + 110}, new int[]{y + 20, y - 25, y + 30}, 3);
        g.fillPolygon(new int[]{x + 35, x + 90, x + 110}, new int[]{y + 70, y + 120, y + 62}, 3);
        g.fillPolygon(new int[]{x + 90, x + 150, x + 120}, new int[]{y + 18, y - 30, y + 22}, 3);
        g.fillPolygon(new int[]{x + 90, x + 150, x + 120}, new int[]{y + 70, y + 120, y + 65}, 3);

        // head and snout
        g.setColor(new Color(185, 55, 28));
        g.fillOval(x + 150, y + 18, 52, 30);
        g.fillPolygon(new int[]{x + 190, x + 220, x + 195}, new int[]{y + 24, y + 42, y + 52}, 3);
        g.fillPolygon(new int[]{x + 200, x + 220, x + 208}, new int[]{y + 40, y + 60, y + 54}, 3);

        // horns
        g.setColor(new Color(70, 20, 12));
        g.fillPolygon(new int[]{x + 160, x + 168, x + 174}, new int[]{y + 8, y - 12, y + 10}, 3);
        g.fillPolygon(new int[]{x + 180, x + 188, x + 194}, new int[]{y + 8, y - 12, y + 10}, 3);

        // belly
        g.setColor(new Color(245, 210, 120));
        g.fillOval(x + 95, y + 38, 30, 18);

        // eye
        g.setColor(new Color(255, 245, 160));
        g.fillOval(x + 197, y + 30, 6, 6);

        // tail
        g.setColor(new Color(185, 55, 28));
        g.fillPolygon(new int[]{x + 10, x - 20, x + 18}, new int[]{y + 45, y + 30, y + 60}, 3);
        g.fillPolygon(new int[]{x - 15, x - 45, x - 10}, new int[]{y + 28, y + 20, y + 38}, 3);

        // facing-right orientation fix
        g.setColor(new Color(185, 55, 28));
        g.fillPolygon(new int[]{x + 15, x + 40, x + 30}, new int[]{y + 44, y + 25, y + 60}, 3);
    }

    private void drawInventoryMenu(Graphics2D g2) {
        int x = 150;
        int y = 90;
        int width = 500;
        int height = 330;

        g2.setColor(new Color(18, 20, 30, 220));
        g2.fillRoundRect(x, y, width, height, 18, 18);
        g2.setColor(new Color(255, 215, 0));
        g2.drawRoundRect(x, y, width, height, 18, 18);

        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        g2.drawString("Inventory & Crafting", x + 20, y + 36);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        int lineY = y + 70;
        g2.drawString("Materials:", x + 20, lineY);
        lineY += 26;
        g2.drawString("- Wood: " + woodCount + "   Iron: " + ironCount + "   Herbs: " + herbCount, x + 20, lineY);
        lineY += 28;
        g2.drawString("- Potions: " + potionCount + "   Arrows: " + arrowCount, x + 20, lineY);
        lineY += 32;
        g2.drawString("Crafting recipes:", x + 20, lineY);
        lineY += 24;
        g2.drawString("1) Iron Sword       3 Wood / 4 Iron      " + (hasIronSword ? "Owned" : "Ready"), x + 20, lineY);
        lineY += 24;
        g2.drawString("2) Steel Armor      2 Wood / 6 Iron      " + (hasSteelArmor ? "Owned" : "Ready"), x + 20, lineY);
        lineY += 24;
        g2.drawString("3) Hunting Bow      5 Wood / 2 Herbs     " + (hasBow ? "Owned" : "Ready"), x + 20, lineY);
        lineY += 24;
        g2.drawString("4) Arrows           2 Wood / 1 Iron      " + (arrowCount > 0 ? "Add 10" : "Craft"), x + 20, lineY);
        lineY += 24;
        g2.drawString("5) Health Potion    2 Herbs              " + (potionCount > 0 ? "Craft" : "Craft"), x + 20, lineY);

        g2.setColor(new Color(200, 200, 255));
        g2.drawString("Press 1-5 to craft, or C to close the inventory.", x + 20, y + height - 20);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Graphics2D gWorld = (Graphics2D) g2.create();
        gWorld.translate(-cameraX, 0);

        // Background
        gWorld.setColor(new Color(24, 28, 42));
        gWorld.fillRect(0, 0, WORLD_WIDTH, VIEW_HEIGHT);
        gWorld.setColor(new Color(60, 25, 25));
        gWorld.fillRect(1900, 0, 700, VIEW_HEIGHT);

        gWorld.setColor(new Color(50, 95, 45));
        gWorld.fillRect(0, GROUND_Y, 1900, VIEW_HEIGHT - GROUND_Y);
        gWorld.setColor(new Color(70, 40, 30));
        gWorld.fillRect(1900, GROUND_Y, 700, VIEW_HEIGHT - GROUND_Y);
        gWorld.setColor(new Color(75, 135, 60));
        gWorld.fillRect(0, GROUND_Y, 1900, 6);

        // Resources
        for (ResourceNode node : resources) {
            if (!node.depleted) {
                if (node.type == ResourceType.WOOD) {
                    gWorld.drawImage(treeSprite, node.x, GROUND_Y - 48, null);
                } else if (node.type == ResourceType.IRON) {
                    gWorld.drawImage(ironSprite, node.x, GROUND_Y - 35, null);
                } else if (node.type == ResourceType.HERB) {
                    gWorld.drawImage(herbSprite, node.x, GROUND_Y - 30, null);
                }
            }
        }

        // Enemies
        for (Enemy mob : enemies) {
            if (mob.alive) {
                drawPixelSprite(gWorld, goblinFrames[mob.frameIndex], mob.x, mob.y, mob.facing);

                gWorld.setColor(Color.BLACK);
                gWorld.fillRect(mob.x, mob.y - 8, mob.width, 5);
                gWorld.setColor(Color.GREEN);
                gWorld.fillRect(mob.x, mob.y - 8, (int) (mob.width * ((double) mob.hp / mob.maxHp)), 5);
            }
        }

        // Dragon Boss
        if (dragonHp > 0) {
            drawDragonShape(gWorld, dragonX, dragonY);

            gWorld.setColor(Color.BLACK);
            gWorld.fillRect(dragonX, dragonY - 20, dragonWidth, 12);
            gWorld.setColor(Color.RED);
            gWorld.fillRect(dragonX, dragonY - 20, (int) (dragonWidth * ((double) dragonHp / dragonMaxHp)), 12);
        }

        // Projectiles
        gWorld.setColor(new Color(210, 180, 120));
        gWorld.setStroke(new BasicStroke(2));
        for (Arrow arr : arrows) {
            gWorld.drawLine((int) arr.x, (int) arr.y, (int) arr.x + (arr.speedX > 0 ? 16 : -16), (int) arr.y);
        }

        for (Fireball fb : fireballs) {
            gWorld.setColor(new Color(255, 90, 0));
            gWorld.fillOval(fb.x, fb.y, fb.size, fb.size);
        }

        // Render Pixel Player
        long now = System.currentTimeMillis();
        boolean isSwinging = (now - meleeSwingStartTime) < SWING_DURATION;

        BufferedImage currentSprite;
        if (isSwinging) {
            currentSprite = playerAttackFrames[0];
        } else if (left || right) {
            currentSprite = playerRunFrames[playerFrameIndex];
        } else {
            currentSprite = playerIdleFrames[playerFrameIndex];
        }

        drawPixelSprite(gWorld, currentSprite, playerX, playerY, facingDir);

        // Floating Loot
        for (FloatingLoot loot : floatingLoots) {
            long elapsed = now - loot.startTime;
            int offsetY = (int) (elapsed / 25);
            gWorld.setFont(new Font("SansSerif", Font.BOLD, 12));
            gWorld.setColor(Color.YELLOW);
            gWorld.drawString("+" + loot.amount + " " + loot.type.name(), loot.x, loot.y - offsetY);
        }

        gWorld.dispose();

        // UI Header Bar
        g2.setColor(new Color(15, 15, 20, 220));
        g2.fillRect(0, 0, VIEW_WIDTH, 45);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("HP: " + playerHp + "/" + maxHp, 10, 27);
        g2.drawString("Potions [H]: " + potionCount, 100, 27);
        g2.drawString("Arrows [R]: " + arrowCount, 200, 27);
        g2.drawString("Wood: " + woodCount, 300, 27);
        g2.drawString("Iron: " + ironCount, 370, 27);
        g2.drawString("Herbs: " + herbCount, 430, 27);
        g2.drawString("Atk: " + attackPower + " | Def: " + defense, 500, 27);

        if (showCraftingMenu) {
            drawInventoryMenu(g2);
        }

        // Status Message
        if (System.currentTimeMillis() - messageTime < 4000) {
            g2.setColor(new Color(0, 0, 0, 210));
            g2.fillRect(20, VIEW_HEIGHT - 55, 760, 35);
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString(statusMessage, 35, VIEW_HEIGHT - 32);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Dragon Slayer RPG - Pixel Art Edition");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new DragonSlayerRPG());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}