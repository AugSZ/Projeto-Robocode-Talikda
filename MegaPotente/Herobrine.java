package MegaPotente;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

import robocode.*;
import robocode.util.Utils;

/** 
 * Herobrine
 *
 * Robô avançado do Robocode com movimentação adaptativa, mira preditiva e
 * aprendizado estatístico por ondas ("Wave Surfing").
 *
 * Combina estratégias de evasão e previsão de disparos para batalhas 1v1 e
 * múltiplos inimigos.
 * Comentários explicativos foram adicionados para tornar o código mais
 * compreensível para um leitor leigo — explicam a finalidade das partes
 * principais sem alterar a lógica do robô.
 */
public class Herobrine extends AdvancedRobot {

    // =======================
    // VARIÁVEIS GERAIS
    // =======================
    // Potência padrão de tiro (ajustada dinamicamente no código)
    static double POTENCIA_TIRO = 3;
    // Quantidade de pontos simulados para decidir movimento em modo múltiplos inimigos
    static final int QUANTIDADE_PONTOS_PREVISTOS = 150;
    // Espaço de segurança em relação às paredes do campo
    static final double MARGEM_PAREDE = 18;

    static Random aleatorio = new Random();

    // Mapa com informações dos inimigos (por nome)
    HashMap<String, Robo> listaInimigos = new HashMap<>();
    // Estrutura que representa nosso próprio robô (posição, energia...)
    Robo meuRobo = new Robo();
    // O inimigo alvo (escolhido entre os detectados)
    Robo alvo;
    // Lista de posições candidatas avaliadas para movimentação
    List<Point2D.Double> posicoesPossiveis = new ArrayList<>();
    // Ponto de destino atual do robô
    Point2D.Double pontoAlvo = new Point2D.Double(60, 60);
    // Retângulo que representa as dimensões do campo de batalha
    Rectangle2D.Double campoBatalha = new Rectangle2D.Double();

    int tempoInativo = 30;
    private static double direcaoLateral;
    private static double velocidadeInimigoAnterior;
    private static Movimento1v1 movimento1v1;

    // =======================
    // CLASSE INTERNA: MODELO DE ROBO
    // =======================
    // Estrutura simples para armazenar dados de um robô (nosso ou inimigo).
    class Robo extends Point2D.Double {
        public long tempoVarredura; // momento da última varredura (scan)
        public boolean vivo = true; // está vivo no momento
        public String nome; // nome do robô (quando aplicável)
        double anguloCanhaoRadianos, anguloAbsolutoRadianos; // ângulos relevantes
        double velocidade, direcao, ultimaDirecao; // movimento
        double pontuacaoDisparo, distancia, energia; // métricas usadas pelo algoritmo
    }

    // =======================
    // CLASSE UTILITÁRIA
    // =======================
    // Funções utilitárias matemáticas e geométricas usadas no resto do código.
    public static class Utilitario {

        // Limita um valor entre min e max
        static double limitar(double valor, double min, double max) {
            return Math.max(min, Math.min(max, valor));
        }

        // Retorna valor aleatório entre min e max
        static double aleatorioEntre(double min, double max) {
            return min + Math.random() * (max - min);
        }

        // Projetar um ponto a partir de uma origem, ângulo e distância (polar -> cartesiano)
        static Point2D projetar(Point2D origem, double angulo, double distancia) {
            return new Point2D.Double(
                    origem.getX() + Math.sin(angulo) * distancia,
                    origem.getY() + Math.cos(angulo) * distancia);
        }

        // Calcula ângulo absoluto (em radianos) entre dois pontos
        static double anguloAbsoluto(Point2D origem, Point2D alvo) {
            return Math.atan2(alvo.getX() - origem.getX(), alvo.getY() - origem.getY());
        }

        // Retorna sinal de um número (1 ou -1)
        static int sinal(double v) {
            return v < 0 ? -1 : 1;
        }
    }

    // =======================
    // CLASSE DE MOVIMENTO 1v1 (Evasão e posicionamento)
    // =======================
    // Implementa uma estratégia simples de evasão para duelos individuais.
    class Movimento1v1 {
        // Parâmetros fixos (podem ser ajustados conforme preferências)
        private static final double LARGURA_CAMPO = 800;
        private static final double ALTURA_CAMPO = 600;
        private static final double TEMPO_MAX_TENTATIVA = 125;
        private static final double AJUSTE_REVERSA = 0.421075;
        private static final double EVASAO_PADRAO = 1.2;
        private static final double AJUSTE_QUIQUE_PAREDE = 0.699484;

