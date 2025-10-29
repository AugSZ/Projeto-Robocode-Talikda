package JackChan;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

import robocode.*;
import robocode.util.Utils;

/** 
 * RoboMegaMachoPotente
 *
 * Robô avançado do Robocode com movimentação adaptativa, mira preditiva e
 * aprendizado estatístico por ondas ("Wave Surfing").
 *
 * Combina estratégias de evasão e previsão de disparos para batalhas 1v1 e
 * múltiplos inimigos.
 */
public class RoboMegaMachoPotente extends AdvancedRobot {

    // =======================
    // VARIÁVEIS GERAIS
    // =======================
    static double POTENCIA_TIRO = 3;
    static final int QUANTIDADE_PONTOS_PREVISTOS = 150;
    static final double MARGEM_PAREDE = 18;

    static Random aleatorio = new Random();

    HashMap<String, Robo> listaInimigos = new HashMap<>();
    Robo meuRobo = new Robo();
    Robo alvo;
    List<Point2D.Double> posicoesPossiveis = new ArrayList<>();
    Point2D.Double pontoAlvo = new Point2D.Double(60, 60);
    Rectangle2D.Double campoBatalha = new Rectangle2D.Double();

    int tempoInativo = 30;
    private static double direcaoLateral;
    private static double velocidadeInimigoAnterior;
    private static Movimento1v1 movimento1v1;

    // =======================
    // CLASSE INTERNA: MODELO DE ROBO
    // =======================
    class Robo extends Point2D.Double {
        public long tempoVarredura;
        public boolean vivo = true;
        public String nome;
        double anguloCanhaoRadianos, anguloAbsolutoRadianos;
        double velocidade, direcao, ultimaDirecao;
        double pontuacaoDisparo, distancia, energia;
    }

    // =======================
    // CLASSE UTILITÁRIA
    // =======================
    public static class Utilitario {

        static double limitar(double valor, double min, double max) {
            return Math.max(min, Math.min(max, valor));
        }

        static double aleatorioEntre(double min, double max) {
            return min + Math.random() * (max - min);
        }

        static Point2D projetar(Point2D origem, double angulo, double distancia) {
            return new Point2D.Double(
                    origem.getX() + Math.sin(angulo) * distancia,
                    origem.getY() + Math.cos(angulo) * distancia);
        }

        static double anguloAbsoluto(Point2D origem, Point2D alvo) {
            return Math.atan2(alvo.getX() - origem.getX(), alvo.getY() - origem.getY());
        }

        static int sinal(double v) {
            return v < 0 ? -1 : 1;
        }
    }

    // =======================
    // CLASSE DE MOVIMENTO 1v1 (Evasão e posicionamento)
    // =======================
    class Movimento1v1 {
        private static final double LARGURA_CAMPO = 800;
        private static final double ALTURA_CAMPO = 600;
        private static final double TEMPO_MAX_TENTATIVA = 125;
        private static final double AJUSTE_REVERSA = 0.421075;
        private static final double EVASAO_PADRAO = 1.2;
        private static final double AJUSTE_QUIQUE_PAREDE = 0.699484;

        private final AdvancedRobot robo;
        private final Rectangle2D areaDisparo = new Rectangle2D.Double(
                MARGEM_PAREDE, MARGEM_PAREDE,
                LARGURA_CAMPO - MARGEM_PAREDE * 2,
                ALTURA_CAMPO - MARGEM_PAREDE * 2);

        private double direcao = 0.4;

        Movimento1v1(AdvancedRobot r) {
            this.robo = r;
        }

