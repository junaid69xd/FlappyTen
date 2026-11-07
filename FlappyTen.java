import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.nio.file.*;
import java.util.Random;

/**
 * FLAPPY TEN - a small Flappy Bird style desktop game written in Java Swing.
 *
 * Goal: fly the bird through 10 pillars (pipes) to win.
 * Controls: SPACE / UP arrow / mouse click = flap, P = pause,
 *           A = autopilot demo, ESC = quit.
 *
 * All graphics are simple flat shapes (no image files needed).
 */
public class FlappyTen extends JPanel implements ActionListener, KeyListener, MouseListener {

    // ---------- Game settings (change these to make the game easier/harder) ----------
    static final int W = 400, H = 600;      // window size
    static final int GROUND = 80;           // height of the ground strip
    static final int TOTAL_PIPES = 10;      // how many pillars to pass to win
    static final int PIPE_W = 64;           // pipe width
    static final int GAP = 160;             // empty space between top and bottom pipe
    static final int PIPE_SPACING = 230;    // distance between two pipes
    static final int SPEED = 3;             // how fast the world scrolls left
    static final int BIRD_X = 100;          // bird stays at this x position
    static final int BIRD_R = 14;           // bird radius
    static final double GRAVITY = 0.5;      // downward pull each frame
    static final double FLAP = -8.0;        // upward speed when flapping

    enum State { MENU, PLAYING, PAUSED, GAME_OVER, WON }

    // ---------- Game data ----------
    State state = State.MENU;
    double birdY, birdVy;
    int[] pipeX = new int[TOTAL_PIPES];     // x position of each pipe
    int[] gapY = new int[TOTAL_PIPES];      // y where the gap starts
    boolean[] passed = new boolean[TOTAL_PIPES];
    int score, best, groundOffset, frame, cooldown;
    boolean autopilot, keyDown;
    double[] cloudX = {40, 190, 330};
    int[] cloudY = {90, 150, 60};
    Random rand = new Random();
    Timer timer = new Timer(16, this);      // about 60 frames per second
    final File bestFile = new File(System.getProperty("user.home"), ".flappy_ten_best.txt");