        private final AdvancedRobot robo;
        // Área útil onde o robô tenta permanecer (evitando bordas)
        private final Rectangle2D areaDisparo = new Rectangle2D.Double(
                MARGEM_PAREDE, MARGEM_PAREDE,
                LARGURA_CAMPO - MARGEM_PAREDE * 2,
                ALTURA_CAMPO - MARGEM_PAREDE * 2);

        private double direcao = 0.4; // direcao lateral inicial para evasão

        Movimento1v1(AdvancedRobot r) {
            this.robo = r;
        }

        /**
         * Estratégia de evasão e movimentação dinâmica em combate 1v1.
         * Calcula um ponto de destino relativo ao inimigo que tende a reduzir
         * a probabilidade de ser atingido (mantém distância e evita paredes).
         *
         * Recebe o evento ScannedRobot e usa a informação para ajustar movimento.
         */
        public void onScannedRobot(ScannedRobotEvent e) {
            // Cria um objeto com dados do inimigo baseados no scan
            Robo inimigo = new Robo();
            inimigo.anguloAbsolutoRadianos = robo.getHeadingRadians() + e.getBearingRadians();
            inimigo.distancia = e.getDistance();

            Point2D posicaoRobo = new Point2D.Double(robo.getX(), robo.getY());
            // posição absoluta do inimigo no campo
            Point2D posicaoInimigo = Utilitario.projetar(posicaoRobo, inimigo.anguloAbsolutoRadianos, inimigo.distancia);
            Point2D destinoRobo;

            // Tenta escolher um ponto válido para se posicionar (dentro da área útil)
            double tempoTentativa = 0;
            while (!areaDisparo.contains(
                    destinoRobo = Utilitario.projetar(
                            posicaoInimigo,
                            inimigo.anguloAbsolutoRadianos + Math.PI + direcao,
                            inimigo.distancia * (EVASAO_PADRAO - tempoTentativa / 100.0)))
                    && tempoTentativa < TEMPO_MAX_TENTATIVA) {
                tempoTentativa++;
            }

            // Em algumas condições, inverte a direção lateral para variar o comportamento
            if ((Math.random() < (Rules.getBulletSpeed(POTENCIA_TIRO) / AJUSTE_REVERSA) / inimigo.distancia)
                    || tempoTentativa > (inimigo.distancia / Rules.getBulletSpeed(POTENCIA_TIRO) / AJUSTE_QUIQUE_PAREDE))
                direcao = -direcao;

            // Calcula o ângulo para chegar ao destino e ordena o movimento ao robocode
            double angulo = Utilitario.anguloAbsoluto(posicaoRobo, destinoRobo) - robo.getHeadingRadians();
            robo.setAhead(Math.cos(angulo) * 100); // move-se adiante ou para trás dependendo do ângulo
            robo.setTurnRightRadians(Math.tan(angulo)); // ajusta a direção do robô
        }
    }

    // =======================
    // CLASSE DE ONDAS (Wave Surfing e Mira Estatística)
    // =======================
    // Representa as "ondas" usadas para mapear onde os tiros tendem a acertar
    // e aprender estatisticamente os padrões de evasão do inimigo.
    static class Onda extends Condition {
        static Point2D posicaoAlvo; // posição prevista do alvo (quando disparo foi feito)
        double potenciaTiro;
        Point2D posicaoCanhao;
        double angulo;
        double direcaoLateral;

        // Parâmetros para criar segmentos estatísticos (tabelas)
        private static final double DISTANCIA_MAXIMA = 900;
        private static final int INDICES_DISTANCIA = 5;
        private static final int INDICES_VELOCIDADE = 5;
        private static final int BINS = 25;
        private static final int BIN_CENTRAL = (BINS - 1) / 2;
        private static final double ANGULO_ESCAPE_MAXIMO = 0.7;
        private static final double LARGURA_BIN = ANGULO_ESCAPE_MAXIMO / (double) BIN_CENTRAL;
        // Buffer 4D que armazena histogramas de comportamento do inimigo
        private static final int[][][][] buffersEstatisticos =
                new int[INDICES_DISTANCIA][INDICES_VELOCIDADE][INDICES_VELOCIDADE][BINS];

        private int[] buffer;
        private double distanciaPercorrida;
        private final AdvancedRobot robo;

        Onda(AdvancedRobot r) {
            this.robo = r;
        }