        /**
         * Estratégia de evasão e movimentação dinâmica em combate 1v1.
         * Calcula um ponto de destino que reduz o risco de impacto do inimigo.
         */
        public void onScannedRobot(ScannedRobotEvent e) {
            Robo inimigo = new Robo();
            inimigo.anguloAbsolutoRadianos = robo.getHeadingRadians() + e.getBearingRadians();
            inimigo.distancia = e.getDistance();

            Point2D posicaoRobo = new Point2D.Double(robo.getX(), robo.getY());
            Point2D posicaoInimigo = Utilitario.projetar(posicaoRobo, inimigo.anguloAbsolutoRadianos, inimigo.distancia);
            Point2D destinoRobo;

            double tempoTentativa = 0;
            while (!areaDisparo.contains(
                    destinoRobo = Utilitario.projetar(
                            posicaoInimigo,
                            inimigo.anguloAbsolutoRadianos + Math.PI + direcao,
                            inimigo.distancia * (EVASAO_PADRAO - tempoTentativa / 100.0)))
                    && tempoTentativa < TEMPO_MAX_TENTATIVA) {
                tempoTentativa++;
            }

            if ((Math.random() < (Rules.getBulletSpeed(POTENCIA_TIRO) / AJUSTE_REVERSA) / inimigo.distancia)
                    || tempoTentativa > (inimigo.distancia / Rules.getBulletSpeed(POTENCIA_TIRO) / AJUSTE_QUIQUE_PAREDE))
                direcao = -direcao;

            double angulo = Utilitario.anguloAbsoluto(posicaoRobo, destinoRobo) - robo.getHeadingRadians();
            robo.setAhead(Math.cos(angulo) * 100);
            robo.setTurnRightRadians(Math.tan(angulo));
        }
    }

    // =======================
    // CLASSE DE ONDAS (Wave Surfing e Mira Estatística)
    // =======================
    static class Onda extends Condition {
        static Point2D posicaoAlvo;
        double potenciaTiro;
        Point2D posicaoCanhao;
        double angulo;
        double direcaoLateral;

        private static final double DISTANCIA_MAXIMA = 900;
        private static final int INDICES_DISTANCIA = 5;
        private static final int INDICES_VELOCIDADE = 5;
        private static final int BINS = 25;
        private static final int BIN_CENTRAL = (BINS - 1) / 2;
        private static final double ANGULO_ESCAPE_MAXIMO = 0.7;
        private static final double LARGURA_BIN = ANGULO_ESCAPE_MAXIMO / (double) BIN_CENTRAL;
        private static final int[][][][] buffersEstatisticos =
                new int[INDICES_DISTANCIA][INDICES_VELOCIDADE][INDICES_VELOCIDADE][BINS];

        private int[] buffer;
        private double distanciaPercorrida;
        private final AdvancedRobot robo;

        Onda(AdvancedRobot r) {
            this.robo = r;
        }

        /**
         * Atualiza o avanço da onda e registra o resultado se ela atingir o alvo.
         */
        public boolean test() {
            avancar();
            if (chegou()) {
                buffer[binAtual()]++;
                robo.removeCustomEvent(this);
            }
            return false;
        }

        double offsetAnguloMaisVisitado() {
            return (direcaoLateral * LARGURA_BIN) * (binMaisVisitado() - BIN_CENTRAL);
        }

        void definirSegmentacoes(double distancia, double velocidade, double ultimaVelocidade) {
            int indiceDistancia = (int) (distancia / (DISTANCIA_MAXIMA / INDICES_DISTANCIA));
            int indiceVelocidade = (int) Math.abs(velocidade / 2);
            int indiceUltimaVelocidade = (int) Math.abs(ultimaVelocidade / 2);
            buffer = buffersEstatisticos[indiceDistancia][indiceVelocidade][indiceUltimaVelocidade];
        }

        private void avancar() {
            distanciaPercorrida += Rules.getBulletSpeed(potenciaTiro);
        }

        private boolean chegou() {
            return distanciaPercorrida > posicaoCanhao.distance(posicaoAlvo) - MARGEM_PAREDE;
        }

        private int binAtual() {
            int bin = (int) Math.round(((Utils.normalRelativeAngle(
                    Utilitario.anguloAbsoluto(posicaoCanhao, posicaoAlvo) - angulo))
                    / (direcaoLateral * LARGURA_BIN)) + BIN_CENTRAL);
            return (int) Utilitario.limitar(bin, 0, BINS - 1);
        }

        private int binMaisVisitado() {
            int maisVisitado = BIN_CENTRAL;
            for (int i = 0; i < BINS; i++)
                if (buffer[i] > buffer[maisVisitado])
                    maisVisitado = i;
            return maisVisitado;
        }
    }

    // =======================
    // CONFIGURAÇÃO E CICLO PRINCIPAL
    // =======================
    {
        movimento1v1 = new Movimento1v1(this);
    }