    public FlappyTen() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        loadBest();
        resetGame();
    }

    // ---------- Setup ----------
    void resetGame() {
        birdY = H / 2.0 - 40;
        birdVy = 0;
        score = 0;
        for (int i = 0; i < TOTAL_PIPES; i++) {
            pipeX[i] = W + 120 + i * PIPE_SPACING;
            gapY[i] = 70 + rand.nextInt(H - GROUND - GAP - 140);
            passed[i] = false;
        }
    }

    void loadBest() {
        try {
            best = Integer.parseInt(new String(Files.readAllBytes(bestFile.toPath())).trim());
        } catch (Exception e) {
            best = 0;
        }
    }

    void saveBest() {
        if (score > best) {
            best = score;
            try {
                Files.write(bestFile.toPath(), String.valueOf(best).getBytes());
            } catch (Exception e) { /* ignore: saving the best score is optional */ }
        }
    }

    // ---------- Game logic (runs every frame) ----------
    void update() {
        frame++;
        if (cooldown > 0) cooldown--;

        // clouds drift slowly (always)
        for (int i = 0; i < cloudX.length; i++) {
            cloudX[i] -= 0.4;
            if (cloudX[i] < -80) cloudX[i] = W + 20;
        }

        if (state == State.MENU) {
            groundOffset = (groundOffset + SPEED) % 24;
            birdY = H / 2.0 - 40 + Math.sin(frame * 0.08) * 8;   // bird bobs up and down
            return;
        }
        if (state == State.PAUSED) return;

        if (state == State.PLAYING) {
            if (autopilot) runAutopilot();

            groundOffset = (groundOffset + SPEED) % 24;
            for (int i = 0; i < TOTAL_PIPES; i++) pipeX[i] -= SPEED;

            birdVy += GRAVITY;
            birdY += birdVy;

            // keep the bird below the top of the screen
            if (birdY - BIRD_R < 0) { birdY = BIRD_R; birdVy = 0; }

            // count passed pipes
            for (int i = 0; i < TOTAL_PIPES; i++) {
                if (!passed[i] && pipeX[i] + PIPE_W < BIRD_X - BIRD_R) {
                    passed[i] = true;
                    score++;
                }
            }
            if (score == TOTAL_PIPES) {
                state = State.WON;
                birdY = 110;                  // bird celebrates above the message
                birdVy = 0;
                cooldown = 40;
                saveBest();
                return;
            }

            // hit the ground?
            if (birdY + BIRD_R >= H - GROUND) {
                birdY = H - GROUND - BIRD_R;
                crash();
                return;
            }
            // hit a pipe? (hit box is a little smaller than the drawing = fair)
            int r = BIRD_R - 2;
            for (int i = 0; i < TOTAL_PIPES; i++) {
                boolean overlapX = BIRD_X + r > pipeX[i] && BIRD_X - r < pipeX[i] + PIPE_W;
                boolean outsideGap = birdY - r < gapY[i] || birdY + r > gapY[i] + GAP;
                if (overlapX && outsideGap) { crash(); return; }
            }
        } else if (state == State.GAME_OVER) {
            // bird falls to the ground after crashing
            if (birdY + BIRD_R < H - GROUND) {
                birdVy += GRAVITY;
                birdY = Math.min(birdY + birdVy, H - GROUND - BIRD_R);
            }
        }
    }

    void crash() {
        state = State.GAME_OVER;
        cooldown = 40;
        autopilot = false;
        saveBest();
    }

    // A very simple bot used for the demo mode (press A)
    void runAutopilot() {
        double target = H / 2.0;
        for (int i = 0; i < TOTAL_PIPES; i++) {
            if (pipeX[i] + PIPE_W > BIRD_X - BIRD_R) {          // next pipe in front of us
                target = gapY[i] + GAP / 2.0 + 30;
                break;
            }
        }
        if (birdY > target && birdVy >= 0) birdVy = FLAP;
    }

    // ---------- Player actions ----------
    void action() {
        if (state == State.MENU) {
            resetGame();
            state = State.PLAYING;
            birdVy = FLAP;
        } else if (state == State.PLAYING) {
            birdVy = FLAP;
        } else if ((state == State.GAME_OVER || state == State.WON) && cooldown == 0) {
            resetGame();
            state = State.PLAYING;
            birdVy = FLAP;
        }
    }

    // ---------- Drawing ----------
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        // no anti-aliasing on shapes = chunky, low-graphics look
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // sky
        g.setColor(new Color(135, 206, 235));
        g.fillRect(0, 0, W, H);

        // clouds
        g.setColor(Color.WHITE);
        for (int i = 0; i < cloudX.length; i++) {
            int cx = (int) cloudX[i], cy = cloudY[i];
            g.fillOval(cx, cy, 50, 24);
            g.fillOval(cx + 18, cy - 10, 36, 26);
            g.fillOval(cx + 34, cy, 44, 22);
        }

        // pipes
        for (int i = 0; i < TOTAL_PIPES; i++) {
            int x = pipeX[i];
            if (x > W || x + PIPE_W < 0) continue;
            drawPipe(g, x, 0, PIPE_W, gapY[i], true);
            drawPipe(g, x, gapY[i] + GAP, PIPE_W, H - GROUND - (gapY[i] + GAP), false);
            // pipe number so the player can see "10 pillars"
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(Color.WHITE);
            String n = String.valueOf(i + 1);
            g.drawString(n, x + (PIPE_W - g.getFontMetrics().stringWidth(n)) / 2, gapY[i] - 32);
        }

        // ground
        g.setColor(new Color(222, 216, 149));
        g.fillRect(0, H - GROUND, W, GROUND);
        g.setColor(new Color(115, 191, 46));
        g.fillRect(0, H - GROUND, W, 14);
        g.setColor(new Color(90, 160, 35));
        for (int x = -24; x < W + 24; x += 24) g.fillRect(x - groundOffset, H - GROUND + 14, 12, 6);

        drawBird(g);
        drawHud(g);
        drawOverlay(g);
    }

    void drawPipe(Graphics2D g, int x, int y, int w, int h, boolean top) {
        if (h <= 0) return;
        Color body = new Color(84, 180, 60), dark = new Color(45, 110, 35);
        g.setColor(body);
        g.fillRect(x, y, w, h);
        g.setColor(dark);
        g.drawRect(x, y, w, h);
        int capH = 22;
        int capY = top ? y + h - capH : y;       // cap sits at the gap edge
        g.setColor(body);
        g.fillRect(x - 4, capY, w + 8, capH);
        g.setColor(dark);
        g.drawRect(x - 4, capY, w + 8, capH);
    }

    void drawBird(Graphics2D g) {
        AffineTransform old = g.getTransform();
        double angle = Math.max(-0.5, Math.min(1.2, birdVy * 0.06));   // tilt up/down
        g.translate(BIRD_X, birdY);
        g.rotate(angle);
        g.setColor(new Color(250, 220, 60));                // body
        g.fillOval(-BIRD_R, -BIRD_R, BIRD_R * 2, BIRD_R * 2);
        g.setColor(new Color(120, 90, 0));
        g.drawOval(-BIRD_R, -BIRD_R, BIRD_R * 2, BIRD_R * 2);
        g.setColor(new Color(235, 180, 30));                // wing
        g.fillOval(-12, -2, 14, 9);
        g.setColor(Color.WHITE);                            // eye
        g.fillOval(3, -10, 10, 10);
        g.setColor(Color.BLACK);
        g.fillOval(8, -7, 4, 4);
        g.setColor(new Color(240, 100, 30));                // beak
        g.fillRect(10, 1, 11, 6);
        g.setTransform(old);
    }

    void drawText(Graphics2D g, String s, int cx, int y, Font f, Color c) {
        g.setFont(f);
        int x = cx - g.getFontMetrics().stringWidth(s) / 2;
        g.setColor(new Color(0, 0, 0, 140));
        g.drawString(s, x + 2, y + 2);                      // little shadow
        g.setColor(c);
        g.drawString(s, x, y);
    }

    void drawHud(Graphics2D g) {
        if (state == State.PLAYING || state == State.PAUSED) {
            drawText(g, String.valueOf(score), W / 2, 70, new Font("SansSerif", Font.BOLD, 52), Color.WHITE);
            drawText(g, "Pillars: " + score + " / " + TOTAL_PIPES, W / 2, 98,
                     new Font("SansSerif", Font.BOLD, 14), Color.WHITE);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(new Color(40, 40, 40));
        g.drawString("Best: " + best, 10, H - 30);
        if (autopilot) g.drawString("AUTOPILOT (press A to stop)", 10, H - 12);
    }

    void drawOverlay(Graphics2D g) {
        Font big = new Font("SansSerif", Font.BOLD, 40);
        Font mid = new Font("SansSerif", Font.BOLD, 18);
        Font small = new Font("SansSerif", Font.PLAIN, 14);
        if (state == State.MENU) {
            drawText(g, "FLAPPY TEN", W / 2, 150, new Font("SansSerif", Font.BOLD, 46), new Color(255, 230, 80));
            drawText(g, "Pass 10 pillars to win!", W / 2, 190, mid, Color.WHITE);
            drawText(g, "Press SPACE or click to start", W / 2, 400, mid, Color.WHITE);
            drawText(g, "P = pause    A = autopilot demo    ESC = quit", W / 2, 430, small, Color.WHITE);
        } else if (state == State.PAUSED) {
            drawText(g, "PAUSED", W / 2, 260, big, Color.WHITE);
            drawText(g, "Press P to continue", W / 2, 295, mid, Color.WHITE);
        } else if (state == State.GAME_OVER || state == State.WON) {
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(40, 170, W - 80, 230, 20, 20);
            if (state == State.WON) {
                drawText(g, "YOU WIN!", W / 2, 225, big, new Color(255, 230, 80));
                drawText(g, "You passed all 10 pillars!", W / 2, 262, mid, Color.WHITE);
            } else {
                drawText(g, "GAME OVER", W / 2, 225, big, new Color(255, 110, 90));
                drawText(g, "Pillars passed: " + score + " / " + TOTAL_PIPES, W / 2, 262, mid, Color.WHITE);
            }
            drawText(g, "Best: " + best, W / 2, 295, mid, Color.WHITE);
            drawText(g, "Press SPACE or click to play again", W / 2, 345, small, Color.WHITE);
        }
    }

    // ---------- Events ----------
    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }

    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if ((k == KeyEvent.VK_SPACE || k == KeyEvent.VK_UP) && !keyDown) {
            keyDown = true;                       // ignore key repeat while holding the key
            action();
        } else if (k == KeyEvent.VK_P) {
            if (state == State.PLAYING) state = State.PAUSED;
            else if (state == State.PAUSED) state = State.PLAYING;
        } else if (k == KeyEvent.VK_A) {
            autopilot = !autopilot;
            if (autopilot && state == State.MENU) action();
        } else if (k == KeyEvent.VK_ESCAPE) {
            System.exit(0);
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_UP) keyDown = false;
    }
    @Override public void keyTyped(KeyEvent e) { }
    @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); action(); }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }

    // ---------- Start the program ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Flappy Ten");
            FlappyTen game = new FlappyTen();
            frame.add(game);
            frame.pack();
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
            game.requestFocusInWindow();
            game.timer.start();
        });
    }
}
