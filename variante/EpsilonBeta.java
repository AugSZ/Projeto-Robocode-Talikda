package variante;

import robocode.*;
import java.awt.*;

/**
 * Perseguidor
 * <p/>
 * Foca em um robô, chega perto, e atira de perto, sufocando-o.
 * 
 * Problemas: robô em longas batalhas perde por superaquecimento
 * robos como wall e spin, ele perde facilmente
 */
public class EpsilonBeta extends AdvancedRobot {
	int moveDirection=1;//Como ele vai se movimentar
	/**
	 * run:  Função principal de movimentação
	 */
	public void run() {
    	setAdjustRadarForRobotTurn(true);
    	setAdjustGunForRobotTurn(true);

    	setBodyColor(new Color(0, 0, 0));
    	setGunColor(new Color(0, 75, 67));
    	setRadarColor(new Color(0, 75, 67));
    	setScanColor(Color.white);
    	setBulletColor(Color.blue);

    	// Mantém o radar girando infinitamente, mas de forma assíncrona
    	while (true) {
        	setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        	execute(); // 🔥 essencial: executa os comandos setXxx()
    }
}


	/**
	 * onScannedRobot: O que o robô faz se localizar um inimigo no radar
	 */
	double previousEnemyEnergy = 100; // variável global no topo da classe

public void onScannedRobot(ScannedRobotEvent e) {
    double absBearing = e.getBearingRadians() + getHeadingRadians();
    double latVel = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
    double gunTurnAmt;
    setTurnRadarLeftRadians(getRadarTurnRemainingRadians());
    
	// 🔁 Travar o radar no inimigo (substitui o lock antigo)
    setTurnRadarRightRadians(2 * robocode.util.Utils.normalRelativeAngle(
        absBearing - getRadarHeadingRadians()
    ));

    // 🔎 Detecção de disparo inimigo
    double changeInEnergy = previousEnemyEnergy - e.getEnergy();
    if (changeInEnergy > 0 && changeInEnergy <= 3) {
        // Movimento aleatório curto — tentativa de desvio
        double evasionAngle = (Math.random() - 0.5) * Math.PI / 2; // entre -45° e +45°
        double moveAmount = 100 + Math.random() * 100; // 100–200 px
        setTurnRightRadians(evasionAngle);
        setAhead(moveAmount * (Math.random() > 0.5 ? 1 : -1));
    }
    previousEnemyEnergy = e.getEnergy();
    
    // 🔫 Lógica original de ataque
    if (Math.random() > .9) setMaxVelocity((12 * Math.random()) + 12);
    if (e.getDistance() > 150) {
    	// --- Previsão do movimento do inimigo (lead shot) ---
    	double firePower = Math.min(400 / e.getDistance(), getEnergy() / 5);
    	double bulletSpeed = 20 - 3 * firePower;
    	double time = e.getDistance() / bulletSpeed;

    	double futureX = getX() + e.getVelocity() * time * Math.sin(e.getHeadingRadians());
    	double futureY = getY() + e.getVelocity() * time * Math.cos(e.getHeadingRadians());
    	double theta = Math.atan2(futureX - getX(), futureY - getY());

    	// Mira no ponto futuro
    	setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(theta - getGunHeadingRadians()));

    	// Movimento e tiro
    	setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(absBearing - getHeadingRadians() + latVel / getVelocity()));
    	setAhead((e.getDistance() - 140) * moveDirection);
    	setFire(firePower);
	} 
	else {
		double firePower = Math.min(400 / e.getDistance(), getEnergy() / 5); // pode ajustar se quiser mais leve
        gunTurnAmt = robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + latVel / 15);
        setTurnGunRightRadians(gunTurnAmt);
        setTurnLeft(-90 - e.getBearing());
        setAhead((e.getDistance() - 140) * moveDirection);
        if (getGunHeat() == 0 && getEnergy() > 1){ 
    	setFire(firePower);
		}
    }
	if (Math.random() > 0.8) moveDirection *= -1;

	//evita encurralamento na parede
	if (getX() < 50 || getX() > getBattleFieldWidth() - 50 || 
    getY() < 50 || getY() > getBattleFieldHeight() - 50) {
    moveDirection = -moveDirection;
}
	//Se a energia estiver baixa, foge
	if (getEnergy() < 20) setTurnRight(90); setAhead(200);

}


	public void onHitWall(HitWallEvent e){
		moveDirection=-moveDirection;//direção reversa
	}

	public void onBulletHit(BulletHitEvent e){
	double energiaPerdidaInimigo;
	energiaPerdidaInimigo = robocode.Rules.getBulletDamage(e.getBullet().getPower()); // calcula quanto de energia o inimigo perdeu
}

public void onHitByBullet (HitByBulletEvent e){
	double energiaGanhaInimigo;
	energiaGanhaInimigo = robocode.Rules.getBulletHitBonus(e.getBullet().getPower()); // calcula quanto de energia o inimigo ganha acertando tiro

}


	/**
	 * onWin: TeaBag no inimigo fds
	 */
	public void onWin(WinEvent e) {
		for (int i = 0; i < 50; i++) {
			turnRight(30);
			turnLeft(30);
		}
	}
}