    public void run() {
        campoBatalha.height = getBattleFieldHeight();
        campoBatalha.width = getBattleFieldWidth();

        meuRobo.x = getX();
        meuRobo.y = getY();
        meuRobo.energia = getEnergy();

        pontoAlvo.x = meuRobo.x;
        pontoAlvo.y = meuRobo.y;

        alvo = new Robo();
        alvo.vivo = false;

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Modo de combate múltiplo
        if (getOthers() > 1) {
            atualizarListaPosicoes(QUANTIDADE_PONTOS_PREVISTOS);
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

            while (true) {
                meuRobo.ultimaDirecao = meuRobo.direcao;
                meuRobo.direcao = getHeadingRadians();
                meuRobo.x = getX();
                meuRobo.y = getY();
                meuRobo.energia = getEnergy();
                meuRobo.anguloCanhaoRadianos = getGunHeadingRadians();

                // Atualiza inimigos (marca como mortos se não varridos há muito tempo)
                for (Robo r : listaInimigos.values()) {
                    if (getTime() - r.tempoVarredura > 25) {
                        r.vivo = false;
                        if (alvo.nome != null && r.nome.equals(alvo.nome))
                            alvo.vivo = false;
                    }
                }

                movimento();
                if (alvo.vivo) disparar();
                execute();
            }
        } else {
            // Modo 1v1 (Wave Surfing)
            direcaoLateral = 1;
            velocidadeInimigoAnterior = 0;
            while (true) turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    // =======================
    // EVENTOS DO ROBOCODE
    // =======================
    public void onWin(WinEvent event) {
        while (true) {
            definirCorPadrao();
            turnRadarRight(360);
        }
    }

    public void onRobotDeath(RobotDeathEvent event) {
        if (listaInimigos.containsKey(event.getName()))
            listaInimigos.get(event.getName()).vivo = false;

        if (event.getName().equals(alvo.nome))
            alvo.vivo = false;
    }

    /**
     * Reação ao detectar um inimigo — atualiza dados, movimenta e dispara.
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        definirCorCombate();

        // --- Modo múltiplos inimigos ---
        if (getOthers() > 1) {
            Robo inimigo = listaInimigos.get(e.getName());
            if (inimigo == null) {
                inimigo = new Robo();
                listaInimigos.put(e.getName(), inimigo);
            }

            inimigo.anguloAbsolutoRadianos = e.getBearingRadians();
            inimigo.setLocation(new Point2D.Double(
                    meuRobo.x + e.getDistance() * Math.sin(getHeadingRadians() + inimigo.anguloAbsolutoRadianos),
                    meuRobo.y + e.getDistance() * Math.cos(getHeadingRadians() + inimigo.anguloAbsolutoRadianos)));
            inimigo.ultimaDirecao = inimigo.direcao;
            inimigo.nome = e.getName();
            inimigo.energia = e.getEnergy();
            inimigo.vivo = true;
            inimigo.tempoVarredura = getTime();
            inimigo.velocidade = e.getVelocity();
            inimigo.direcao = e.getHeadingRadians();

            inimigo.pontuacaoDisparo = inimigo.energia < 25
                    ? (inimigo.energia < 5
                            ? (inimigo.energia == 0 ? Double.MIN_VALUE : inimigo.distance(meuRobo) * 0.1)
                            : inimigo.distance(meuRobo) * 0.75)
                    : inimigo.distance(meuRobo);

            if (getOthers() == 1)
                setTurnRadarLeftRadians(getRadarTurnRemainingRadians());

            if (!alvo.vivo || inimigo.pontuacaoDisparo < alvo.pontuacaoDisparo)
                alvo = inimigo;
        }

        // --- Modo 1v1 (com ondas e mira estatística) ---
        else {
            setScanColor(Color.red);
            Robo inimigo = new Robo();
            inimigo.anguloAbsolutoRadianos = getHeadingRadians() + e.getBearingRadians();
            inimigo.distancia = e.getDistance();
            inimigo.velocidade = e.getVelocity();

            if (inimigo.velocidade != 0)
                direcaoLateral = Utilitario.sinal(
                        inimigo.velocidade * Math.sin(e.getHeadingRadians() - inimigo.anguloAbsolutoRadianos));

            Onda onda = new Onda(this);
            onda.posicaoCanhao = new Point2D.Double(getX(), getY());
            Onda.posicaoAlvo = Utilitario.projetar(onda.posicaoCanhao, inimigo.anguloAbsolutoRadianos, inimigo.distancia);
            onda.direcaoLateral = direcaoLateral;
            onda.definirSegmentacoes(inimigo.distancia, inimigo.velocidade, velocidadeInimigoAnterior);
            velocidadeInimigoAnterior = inimigo.velocidade;
            onda.angulo = inimigo.anguloAbsolutoRadianos;

            // Mira preditiva com base em ondas
            setTurnGunRightRadians(Utils.normalRelativeAngle(
                    inimigo.anguloAbsolutoRadianos - getGunHeadingRadians() + onda.offsetAnguloMaisVisitado()));

            POTENCIA_TIRO = Math.min(3, Math.min(this.getEnergy(), e.getEnergy()) / 4.0);
            onda.potenciaTiro = POTENCIA_TIRO;

            if (getEnergy() < 2 && e.getDistance() < 500)
                onda.potenciaTiro = 0.1;
            else if (e.getDistance() >= 500)
                onda.potenciaTiro = 1.1;

            setFire(onda.potenciaTiro);

            if (getEnergy() >= onda.potenciaTiro)
                addCustomEvent(onda);

            movimento1v1.onScannedRobot(e);
            setTurnRadarRightRadians(Utils.normalRelativeAngle(
                    inimigo.anguloAbsolutoRadianos - getRadarHeadingRadians()) * 2);
        }
    }

    // =======================
    // MÉTODOS DE COMBATE
    // =======================
    private void definirCorCombate() {
        setColors(new Color(255, 255, 255),
                new Color(0, 0, 0),
                new Color(255, 255, 255),
                new Color(0, 0, 0),
                new Color(255, 255, 255));
    }

    private void definirCorPadrao() {
        setColors(new Color(255, 255, 255),
                new Color(0, 0, 0),
                new Color(255, 255, 255),
                new Color(0, 0, 0),
                new Color(255, 255, 255));
    }

    /**
     * Mira preditiva com base na posição, direção e velocidade do inimigo.
     * Mantive a lógica original de previsão e ajuste de potência dependendo
     * do cenário e das bordas do campo.
     */
    public void disparar() {
        if (alvo != null && alvo.vivo) {
            double distancia = meuRobo.distance(alvo);
            double potencia = (distancia > 850 ? 0.1 : (distancia > 700 ? 0.5 : (distancia > 250 ? 2.0 : 3.0)));
            potencia = Math.min(meuRobo.energia / 4d, Math.min(alvo.energia / 3d, potencia));
            potencia = Utilitario.limitar(potencia, 0.1, 3.0);

            long tempoAteAcerto;
            Point2D.Double mirarEm = new Point2D.Double();
            double direcao = alvo.direcao;
            double deltaDirecao = direcao - alvo.ultimaDirecao;
            double preverX = alvo.getX();
            double preverY = alvo.getY();

            mirarEm.setLocation(preverX, preverY);
            tempoAteAcerto = 0;

            do {
                preverX += Math.sin(direcao) * alvo.velocidade;
                preverY += Math.cos(direcao) * alvo.velocidade;
                direcao += deltaDirecao;
                tempoAteAcerto++;

                Rectangle2D.Double areaDisparo = new Rectangle2D.Double(
                        MARGEM_PAREDE, MARGEM_PAREDE,
                        campoBatalha.width - MARGEM_PAREDE, campoBatalha.height - MARGEM_PAREDE);

                if (!areaDisparo.contains(preverX, preverY)) {
                    double velocidadeTiro = mirarEm.distance(meuRobo) / tempoAteAcerto;
                    potencia = Utilitario.limitar((20 - velocidadeTiro) / 3.0, 0.1, 3.0);
                    break;
                }
                mirarEm.setLocation(preverX, preverY);
            } while ((int) Math.round((mirarEm.distance(meuRobo) - MARGEM_PAREDE) /
                    Rules.getBulletSpeed(potencia)) > tempoAteAcerto);

            mirarEm.setLocation(
                    Utilitario.limitar(preverX, 34, getBattleFieldWidth() - 34),
                    Utilitario.limitar(preverY, 34, getBattleFieldHeight() - 34));

            if ((getGunHeat() == 0.0) && (getGunTurnRemaining() == 0.0) && (potencia > 0.0) && (meuRobo.energia > 0.1)) {
                setFire(potencia);
            }

            setTurnGunRightRadians(Utils.normalRelativeAngle(((Math.PI / 2) - Math.atan2(mirarEm.y - meuRobo.getY(),
                    mirarEm.x - meuRobo.getX())) - getGunHeadingRadians()));
        }
    }

    // =======================
    // MOVIMENTAÇÃO E AVALIAÇÃO DE PONTOS
    // =======================
    public void movimento() {
        if (pontoAlvo.distance(meuRobo) < 15 || tempoInativo > 25) {
            tempoInativo = 0;
            atualizarListaPosicoes(QUANTIDADE_PONTOS_PREVISTOS);

            Point2D.Double pontoMenorRisco = null;
            double menorRisco = Double.MAX_VALUE;

            for (Point2D.Double p : posicoesPossiveis) {
                double riscoAtual = avaliarPonto(p);
                if (riscoAtual <= menorRisco || pontoMenorRisco == null) {
                    menorRisco = riscoAtual;
                    pontoMenorRisco = p;
                }
            }
            pontoAlvo = pontoMenorRisco;
        } else {
            tempoInativo++;
            double angulo = Utilitario.anguloAbsoluto(meuRobo, pontoAlvo) - getHeadingRadians();
            double direcao = 1;
            if (Math.cos(angulo) < 0) {
                angulo += Math.PI;
                direcao *= -1;
            }
            setMaxVelocity(10 - (4 * Math.abs(getTurnRemainingRadians())));
            setAhead(meuRobo.distance(pontoAlvo) * direcao);
            angulo = Utils.normalRelativeAngle(angulo);
            setTurnRightRadians(angulo);
        }
    }

    public void atualizarListaPosicoes(int n) {
        posicoesPossiveis.clear();
        final int alcanceX = (int) (125 * 1.5);

        for (int i = 0; i < n; i++) {
            double modX = Utilitario.aleatorioEntre(-alcanceX, alcanceX);
            double alcanceY = Math.sqrt(alcanceX * alcanceX - modX * modX);
            double modY = Utilitario.aleatorioEntre(-alcanceY, alcanceY);

            double y = Utilitario.limitar(meuRobo.y + modY, 75, campoBatalha.height - 75);
            double x = Utilitario.limitar(meuRobo.x + modX, 75, campoBatalha.width - 75);

            posicoesPossiveis.add(new Point2D.Double(x, y));
        }
    }

    /**
     * Avalia o "risco" de estar num ponto p com base em múltiplos fatores:
     * - proximidade relativa ao próprio robô
     * - concentração de inimigos
     * - ângulos e exposição a disparos
     *
     * Retorna um valor de risco (quanto menor, melhor).
     */
    public double avaliarPonto(Point2D.Double p) {
        double valorRisco = Utilitario.aleatorioEntre(1, 2.25) / p.distanceSq(meuRobo);
        valorRisco += (6 * (getOthers() - 1)) / p.distanceSq(campoBatalha.width / 2, campoBatalha.height / 2);

        double fatorCanto = getOthers() <= 5 ? (getOthers() == 1 ? 0.25 : 0.5) : 1;
        valorRisco += fatorCanto / p.distanceSq(0, 0);
        valorRisco += fatorCanto / p.distanceSq(campoBatalha.width, 0);
        valorRisco += fatorCanto / p.distanceSq(0, campoBatalha.height);
        valorRisco += fatorCanto / p.distanceSq(campoBatalha.width, campoBatalha.height);

        if (alvo.vivo) {
            double anguloRobo = Utils.normalRelativeAngle(Utilitario.anguloAbsoluto(p, alvo) - Utilitario.anguloAbsoluto(meuRobo, p));
            for (Robo inimigo : listaInimigos.values()) {
                valorRisco += (inimigo.energia / meuRobo.energia)
                        * (1 / p.distanceSq(inimigo))
                        * (1.0 + ((1 - (Math.abs(Math.sin(anguloRobo)))) + Math.abs(Math.cos(anguloRobo))) / 2)
                        * (1 + Math.abs(Math.cos(Utilitario.anguloAbsoluto(meuRobo, p) - Utilitario.anguloAbsoluto(inimigo, p))));
            }
        } else if (listaInimigos.values().size() >= 1) {
            for (Robo inimigo : listaInimigos.values()) {
                valorRisco += (inimigo.energia / meuRobo.energia)
                        * (1 / p.distanceSq(inimigo))
                        * (1 + Math.abs(Math.cos(Utilitario.anguloAbsoluto(meuRobo, p) - Utilitario.anguloAbsoluto(inimigo, p))));
            }
        } else {
            valorRisco += (1 + Math.abs(Utilitario.anguloAbsoluto(meuRobo, pontoAlvo) - getHeadingRadians()));
        }

        return valorRisco;
    }
}