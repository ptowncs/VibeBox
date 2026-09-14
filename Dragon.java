import java.awt.*;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;

public class Dragon {
    public int x, y;
    public int width = 220, height = 160;
    public int hp = 350, maxHp = 350;

    public int attackTimer = 0;
    public int damagePlayerTimer = 0;
    private Image sprite;

    public Dragon(int x, int y) {
        this.x = x;
        this.y = y;
        Image sheet = new ImageIcon("sprites/Dragon.png").getImage();
        BufferedImage idleFrame = new BufferedImage(230, 165, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = idleFrame.createGraphics();
        g2.drawImage(sheet, 0, 0, 230, 165, 0, 38, 230, 203, null);
        g2.dispose();
        this.sprite = idleFrame;
    }

    public void update() {
        attackTimer++;
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

    public Image getSprite() {
        return sprite;
    }
}