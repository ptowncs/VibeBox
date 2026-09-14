import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class Player {
    public static final int SPRITE_WIDTH = 96;
    public static final int SPRITE_HEIGHT = 96;
    private static final int DEFAULT_ATTACK_CANVAS_WIDTH = 192;

    public int x, y;
    // Fixed character size across all states
    public final int width = SPRITE_WIDTH, height = SPRITE_HEIGHT;
    public int hp = 100, maxHp = 100;

    private float vx = 0;
    private int attackAnimTimer = 0;
    private int attackCooldown = 0;
    private Weapon equippedWeapon = Weapon.WOODEN_CLUB;
    public Map<String, Integer> inventory = new HashMap<>();

    // Animation States
    public boolean facingRight = true;
    private Map<String, Image> animations = new HashMap<>();

    public Player(int x, int y) {
        this.x = x;
        this.y = y;
        inventory.put("Wood", 0);
        inventory.put("Iron", 0);
        inventory.put("Crystal", 0);
        loadAnimations();
    }

    private void loadAnimations() {
        // Runs
        animations.put("run_right", new ImageIcon("sprites/player/runs/char_run_right_anim.gif").getImage());
        animations.put("run_left", new ImageIcon("sprites/player/runs/char_run_left_anim.gif").getImage());

        // Idles
        animations.put("idle_right", new ImageIcon("sprites/player/idles/char_idle_right_anim.gif").getImage());
        animations.put("idle_left", new ImageIcon("sprites/player/idles/char_idle_left_anim.gif").getImage());
        animations.put("idle_spear", new ImageIcon("sprites/player/idles/char_idle_spear.gif").getImage());
        animations.put("idle_polearm", new ImageIcon("sprites/player/idles/char_idle_with_polearm.gif").getImage());
        animations.put("idle_dragonblade", new ImageIcon("sprites/player/idles/char_idle_dragonblade(1).gif").getImage());

        // Attacks
        animations.put("attack_right", new ImageIcon("sprites/player/attacks/char_attack_right_anim.gif").getImage());
        animations.put("attack_left", new ImageIcon("sprites/player/attacks/char_attack_left_anim.gif").getImage());
        animations.put("attack_spear", new ImageIcon("sprites/player/attacks/char_attack_spear.gif").getImage());
        animations.put("attack_polearm", new ImageIcon("sprites/player/attacks/char_attack_polearm.gif").getImage());
        animations.put("attack_dragonblade", new ImageIcon("sprites/player/attacks/char_attack_dragonblade(1).gif").getImage());
    }

    public void update(int maxBoundsX) {
        x += vx;
        if (x < 10) x = 10;
        if (x > maxBoundsX - 120) x = maxBoundsX - 120;

        if (attackAnimTimer > 0) attackAnimTimer--;
        if (attackCooldown > 0) attackCooldown--;
    }

    public void moveLeft() { 
        vx = -4.5f; 
        facingRight = false;
    }
    
    public void moveRight() { 
        vx = 4.5f; 
        facingRight = true;
    }
    
    public void stop() { vx = 0; }

    public boolean canAttack() { return attackCooldown <= 0; }

    public void triggerAttack() {
        attackAnimTimer = 18;
        attackCooldown = 24;
    }

    public void equipWeapon(Weapon w) { this.equippedWeapon = w; }
    public Weapon getEquippedWeapon() { return equippedWeapon; }

    public void takeDamage(int amt) { hp = Math.max(0, hp - amt); }
    public boolean isAlive() { return hp > 0; }

    public int getCenterX() { return x + width / 2; }
    public int getRightX() { return x + width; }

    public int getCurrentImageXOffset() {
        if (attackAnimTimer > 0 && equippedWeapon == Weapon.WOODEN_CLUB) {
            return facingRight ? -12 : -108;
        }
        return 0;
    }

public Image getCurrentImage() {
        Image rawSprite = null;
        boolean defaultAttack = false;

        // 1. Determine raw sprite frame from animation state map
        if (attackAnimTimer > 0) {
            if (equippedWeapon == Weapon.DRAGON_SPEAR) {
                rawSprite = animations.get("attack_polearm");
            } else if (equippedWeapon == Weapon.IRON_SWORD) {
                rawSprite = animations.get("attack_dragonblade");
            } else {
                rawSprite = facingRight ? animations.get("attack_right") : animations.get("attack_left");
                defaultAttack = true;
            }
        } else if (vx > 0) {
            rawSprite = animations.get("run_right");
        } else if (vx < 0) {
            rawSprite = animations.get("run_left");
        } else {
            if (equippedWeapon == Weapon.DRAGON_SPEAR) {
                rawSprite = animations.get("idle_spear");
            } else if (equippedWeapon == Weapon.IRON_SWORD) {
                rawSprite = animations.get("idle_dragonblade");
            } else {
                rawSprite = facingRight ? animations.get("idle_right") : animations.get("idle_left");
            }
        }

        // Fallback check if specific animation key wasn't loaded
        if (rawSprite == null) {
            rawSprite = animations.get("idle_right");
            if (rawSprite == null) return null;
        }

        int canvasWidth = defaultAttack ? DEFAULT_ATTACK_CANVAS_WIDTH : SPRITE_WIDTH;
        BufferedImage scaledImage = new BufferedImage(canvasWidth, SPRITE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaledImage.createGraphics();

        // Anti-aliasing and interpolation settings for smooth sprite scaling
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (defaultAttack) {
            // Keep the full 32x16 attack strip. The body is 12x16, while the
            // remaining pixels contain the weapon extending outward.
            g2.drawImage(rawSprite, 0, 0, DEFAULT_ATTACK_CANVAS_WIDTH, SPRITE_HEIGHT,
                    0, 16, 32, 32, null);
        } else {
            int sourceWidth = rawSprite.getWidth(null);
            int sourceHeight = rawSprite.getHeight(null);
            if (sourceWidth > 0 && sourceHeight > 0) {
                float fitScale = Math.min((float) SPRITE_WIDTH / sourceWidth,
                        (float) SPRITE_HEIGHT / sourceHeight);
                int drawWidth = Math.round(sourceWidth * fitScale);
                int drawHeight = Math.round(sourceHeight * fitScale);
                int drawX = (SPRITE_WIDTH - drawWidth) / 2;
                int drawY = SPRITE_HEIGHT - drawHeight;
                g2.drawImage(rawSprite, drawX, drawY, drawWidth, drawHeight, null);
            }
        }
        g2.dispose();

        return scaledImage;
    }
}