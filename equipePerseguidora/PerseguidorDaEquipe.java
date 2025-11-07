package inicial;

import robocode.*;
import java.awt.*;
import robocode.Droid;
import robocode.TeamRobot;

/**
 * Perseguidor
 * <p/>
 * Foca em um robô, chega perto, e atira de perto, sufocando-o.
 * 
 * Problemas: robô em longas batalhas perde por superaquecimento
 * robos como wall e spin, ele perde facilmente
 */
public class perseguidor extends TeamRobot implements Droid {
	int moveDirection=1;//Como ele vai se movimentar
	/**
	 * run:  Função principal de movimentação
	 */
	public void run() {
		setAdjustRadarForRobotTurn(true);//Mantém o radar parado, enquanto se movimenta
		setBodyColor(new Color(128, 128, 50));		// 
		setGunColor(new Color(50, 50, 20));			// Define as cores do robô
		setRadarColor(new Color(200, 200, 70));		// 
		setScanColor(Color.white);					// Cor do scanner
		setBulletColor(Color.blue);					// Cor da bala
		setAdjustGunForRobotTurn(true); // Mantém o canhão estável no movimento
		turnRadarRightRadians(Double.POSITIVE_INFINITY);//Mantém o radar se movimentando para direita
	}

	/**
	 * onScannedRobot: O que o robô faz se localizar um inimigo no radar
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		double absBearing=e.getBearingRadians()+getHeadingRadians();//Pega o angulo/bearing do inimigo
		double latVel=e.getVelocity() * Math.sin(e.getHeadingRadians() -absBearing);//Velocidade do inimigo
		double gunTurnAmt;//amount to turn our gun (não sei oq signifca, estava aqui antes. Não tirei pra não dar merda)
		setTurnRadarLeftRadians(getRadarTurnRemainingRadians());// Lock no radar
		if(Math.random()>.9){
			setMaxVelocity((12*Math.random())+12);//Muda a velocidade de forma aleatória para fugir da previsibilidade do tiro
		}
		if (e.getDistance() > 150) {//Se a distancia do inimigo for maior que 150 px
			gunTurnAmt = robocode.util.Utils.normalRelativeAngle(absBearing- getGunHeadingRadians()+latVel/22);//quantidade para virar o canhão, aos pouquinhos
			setTurnGunRightRadians(gunTurnAmt); //virar a arma
			setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(absBearing-getHeadingRadians()+latVel/getVelocity()));//Vai no inimigo prevendo a localização dele (MUITO FODA ESSA LINHA)
			setAhead((e.getDistance() - 140)*moveDirection);//vai para frente
			setFire(3);//atira ai ai ai
		}
		else{//se estamos mais perto
			gunTurnAmt = robocode.util.Utils.normalRelativeAngle(absBearing- getGunHeadingRadians()+latVel/15);//
			setTurnGunRightRadians(gunTurnAmt);//vira a arma
			setTurnLeft(-90-e.getBearing()); //vira perpendicularmente ao inimigo
			setAhead((e.getDistance() - 140)*moveDirection);//vai para frente
			setFire(3);//atira ai ai ai ta doendo
		}	
	}

	public void onHitWall(HitWallEvent e){
		moveDirection=-moveDirection;//direção reversa
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
