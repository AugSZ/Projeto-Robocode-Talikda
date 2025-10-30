package Robocode01;


public class MovimentacaoDoRobo {

    double perdaEnergia = energiaAnterior - e.getEnergy();
	
    if (perdaEnergia > 0 && perdaEnergia <= 3.0) {
        executarDefesa();
    }

    double anguloMovimento = Utils.normalRelativeAngleDegrees(e.getBearing() + 90 - (15 * moveDirection));
    
	setTurnRight(anguloMovimento);
    setAhead(100 * moveDirection);

    moveDirection *= -1; // Alternância de direção em defesa, colisão, parede
}