        /**
         * Chamado periodicamente (Condition.test) para avançar a onda e, quando
         * a onda "atinge" o alvo, atualizar o histograma estatístico.
         */
        public boolean test() {
            avancar();
            if (chegou()) {
                // marca o bin correspondente como visitado (aprendizado)
                buffer[binAtual()]++;
                robo.removeCustomEvent(this); // remove a condição (onda terminada)
            }
            return false;
        }

        // Retorna o deslocamento angular mais provável com base no bin mais visitado
        double offsetAnguloMaisVisitado() {
            return (direcaoLateral * LARGURA_BIN) * (binMaisVisitado() - BIN_CENTRAL);
        }

        // Seleciona o buffer (histograma) apropriado com base na segmentação
        void definirSegmentacoes(double distancia, double velocidade, double ultimaVelocidade) {
            int indiceDistancia = (int) (distancia / (DISTANCIA_MAXIMA / INDICES_DISTANCIA));
            int indiceVelocidade = (int) Math.abs(velocidade / 2);
            int indiceUltimaVelocidade = (int) Math.abs(ultimaVelocidade / 2);
            buffer = buffersEstatisticos[indiceDistancia][indiceVelocidade][indiceUltimaVelocidade];
        }

        // Avança a onda conforme a velocidade da bala
        private void avancar() {
            distanciaPercorrida += Rules.getBulletSpeed(potenciaTiro);
        }

        // Verifica se a onda já alcançou a posição do alvo
        private boolean chegou() {
            return distanciaPercorrida > posicaoCanhao.distance(posicaoAlvo) - MARGEM_PAREDE;
        }

        // Calcula em qual bin a diferença angular atual se encaixa
        private int binAtual() {
            int bin = (int) Math.round(((Utils.normalRelativeAngle(
                    Utilitario.anguloAbsoluto(posicaoCanhao, posicaoAlvo) - angulo))
                    / (direcaoLateral * LARGURA_BIN)) + BIN_CENTRAL);
            return (int) Utilitario.limitar(bin, 0, BINS - 1);
        }

        // Retorna o índice do bin mais visitado (maior contagem)
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
        // inicializa o módulo de movimento 1v1 com referência a este robô
        movimento1v1 = new Movimento1v1(this);
    }

