// Ashen Vale - a 2D side-scrolling soulslike, single-file Java Swing edition.
// Run:  javac AshenVale.java   then   java AshenVale
// Controls: A/D move, W or SPACE jump, SHIFT sprint, L roll, J light attack,
//           K heavy attack, Q drink flask, ENTER / R restart.

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class AshenVale extends JPanel implements ActionListener, KeyListener {

    // ---------- constants ----------
    static final int VIEW_W = 1280, VIEW_H = 560;
    static final double GROUND = 430, GRAVITY = 2100, WORLD_END = 7200;

    // ---------- entities ----------
    static class Player {
        double x = 120, y = GROUND, vx = 0, vy = 0;
        int face = 1;
        double hp = 100, maxHp = 100, fp = 60, maxFp = 60, st = 100, maxSt = 100;
        int flasks = 4, maxFlasks = 4;
        boolean grounded = true;
        String action = "idle";      // idle | light | heavy | roll | hurt | dead
        double actT = 0, actLen = 0;
        boolean struck = false;
        double iframes = 0, animT = 0, hurtFlash = 0;
        long runes = 0;
    }

    static class Enemy {
        double x, y = GROUND, vx = 0;
        int face = -1;
        double hp, maxHp, scale, reach, dmg, speed;
        String name;
        boolean boss, phase2 = false, dead = false, awake = false;
        String state = "idle";       // idle | walk | windup | strike | recover | hit | dead
        double stateT = 0, animT = 0, cool = 1.0;
        boolean hasHit = false;
        int runes;

        Enemy(double x, double hp, double scale, double reach, double dmg, double speed,
              String name, boolean boss, int runes) {
            this.x = x; this.hp = hp; this.maxHp = hp; this.scale = scale;
            this.reach = reach; this.dmg = dmg; this.speed = speed;
            this.name = name; this.boss = boss; this.runes = runes;
        }
    }

    static class Particle {
        double x, y, vx, vy, life, max, size;
        Color col;
        Particle(double x, double y, double vx, double vy, double life, double size, Color c) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.max = life; this.size = size; this.col = c;
        }
    }

    // ---------- state ----------
    Player p = new Player();
    List<Enemy> enemies = new ArrayList<>();
    List<Particle> particles = new ArrayList<>();
    Set<Integer> keys = new HashSet<>();
    double camX = 0, shake = 0, time = 0;
    String message = "", messageSub = "";
    double messageT = 0;
    boolean started = false, dead = false, victory = false;
    Enemy activeBoss = null;
    Random rng = new Random(7);
    long lastNano = System.nanoTime();

    // background hills, generated once
    double[] hillA = new double[64], hillB = new double[64];

    public AshenVale() {
        setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        setBackground(new Color(12, 10, 14));
        setFocusable(true);
        addKeyListener(this);
        for (int i = 0; i < hillA.length; i++) {
            hillA[i] = 60 + rng.nextDouble() * 70;
            hillB[i] = 110 + rng.nextDouble() * 110;
        }
        reset();
        new javax.swing.Timer(16, this).start();
    }

    void reset() {
        p = new Player();
        enemies.clear();
        particles.clear();
        dead = false; victory = false; activeBoss = null;
        camX = 0; shake = 0;
        double[] spots = {900, 1500, 2000, 3600, 4400};
        for (double s : spots)
            enemies.add(new Enemy(s, 90, 1.0, 74, 12, 62, "Hollowed Wretch", false, 260));
        enemies.add(new Enemy(2600, 420, 1.7, 120, 24, 78, "Godrick Knight", true, 3200));
        enemies.add(new Enemy(5900, 760, 1.9, 150, 30, 88, "Malenith, Blade of Ash", true, 9000));
        say("ASHEN VALE", "press any key to rise");
    }

    void say(String m, String s) { message = m; messageSub = s; messageT = 2.6; }

    // ---------- loop ----------
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = Math.min((now - lastNano) / 1e9, 0.033);
        lastNano = now;
        update(dt);
        repaint();
    }

    void update(double dt) {
        time += dt;
        if (messageT > 0) messageT -= dt;
        if (shake > 0) shake = Math.max(0, shake - dt * 40);
        updateParticles(dt);
        if (!started || dead || victory) return;

        p.animT += dt;
        if (p.iframes > 0) p.iframes -= dt;
        if (p.hurtFlash > 0) p.hurtFlash -= dt;

        boolean busy = !p.action.equals("idle");
        if (busy) {
            p.actT += dt;
            if (p.action.equals("roll")) {
                p.vx = p.face * 430;
                p.iframes = Math.max(p.iframes, 0.02);
            } else if (p.action.equals("light") && !p.struck && p.actT > 0.16) {
                p.struck = true; strike(30, 88, false);
            } else if (p.action.equals("heavy") && !p.struck && p.actT > 0.30) {
                p.struck = true; strike(72, 112, true);
            }
            if (p.actT >= p.actLen) { p.action = "idle"; p.struck = false; p.vx = 0; }
        } else {
            double sp = keys.contains(KeyEvent.VK_SHIFT) && p.st > 1 ? 320 : 190;
            double mv = 0;
            if (keys.contains(KeyEvent.VK_A)) mv -= 1;
            if (keys.contains(KeyEvent.VK_D)) mv += 1;
            if (mv != 0) { p.face = (int) Math.signum(mv); p.vx = mv * sp; }
            else p.vx *= 0.7;
            if (mv != 0 && sp > 200) p.st = Math.max(0, p.st - 18 * dt);
            else p.st = Math.min(p.maxSt, p.st + 26 * dt);
            p.fp = Math.min(p.maxFp, p.fp + 3 * dt);
        }

        // physics
        p.vy += GRAVITY * dt;
        p.x = Math.max(40, Math.min(WORLD_END, p.x + p.vx * dt));
        p.y += p.vy * dt;
        if (p.y >= GROUND) { p.y = GROUND; p.vy = 0; p.grounded = true; }
        else p.grounded = false;

        updateEnemies(dt);

        // camera
        double target = Math.max(0, Math.min(WORLD_END - VIEW_W + 300, p.x - VIEW_W * 0.38));
        camX += (target - camX) * Math.min(1, dt * 6);

        if (p.x > WORLD_END - 200) {
            victory = true;
            say("LANDS BETWEEN CONQUERED", "press ENTER to walk again");
        }
    }

    void updateEnemies(double dt) {
        activeBoss = null;
        for (Enemy e : enemies) {
            e.animT += dt;
            if (e.dead) continue;
            double d = p.x - e.x;
            double ad = Math.abs(d);
            if (ad > 900 && !e.awake) continue;
            e.awake = true;
            if (e.boss && ad < 620) activeBoss = e;

            if (e.boss && !e.phase2 && e.hp < e.maxHp * 0.5) {
                e.phase2 = true;
                say(e.name.toUpperCase(), "the blade awakens");
                shake = 18;
            }

            e.stateT += dt;
            double speedMul = e.phase2 ? 1.25 : 1.0;

            switch (e.state) {
                case "hit":
                    if (e.stateT > 0.22) { e.state = "idle"; e.stateT = 0; }
                    break;
                case "windup": {
                    double len = (e.boss ? 0.55 : 0.45) / speedMul;
                    if (e.stateT > len) { e.state = "strike"; e.stateT = 0; e.hasHit = false; }
                    break;
                }
                case "strike": {
                    if (!e.hasHit && e.stateT > 0.06) {
                        e.hasHit = true;
                        if (Math.abs(p.x - e.x) < e.reach && Math.signum(p.x - e.x) == e.face)
                            hurtPlayer(e.dmg * (e.phase2 ? 1.25 : 1));
                        for (int i = 0; i < 10; i++)
                            particles.add(new Particle(e.x + e.face * e.reach * 0.7, GROUND - 40 * e.scale,
                                    rng.nextGaussian() * 60, -rng.nextDouble() * 120, 0.4, 3,
                                    new Color(220, 150, 80)));
                    }
                    if (e.stateT > 0.22) { e.state = "recover"; e.stateT = 0; }
                    break;
                }
                case "recover": {
                    double len = (e.boss ? 0.6 : 0.5) / speedMul;
                    if (e.stateT > len) { e.state = "idle"; e.stateT = 0; e.cool = 0.35 + rng.nextDouble() * 0.5; }
                    break;
                }
                default: {
                    e.face = d >= 0 ? 1 : -1;
                    if (ad < e.reach * 0.85) {
                        e.cool -= dt;
                        if (e.cool <= 0) { e.state = "windup"; e.stateT = 0; }
                        e.vx = 0;
                    } else if (ad < 620) {
                        e.state = "idle";
                        e.vx = e.face * e.speed * speedMul;
                        e.x += e.vx * dt;
                    } else e.vx = 0;
                }
            }
        }
        enemies.removeIf(e -> e.dead && e.stateT > 1.6);
    }

    void strike(double dmg, double reach, boolean heavy) {
        shake = heavy ? 14 : 6;
        double hx = p.x + p.face * reach * 0.6;
        for (int i = 0; i < (heavy ? 22 : 12); i++)
            particles.add(new Particle(hx, GROUND - 45, rng.nextGaussian() * 90,
                    -rng.nextDouble() * 200, 0.45, heavy ? 4 : 3, new Color(255, 214, 140)));
        for (Enemy e : enemies) {
            if (e.dead) continue;
            if (Math.abs(e.x - p.x) < reach && Math.signum(e.x - p.x) == p.face) {
                e.hp -= dmg + (heavy ? 0 : 0);
                e.state = "hit"; e.stateT = 0;
                for (int i = 0; i < 16; i++)
                    particles.add(new Particle(e.x, GROUND - 50 * e.scale, rng.nextGaussian() * 110,
                            -rng.nextDouble() * 230, 0.5, 3, new Color(190, 40, 40)));
                if (e.hp <= 0) {
                    e.dead = true; e.state = "dead"; e.stateT = 0;
                    p.runes += e.runes;
                    if (e.boss) { say("GREAT ENEMY FELLED", "+" + e.runes + " runes"); p.flasks = p.maxFlasks; }
                    for (int i = 0; i < 40; i++)
                        particles.add(new Particle(e.x, GROUND - 60 * e.scale, rng.nextGaussian() * 140,
                                -rng.nextDouble() * 300, 1.0, 3, new Color(255, 190, 90)));
                }
            }
        }
    }

    void hurtPlayer(double dmg) {
        if (p.iframes > 0 || dead) return;
        p.hp -= dmg;
        p.hurtFlash = 0.35;
        p.iframes = 0.55;
        shake = 12;
        for (int i = 0; i < 18; i++)
            particles.add(new Particle(p.x, GROUND - 50, rng.nextGaussian() * 100,
                    -rng.nextDouble() * 220, 0.5, 3, new Color(200, 50, 50)));
        if (p.hp <= 0) {
            p.hp = 0; dead = true; p.action = "dead";
            say("YOU DIED", "press ENTER to rise again");
        }
    }

    void updateParticles(double dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle q = it.next();
            q.life -= dt;
            q.vy += 900 * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            if (q.life <= 0) it.remove();
        }
    }

    // ---------- input ----------
    public void keyPressed(KeyEvent ev) {
        int k = ev.getKeyCode();
        keys.add(k);
        if (!started) { started = true; say("ASHEN VALE", "the road east awaits"); return; }
        if (dead || victory) {
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_R) { started = true; reset(); started = true; }
            return;
        }
        boolean busy = !p.action.equals("idle");
        if ((k == KeyEvent.VK_W || k == KeyEvent.VK_SPACE) && p.grounded && !busy) {
            p.vy = -760; p.grounded = false;
        }
        if (k == KeyEvent.VK_J && !busy && p.st >= 16) {
            p.action = "light"; p.actLen = 0.34; p.actT = 0; p.struck = false; p.st -= 16; p.vx = 0;
        }
        if (k == KeyEvent.VK_K && !busy && p.st >= 34) {
            p.action = "heavy"; p.actLen = 0.62; p.actT = 0; p.struck = false; p.st -= 34; p.vx = 0;
        }
        if (k == KeyEvent.VK_L && !busy && p.st >= 22 && p.grounded) {
            p.action = "roll"; p.actLen = 0.42; p.actT = 0; p.st -= 22; p.iframes = 0.30;
        }
        if (k == KeyEvent.VK_Q && !busy && p.flasks > 0 && p.hp > 0) {
            p.flasks--; p.hp = Math.min(p.maxHp, p.hp + 55);
            for (int i = 0; i < 24; i++)
                particles.add(new Particle(p.x, GROUND - 40, rng.nextGaussian() * 40,
                        -60 - rng.nextDouble() * 160, 0.8, 3, new Color(255, 230, 150)));
        }
    }

    public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }
    public void keyTyped(KeyEvent e) { }

    // ---------- rendering ----------
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double sx = shake > 0 ? (rng.nextDouble() - 0.5) * shake : 0;
        double sy = shake > 0 ? (rng.nextDouble() - 0.5) * shake : 0;
        g.translate(sx, sy);

        drawSky(g);
        g.translate(-camX, 0);
        drawGround(g);
        for (Enemy e : enemies) if (e.awake || Math.abs(e.x - p.x) < 1100) drawEnemy(g, e);
        drawPlayer(g);
        for (Particle q : particles) {
            float a = (float) Math.max(0, Math.min(1, q.life / q.max));
            g.setColor(new Color(q.col.getRed(), q.col.getGreen(), q.col.getBlue(), (int) (a * 255)));
            g.fill(new Ellipse2D.Double(q.x - q.size / 2, q.y - q.size / 2, q.size, q.size));
        }
        g.translate(camX, 0);
        g.translate(-sx, -sy);

        drawHud(g);
        drawMessage(g);
        if (!started) drawTitle(g);
    }

    void drawSky(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(28, 22, 30), 0, (float) GROUND, new Color(58, 40, 36)));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        // pale sun
        g.setColor(new Color(220, 190, 140, 40));
        g.fill(new Ellipse2D.Double(880, 60, 170, 170));
        drawHills(g, hillB, 0.22, new Color(38, 30, 36), 250);
        drawHills(g, hillA, 0.45, new Color(30, 24, 28), 310);
    }

    void drawHills(Graphics2D g, double[] h, double par, Color c, double baseY) {
        g.setColor(c);
        Path2D path = new Path2D.Double();
        double off = -camX * par;
        path.moveTo(off - 200, VIEW_H);
        for (int i = 0; i < h.length; i++) {
            double x = off + i * 190 - 200;
            path.lineTo(x, baseY - h[i]);
            path.lineTo(x + 95, baseY - h[i] * 0.55);
        }
        path.lineTo(off + h.length * 190, VIEW_H);
        path.closePath();
        g.fill(path);
    }

    void drawGround(Graphics2D g) {
        g.setColor(new Color(24, 20, 22));
        g.fillRect((int) camX - 100, (int) GROUND, VIEW_W + 400, VIEW_H);
        g.setColor(new Color(92, 74, 58));
        g.fillRect((int) camX - 100, (int) GROUND, VIEW_W + 400, 4);
        g.setColor(new Color(40, 34, 34));
        for (int i = (int) (camX / 120) - 1; i < (camX + VIEW_W) / 120 + 2; i++) {
            double x = i * 120 + ((i * 37) % 40);
            g.fillRect((int) x, (int) GROUND + 18 + (i % 3) * 14, 46, 5);
        }
        // milestones
        g.setColor(new Color(70, 60, 55));
        for (int i = 1; i < 8; i++) {
            double x = i * 900;
            g.fillRect((int) x, (int) GROUND - 54, 10, 54);
            g.fillRect((int) x - 14, (int) GROUND - 54, 38, 9);
        }
    }

    void drawPlayer(Graphics2D g) {
        double bob = p.grounded && Math.abs(p.vx) > 10 ? Math.sin(p.animT * 14) * 3 : 0;
        double x = p.x, y = p.y + bob;
        boolean flash = p.hurtFlash > 0 && ((int) (p.hurtFlash * 30) % 2 == 0);
        Color body = flash ? new Color(240, 200, 200) : new Color(196, 186, 172);
        Color cloak = flash ? new Color(220, 150, 150) : new Color(96, 40, 48);

        double lean = p.action.equals("roll") ? Math.sin(p.actT / p.actLen * Math.PI) * 1.4 : 0;
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(lean * p.face);
        g.scale(p.face, 1);

        // shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(-18, -bob - (p.y - GROUND) * 0 + 0, 36, 10));

        // cloak
        g.setColor(cloak);
        Path2D cl = new Path2D.Double();
        cl.moveTo(-6, -62); cl.lineTo(-22, -6); cl.lineTo(10, -6); cl.lineTo(8, -62);
        cl.closePath();
        g.fill(cl);
        // legs
        double swing = p.grounded && Math.abs(p.vx) > 10 ? Math.sin(p.animT * 14) * 9 : 0;
        g.setColor(new Color(56, 48, 44));
        g.fillRoundRect((int) (-9 + swing), -26, 8, 26, 5, 5);
        g.fillRoundRect((int) (1 - swing), -26, 8, 26, 5, 5);
        // torso
        g.setColor(body);
        g.fillRoundRect(-10, -56, 20, 32, 7, 7);
        // head + helm
        g.setColor(new Color(214, 204, 190));
        g.fillRoundRect(-8, -76, 16, 20, 6, 6);
        g.setColor(new Color(30, 26, 26));
        g.fillRect(-7, -68, 14, 4);

        // weapon
        double ang;
        if (p.action.equals("light")) ang = -1.5 + Math.min(1, p.actT / 0.24) * 2.6;
        else if (p.action.equals("heavy")) ang = -2.2 + Math.min(1, p.actT / 0.44) * 3.6;
        else ang = -0.5 + Math.sin(p.animT * 2) * 0.05;
        g.rotate(ang, 8, -46);
        g.setColor(new Color(60, 52, 48));
        g.fillRect(6, -50, 5, 14);
        g.setPaint(new GradientPaint(0, -110, new Color(230, 228, 235), 0, -46, new Color(150, 150, 160)));
        g.fillRect(6, -108, 6, 60);
        g.setTransform(old);

        if (p.iframes > 0 && p.action.equals("roll")) {
            g.setColor(new Color(255, 230, 170, 60));
            g.fill(new Ellipse2D.Double(x - 30, y - 70, 60, 70));
        }
    }

    void drawEnemy(Graphics2D g, Enemy e) {
        double s = e.scale;
        AffineTransform old = g.getTransform();
        g.translate(e.x, e.y);
        if (e.dead) g.rotate(Math.min(1, e.stateT * 2) * 1.5 * -e.face);
        g.scale(e.face * s, s);

        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(-20, 0, 40, 11));

        Color skin = e.boss ? (e.phase2 ? new Color(60, 38, 30) : new Color(48, 44, 52))
                : new Color(64, 60, 54);
        Color trim = e.phase2 ? new Color(235, 130, 40) : (e.boss ? new Color(150, 130, 90) : new Color(100, 92, 80));
        if (e.state.equals("hit")) { skin = new Color(210, 170, 170); }
        if (e.state.equals("windup")) trim = new Color(240, 90, 60);

        double sway = Math.sin(e.animT * (e.state.equals("walk") || Math.abs(e.vx) > 1 ? 10 : 2)) * 4;
        // legs
        g.setColor(skin.darker());
        g.fillRoundRect((int) (-10 + sway / 2), -28, 9, 28, 5, 5);
        g.fillRoundRect((int) (2 - sway / 2), -28, 9, 28, 5, 5);
        // torso
        g.setColor(skin);
        g.fillRoundRect(-14, -62, 28, 36, 8, 8);
        g.setColor(trim);
        g.fillRect(-14, -46, 28, 4);
        // head
        g.setColor(skin.brighter());
        g.fillRoundRect(-9, -82, 18, 22, 7, 7);
        g.setColor(e.phase2 ? new Color(255, 160, 60) : new Color(200, 60, 50));
        g.fill(new Ellipse2D.Double(-6, -75, 4, 4));
        g.fill(new Ellipse2D.Double(2, -75, 4, 4));

        // weapon arc
        double ang;
        if (e.state.equals("windup")) ang = -2.4 * Math.min(1, e.stateT / 0.5);
        else if (e.state.equals("strike")) ang = -2.4 + Math.min(1, e.stateT / 0.2) * 3.4;
        else if (e.state.equals("recover")) ang = 1.0;
        else ang = -0.3 + Math.sin(e.animT * 1.8) * 0.06;
        g.rotate(ang, 10, -50);
        g.setColor(new Color(50, 44, 40));
        g.fillRect(8, -54, 6, 16);
        g.setColor(e.boss ? new Color(200, 196, 200) : new Color(150, 148, 150));
        double blade = e.reach * 0.75 / s;
        g.fillRect(8, (int) (-46 - blade), 7, (int) blade);
        g.setTransform(old);

        if (!e.dead && !e.boss) {
            double w = 54, hpw = w * Math.max(0, e.hp / e.maxHp);
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect((int) (e.x - w / 2), (int) (GROUND - 104 * s), (int) w, 5);
            g.setColor(new Color(178, 40, 40));
            g.fillRect((int) (e.x - w / 2), (int) (GROUND - 104 * s), (int) hpw, 5);
        }
    }

    void drawHud(Graphics2D g) {
        bar(g, 26, 24, 320, 18, p.hp / p.maxHp, new Color(150, 32, 32), new Color(210, 60, 50));
        bar(g, 26, 48, 250, 13, p.fp / p.maxFp, new Color(28, 60, 110), new Color(70, 120, 190));
        bar(g, 26, 66, 280, 13, p.st / p.maxSt, new Color(50, 88, 44), new Color(110, 170, 90));

        // flasks
        for (int i = 0; i < p.maxFlasks; i++) {
            int fx = 28 + i * 26, fy = 92;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(fx, fy, 20, 26, 6, 6);
            g.setColor(i < p.flasks ? new Color(228, 176, 70) : new Color(60, 54, 48));
            g.fillRoundRect(fx + 3, fy + 3, 14, 20, 4, 4);
        }

        g.setFont(new Font("Serif", Font.PLAIN, 22));
        String r = "Runes  " + p.runes;
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(r, VIEW_W - 12 - g.getFontMetrics().stringWidth(r) + 2, VIEW_H - 24 + 2);
        g.setColor(new Color(226, 200, 138));
        g.drawString(r, VIEW_W - 12 - g.getFontMetrics().stringWidth(r), VIEW_H - 24);

        if (activeBoss != null && !activeBoss.dead) {
            int bw = 720, bx = (VIEW_W - bw) / 2, by = VIEW_H - 74;
            g.setColor(new Color(0, 0, 0, 190));
            g.fillRect(bx - 2, by - 2, bw + 4, 20);
            g.setColor(new Color(120, 24, 24));
            g.fillRect(bx, by, (int) (bw * Math.max(0, activeBoss.hp / activeBoss.maxHp)), 16);
            g.setColor(new Color(168, 140, 90));
            g.drawRect(bx - 2, by - 2, bw + 4, 20);
            g.setFont(new Font("Serif", Font.PLAIN, 19));
            int tw = g.getFontMetrics().stringWidth(activeBoss.name);
            g.setColor(new Color(224, 208, 176));
            g.drawString(activeBoss.name, (VIEW_W - tw) / 2, by - 10);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(190, 178, 158, 130));
        g.drawString("A/D move   W jump   SHIFT sprint   L roll   J light   K heavy   Q flask", 26, VIEW_H - 18);
    }

    void bar(Graphics2D g, int x, int y, int w, int h, double frac, Color back, Color fill) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(x - 2, y - 2, w + 4, h + 4);
        g.setColor(back.darker());
        g.fillRect(x, y, w, h);
        g.setPaint(new GradientPaint(0, y, fill, 0, y + h, back));
        g.fillRect(x, y, (int) (w * Math.max(0, Math.min(1, frac))), h);
        g.setColor(new Color(150, 130, 96, 180));
        g.drawRect(x - 2, y - 2, w + 4, h + 4);
    }

    void drawMessage(Graphics2D g) {
        if (messageT <= 0 || !started) return;
        float a = (float) Math.min(1, messageT / 0.8);
        g.setColor(new Color(0, 0, 0, (int) (a * 120)));
        g.fillRect(0, VIEW_H / 2 - 70, VIEW_W, 140);
        g.setFont(new Font("Serif", Font.PLAIN, 54));
        int tw = g.getFontMetrics().stringWidth(message);
        g.setColor(new Color(214, 42, 42, (int) (a * 255)));
        if (message.equals("YOU DIED"))
            g.drawString(message, (VIEW_W - tw) / 2, VIEW_H / 2);
        else {
            g.setColor(new Color(232, 214, 170, (int) (a * 255)));
            g.drawString(message, (VIEW_W - tw) / 2, VIEW_H / 2);
        }
        g.setFont(new Font("Serif", Font.PLAIN, 20));
        int sw = g.getFontMetrics().stringWidth(messageSub);
        g.setColor(new Color(200, 186, 158, (int) (a * 220)));
        g.drawString(messageSub, (VIEW_W - sw) / 2, VIEW_H / 2 + 36);
    }

    void drawTitle(Graphics2D g) {
        g.setColor(new Color(6, 5, 8, 215));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setFont(new Font("Serif", Font.PLAIN, 76));
        String t = "ASHEN VALE";
        int tw = g.getFontMetrics().stringWidth(t);
        g.setColor(new Color(228, 206, 156));
        g.drawString(t, (VIEW_W - tw) / 2, 230);
        g.setFont(new Font("Serif", Font.PLAIN, 22));
        String s = "press any key to begin";
        int sw = g.getFontMetrics().stringWidth(s);
        g.setColor(new Color(190, 176, 150, (int) (160 + 80 * Math.sin(time * 3))));
        g.drawString(s, (VIEW_W - sw) / 2, 280);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        String c = "A / D  move      W or SPACE  jump      SHIFT  sprint      L  roll      J  light attack      K  heavy attack      Q  flask";
        int cw = g.getFontMetrics().stringWidth(c);
        g.setColor(new Color(160, 150, 134));
        g.drawString(c, (VIEW_W - cw) / 2, 360);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Ashen Vale");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            AshenVale panel = new AshenVale();
            f.add(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setResizable(false);
            f.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
