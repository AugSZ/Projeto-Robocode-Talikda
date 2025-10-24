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
public class EpsilonGamma extends AdvancedRobot {
	int moveDirection=1;//Como ele vai se movimentar
	/**
	 * run:  Função principal de movimentação
	 */
	public void run() {
		setAdjustRadarForRobotTurn(true);//Mantém o radar parado, enquanto se movimenta
		setBodyColor(new Color(0, 0, 0));		// 
		setGunColor(new Color(0, 75, 67));			// Define as cores do robô
		setRadarColor(new Color(0, 75, 67));		// 
		setScanColor(Color.white);					// Cor do scanner
		setBulletColor(Color.blue);					// Cor da bala
		setAdjustGunForRobotTurn(true); // Mantém o canhão estável no movimento
		turnRadarRightRadians(Double.POSITIVE_INFINITY);//Mantém o radar se movimentando para direita
	}

	/**
	 * onScannedRobot: O que o robô faz se localizar um inimigo no radar
	 */
	double previousEnemyEnergy = 100; // variável global no topo da classe

public void onScannedRobot(ScannedRobotEvent e) {
    double firePower = Math.min(400 / e.getDistance(), getEnergy() / 5);
    double absBearing = e.getBearingRadians() + getHeadingRadians();
    double latVel = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
    double gunTurnAmt;
    setTurnRadarLeftRadians(getRadarTurnRemainingRadians());
    
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
        gunTurnAmt = robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + latVel / 22);
        setTurnGunRightRadians(gunTurnAmt);
        setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(absBearing - getHeadingRadians() + latVel / getVelocity()));
        setAhead((e.getDistance() - 140) * moveDirection);
        setFire(3);
    } else {
        gunTurnAmt = robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + latVel / 15);
        setTurnGunRightRadians(gunTurnAmt);
        setTurnLeft(-90 - e.getBearing());
        setAhead((e.getDistance() - 140) * moveDirection);
        if (getGunHeat() == 0 && getEnergy() > 1){ 
    	    setFire(firePower);
		}
    }
	
	//evita encurralamento na parede
	if (getX() < 50 || getX() > getBattleFieldWidth() - 50 || 
    getY() < 50 || getY() > getBattleFieldHeight() - 50) {
    moveDirection = -moveDirection;
}
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


}
