# Robocode01
Este é mega macho rodo de testes e desenvolvimento dos serelepes academicos de ICO.

Epsilon basicamente vem do meu nome, Vitor, _victor_ do latim, significando vitorioso. Como sabia que teriam diversas versões, e honestamente é bem paia usar o nome v4, v5, ou algo assim, aproveitei que as próprias letras gregas tem valor númerico. Do alpha ao ômega. Então, começaria do VitorAlpha. E obviamente não fiz assim por ser muito personalista. Aí a variação de nome "vitorioso" seria Nikáo. Mas a letra incial seria a letra "ni". Nome ruim. Então fui para um próximo, Heitor, Eκτωρ, que começa com a letra Epsilon

----

# 26/10
## 22:15
Primeira versão seguia código padrão do perseguidor.
Versão beta houve a tentativa de adaptar o tiro, onde o firePower seria adequado para a distância, evitando atirar loucamente. Teria o radar lockado em um inimigo ao invés de girar infinitamente pegando inimigos quaisquer. Os problemas foram examidos via IA, mas não compensou. Robô mal se movimentava. Algumas correções foram feitas na versão Gamma, mas nada demais. Versão alpha se mantinha melhor
Dia 25/10 fiquei das 20 até 4 da manhã pensando e adicionei uma espécie de previsão de posição inimiga, na versão alpha, pois, se ele vai superaquecer, ele faz isso pois atira demais. Se ele acertar todos os tiros, os inimigos morrem e ele atira pouco. Um problema a menos. Meter 3x0 no primeiro tempo é essencial. MAs como o fato do robo chegar perto do inimigo e atirar é uma vantagem gigante que tem sobre outros robos, essa previsão de tiro foi colocada apenas para grandes distâncias. Mas a previsão não deu muito certo. Quando o inimigo mudava a velocidade, para por exemplo virar para o lado, no caso de um **Robot**, ou a velocidade oscilava no caso de um **AdvancedRobot**, ele começava a errar todos os tiros dali em diante. Acredito que o problema é a o e.getDistance(); para pegar a velocidade inicial

## 23:10
Pesquisando e lendo código de robos no github de uns caras do Bangladesh até a Dinamarca, parece que é adequado dedicar uma... função? Método? Como chama isso? não sei ``public double EuSeiOndeVoceEsta() {}``, isso aí em Java. Parece ser algo específico da programação orientada a objetos. O que não entendo. Mas sei que preciso fazer isso. Na minha cabeça fazer isso dentro do onScannedRobot tá ok, mas pelo jeito não é.
Meu código está:
```
double t = e.getDistance()/velocidadeBala;
double posicaoFinal = velocidadeInicial * t + 0.5 * aceleracao * Math.pow(t,2);
double futuroX = Xinicial + Math.sin(anguloAbsoluto) * posicaoFinal;
double futuroY = Yinicial + Math.cos(anguloAbsoluto) * posicaoFinal;
```
o ``t``, pelo que parece, assume que o inimigo vai estar parado até o próximo scan. Essa variável precisa ter os ticks do jogo em conta. Confesso que enquanto colocava pensei nos ticks do jogo, mas achei demais, e fiquei com preguiça. Vai que dava certo sem. https://github.com/jd12/RobocodeTargeting Bom link