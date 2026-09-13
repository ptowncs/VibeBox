import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;

public class Player {
    public int x, y;
    // Fixed character size across all states
    public int width = 96, height = 96; 
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

    public Image getCurrentImage() {
        // Attack Animations
        if (attackAnimTimer > 0) {
            if (equippedWeapon == Weapon.DRAGON_SPEAR) {
                return animations.get("attack_spear");
            } else if (equippedWeapon == Weapon.IRON_SWORD) {
                return animations.get("attack_dragonblade");
            }
            return facingRight ? animations.get("attack_right") : animations.get("attack_left");
        }

        // Run Animations
        if (vx > 0) return animations.get("run_right");
        if (vx < 0) return animations.get("run_left");

        // Idle Animations
        if (equippedWeapon == Weapon.DRAGON_SPEAR) {
            return animations.get("idle_spear");
        } else if (equippedWeapon == Weapon.IRON_SWORD) {
            return animations.get("idle_dragonblade");
        }

        return facingRight ? animations.get("idle_right") : animations.get("idle_left");
    }
}