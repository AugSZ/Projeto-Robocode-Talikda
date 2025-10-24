package variante;

import robocode.*;
import robocode.util.Utils;
import java.awt.*;

/**
 * EpsilonAlphaV2
 * Perseguidor aprimorado com movimentação lateral, evasão e mira preditiva.
 */
public class teste extends AdvancedRobot {

    int moveDirection = 1;
    double previousEnemyEnergy = 100;

    public void run() {
        setAdjustRadarForRobotTurn(true);
        setAdjustGunForRobotTurn(true);
        setBodyColor(new Color(0, 0, 0));
        setGunColor(new Color(0, 75, 67));
        setRadarColor(new Color(0, 75, 67));
        setScanColor(Color.white);
        setBulletColor(Color.blue);

        while (true) {
            // Radar infinito
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double absBearing = e.getBearingRadians() + getHeadingRadians();
        double latVel = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
        double gunTurnAmt;

        // Mantém radar travado no inimigo
        setTurnRadarLeftRadians(getRadarTurnRemainingRadians());

        // Detecta disparos inimigos
        double changeInEnergy = previousEnemyEnergy - e.getEnergy();
        if (changeInEnergy > 0 && changeInEnergy <= 3) {
            // Evasão aleatória
            double evasionAngle = (Math.random() - 0.5) * Math.PI / 2;
            double moveAmount = 100 + Math.random() * 100;
            setTurnRightRadians(evasionAngle);
            setAhead(moveAmount * (Math.random() > 0.5 ? 1 : -1));
            moveDirection *= -1; // Alterna direção para tornar padrão imprevisível
        }
        previousEnemyEnergy = e.getEnergy();

        // Movimentação lateral avançada
        double turnAngle = Utils.normalRelativeAngle(absBearing - getHeadingRadians() + Math.PI/2);
        setTurnRightRadians(turnAngle);
        setAhead(100 * moveDirection);

        // Mira preditiva simples
        if (e.getDistance() > 150) {
            gunTurnAmt = Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + latVel / 22);
            setTurnGunRightRadians(gunTurnAmt);
            setFire(3);
        } else {
            gunTurnAmt = Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + latVel / 15);
            setTurnGunRightRadians(gunTurnAmt);
            setFire(3);
        }

        // Velocidade variável para evitar padrões previsíveis
        if (Math.random() > 0.8) {
            setMaxVelocity(8 + Math.random() * 4);
        }
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
        setBack(50); // Recua ao bater na parede
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Pequeno recuo ao ser atingido
        moveDirection = -moveDirection;
        setAhead(50);
    }

    public void onBulletHit(BulletHitEvent e) {
        // Nada adicional, mas poderia implementar score tracking
    }
}