    public void run() {
        // Inicializa dimensões do campo
        campoBatalha.height = getBattleFieldHeight();
        campoBatalha.width = getBattleFieldWidth();

        // Inicializa dados do nosso robô (posição e energia)
        meuRobo.x = getX();
        meuRobo.y = getY();
        meuRobo.energia = getEnergy();

        // Começamos sem um alvo definido — pontoAlvo inicial é nossa posição
        pontoAlvo.x = meuRobo.x;
        pontoAlvo.y = meuRobo.y;

        alvo = new Robo();
        alvo.vivo = false;

        // Ajustes para que o canhão e radar girem independentemente do corpo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Modo de combate múltiplo (mais de 1 oponente)
        if (getOthers() > 1) {
            // preenche uma lista de pontos possíveis para avaliar movimento
            atualizarListaPosicoes(QUANTIDADE_PONTOS_PREVISTOS);
            // mantém o radar girando indefinidamente
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

            // Loop principal em batalhas múltiplas
            while (true) {
                // Atualiza informações sobre nosso estado
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

                // Decide movimento e dispara no alvo escolhido
                movimento();
                if (alvo.vivo) disparar();
                execute(); // executa ações enfileiradas no Robocode
            }
        } else {
            // Modo 1v1 (Wave Surfing): radar gira constantemente e lógica é acionada em onScannedRobot
            direcaoLateral = 1;
            velocidadeInimigoAnterior = 0;
            while (true) turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    // =======================
    // EVENTOS DO ROBOCODE
    // =======================
    public void onWin(WinEvent event) {
        // Animação/sinal de vitória: gira radar
        while (true) {
            
            turnRadarRight(360);
        }
    }

    public void onRobotDeath(RobotDeathEvent event) {
        // Atualiza estado quando um inimigo morre
        if (listaInimigos.containsKey(event.getName()))
            listaInimigos.get(event.getName()).vivo = false;

        if (event.getName().equals(alvo.nome)) {
            alvo.vivo = false;
        }
        definirCorKill();
    }

    /**
     * Reação ao detectar um inimigo — atualiza dados, movimenta e dispara.
     * Este método separa duas grandes estratégias:
     * - modo múltiplos inimigos (usa avaliação de pontos)
     * - modo 1v1 (usa ondas para mira estatística e evasão)
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

            // calcula posição absoluta do inimigo a partir do scan
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

            // Heurística para escolher o melhor alvo (considera distância e energia)
            inimigo.pontuacaoDisparo = inimigo.energia < 25
                    ? (inimigo.energia < 5
                            ? (inimigo.energia == 0 ? Double.MIN_VALUE : inimigo.distance(meuRobo) * 0.1)
                            : inimigo.distance(meuRobo) * 0.75)
                    : inimigo.distance(meuRobo);

            // Ajusta radar para continuar scaneando eficientemente
            if (getOthers() == 1)
                setTurnRadarLeftRadians(getRadarTurnRemainingRadians());

            // Escolhe novo alvo se for mais interessante que o atual
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

            // define direção lateral (esquerda/direita) com base no movimento do inimigo
            if (inimigo.velocidade != 0)
                direcaoLateral = Utilitario.sinal(
                        inimigo.velocidade * Math.sin(e.getHeadingRadians() - inimigo.anguloAbsolutoRadianos));

            // Cria uma onda para modelar o tiro que será feito agora
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

            // Ajusta potência do tiro com base na energia e distância
            POTENCIA_TIRO = Math.min(3, Math.min(this.getEnergy(), e.getEnergy()) / 4.0);
            onda.potenciaTiro = POTENCIA_TIRO;

            if (getEnergy() < 2 && e.getDistance() < 500)
                onda.potenciaTiro = 0.1;
            else if (e.getDistance() >= 500)
                onda.potenciaTiro = 1.1;

            // Dispara e registra a onda para aprendizado se tiver energia suficiente
            setFire(onda.potenciaTiro);

            if (getEnergy() >= onda.potenciaTiro)
                addCustomEvent(onda);

            // Usa o módulo de movimento 1v1 para evasão/posicionamento
            movimento1v1.onScannedRobot(e);
            // Mantém o radar travado no inimigo
            setTurnRadarRightRadians(Utils.normalRelativeAngle(
                    inimigo.anguloAbsolutoRadianos - getRadarHeadingRadians()) * 2);
        }
    }

    // =======================
    // MÉTODOS DE COMBATE
    // =======================
    // Define a cor do robô em combate (apenas estética)
    private void definirCorCombate() {
        setColors(new Color(9, 121, 110), //Cor do corpo
                new Color(0, 0, 0), //Gor do canhao
                new Color(9, 121, 110), //radarColor
                new Color(204, 204, 255), // bulletColor
                new Color(68, 223, 208)); // Cor do arco de radar
    }

    // Define a cor padrão do robô (usada ao ganhar)
    private void definirCorKill() {
        setColors(new Color(255, 0, 0), // Gor do corpo
                new Color(0, 0, 0), // Cor do canhão
                new Color(255, 0, 0), // Cor do radar
                new Color(204, 204, 255), // Cor cor do tiro
                new Color(255, 0, 0)); // Cor do arco de radar
    }

    /**
     * Mira preditiva com base na posição, direção e velocidade do inimigo.
     * Tenta prever onde o inimigo estará quando a bala chegar e ajustar a potência.
     */
    public void disparar() {
        if (alvo != null && alvo.vivo) {
            double distancia = meuRobo.distance(alvo);
            // Heurística para escolher potência conforme distância e energia
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

            // Simula o movimento futuro do inimigo até a bala alcançá-lo (ou até bater na parede)
            do {
                preverX += Math.sin(direcao) * alvo.velocidade;
                preverY += Math.cos(direcao) * alvo.velocidade;
                direcao += deltaDirecao;
                tempoAteAcerto++;

                Rectangle2D.Double areaDisparo = new Rectangle2D.Double(
                        MARGEM_PAREDE, MARGEM_PAREDE,
                        campoBatalha.width - MARGEM_PAREDE, campoBatalha.height - MARGEM_PAREDE);

                // Se a previsão extrapola o campo, ajusta potência para evitar desperdício
                if (!areaDisparo.contains(preverX, preverY)) {
                    double velocidadeTiro = mirarEm.distance(meuRobo) / tempoAteAcerto;
                    potencia = Utilitario.limitar((20 - velocidadeTiro) / 3.0, 0.1, 3.0);
                    break;
                }
                mirarEm.setLocation(preverX, preverY);
            } while ((int) Math.round((mirarEm.distance(meuRobo) - MARGEM_PAREDE) /
                    Rules.getBulletSpeed(potencia)) > tempoAteAcerto);

            // Garante que o ponto final esteja dentro dos limites do campo
            mirarEm.setLocation(
                    Utilitario.limitar(preverX, 34, getBattleFieldWidth() - 34),
                    Utilitario.limitar(preverY, 34, getBattleFieldHeight() - 34));

            // Só dispara se o canhão estiver pronto e tivermos energia suficiente
            if ((getGunHeat() == 0.0) && (getGunTurnRemaining() == 0.0) && (potencia > 0.0) && (meuRobo.energia > 0.1)) {
                setFire(potencia);
            }

            // Ajusta o canhão para mirar no ponto previsto
            setTurnGunRightRadians(Utils.normalRelativeAngle(((Math.PI / 2) - Math.atan2(mirarEm.y - meuRobo.getY(),
                    mirarEm.x - meuRobo.getX())) - getGunHeadingRadians()));
        }
    }

    // =======================
    // MOVIMENTAÇÃO E AVALIAÇÃO DE PONTOS
    // =======================
    // Decide movimento em modo múltiplos inimigos com base em avaliação de risco
    public void movimento() {
        // Se chegamos perto do ponto alvo, ou ficamos inativos por muito tempo, recalculamos
        if (pontoAlvo.distance(meuRobo) < 15 || tempoInativo > 25) {
            tempoInativo = 0;
            atualizarListaPosicoes(QUANTIDADE_PONTOS_PREVISTOS);

            Point2D.Double pontoMenorRisco = null;
            double menorRisco = Double.MAX_VALUE;

            // Avalia cada ponto candidato e escolhe aquele com menor risco
            for (Point2D.Double p : posicoesPossiveis) {
                double riscoAtual = avaliarPonto(p);
                if (riscoAtual <= menorRisco || pontoMenorRisco == null) {
                    menorRisco = riscoAtual;
                    pontoMenorRisco = p;
                }
            }
            pontoAlvo = pontoMenorRisco;
        } else {
            // Movimento em direção ao ponto alvo com ajuste de velocidade ao virar
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

    // Preenche a lista de posições canditadas ao redor do robô
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
     * - concentração de inimigos e suas energias
     * - exposição em direção aos inimigos e aos cantos (paredes)
     *
     * Retorna um valor de risco (quanto menor, melhor).
     */
    public double avaliarPonto(Point2D.Double p) {
        // Começa com um termo aleatório suave para quebrar empates
        double valorRisco = Utilitario.aleatorioEntre(1, 2.25) / p.distanceSq(meuRobo);
        // Penaliza pontos mais centrais quando há muitos inimigos
        valorRisco += (6 * (getOthers() - 1)) / p.distanceSq(campoBatalha.width / 2, campoBatalha.height / 2);

        // Fator relativo aos cantos do campo (evitar ficar exposto)
        double fatorCanto = getOthers() <= 5 ? (getOthers() == 1 ? 0.25 : 0.5) : 1;
        valorRisco += fatorCanto / p.distanceSq(0, 0);
        valorRisco += fatorCanto / p.distanceSq(campoBatalha.width, 0);
        valorRisco += fatorCanto / p.distanceSq(0, campoBatalha.height);
        valorRisco += fatorCanto / p.distanceSq(campoBatalha.width, campoBatalha.height);

        // Se temos um alvo, considera ângulo e energia dos inimigos para computar risco
        if (alvo.vivo) {
            double anguloRobo = Utils.normalRelativeAngle(Utilitario.anguloAbsoluto(p, alvo) - Utilitario.anguloAbsoluto(meuRobo, p));
            for (Robo inimigo : listaInimigos.values()) {
                valorRisco += (inimigo.energia / meuRobo.energia)
                        * (1 / p.distanceSq(inimigo))
                        * (1.0 + ((1 - (Math.abs(Math.sin(anguloRobo)))) + Math.abs(Math.cos(anguloRobo))) / 2)
                        * (1 + Math.abs(Math.cos(Utilitario.anguloAbsoluto(meuRobo, p) - Utilitario.anguloAbsoluto(inimigo, p))));
            }
        } else if (listaInimigos.values().size() >= 1) {
            // Se não há um alvo definido, ainda penaliza proximidade de inimigos
            for (Robo inimigo : listaInimigos.values()) {
                valorRisco += (inimigo.energia / meuRobo.energia)
                        * (1 / p.distanceSq(inimigo))
                        * (1 + Math.abs(Math.cos(Utilitario.anguloAbsoluto(meuRobo, p) - Utilitario.anguloAbsoluto(inimigo, p))));
            }
        } else {
            // Caso sem inimigos conhecidos, penaliza pontos que obriguem virar muito o robô
            valorRisco += (1 + Math.abs(Utilitario.anguloAbsoluto(meuRobo, pontoAlvo) - getHeadingRadians()));
        }

        return valorRisco;
    }
}