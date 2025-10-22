package teste;
import robocode.*;
import java.util.*;
import robocode.util.*;
import java.awt.geom.*;
import jk.mega.FastTrig;
import jk.precise.util.*;
.
public class Teste
{
   final static int OPTIONS = 12;
   static int[] optionScores = new int[OPTIONS];
   static int[] offsetCounts = new int[OPTIONS];
   static double[] offsets = new double[OPTIONS];
   static double[] dirOffsets = new double[OPTIONS];

   double lastEnemyEnergy = 100;
   Point2D.Double lastEnemyLocation;
   ArrayList<Point2D.Double> myLocations = new ArrayList<Point2D.Double>();
   ArrayList<Point2D.Double> enemyLocations = new ArrayList<Point2D.Double>();
   ArrayList<Double> enemyHeadings = new ArrayList<Double>();
   ArrayList<Double> enemyVelocities = new ArrayList<Double>();
   double lastBearing;
   double firePower;
   double moveAmount;
   long nextFireTime;
   ArrayList<DetectWave> enemyWaves = new ArrayList<DetectWave>();
   double direction=1;
   static double unmatchedEnemyDamage;
   static double enemyDamage;
   static double myDamage;
 
 
   boolean move;
   boolean aim;
   boolean turn;
   boolean fire;
   double moveVal;
   double aimAngle;
   double turnAngle;
   
   AdvancedRobot bot;
   public Teste {
   
      this.bot = bot;
   
      nextFireTime = bot.getTime() + (long)Math.ceil(bot.getGunHeat()/bot.getGunCoolingRate());
      //setAdjustRadarForGunTurn(true);
      //setAdjustRadarForRobotTurn(true); -- redundant
      //setAdjustGunForRobotTurn(true);
      if(bot.getRoundNum() > 0)
         System.out.println("Enemy Gun VG scores: \n" + Arrays.toString(optionScores));
      // while(true){
         // turnRadarRight(Double.POSITIVE_INFINITY);
      // }
   	
   }

   public void onScannedRobot(ScannedRobotEvent e) {
   
      double latVel = bot.getVelocity()*Math.sin(e.getBearingRadians());
      if(latVel < 0)
         direction = -1;
      if(latVel > 0)
         direction = 1;
      
      Point2D.Double myLocation = new Point2D.Double(bot.getX(), bot.getY());
      double absBearing=e.getBearingRadians() + (bot.getHeadingRadians());
      double eDistance = e.getDistance();
      double deltaE = (lastEnemyEnergy - (lastEnemyEnergy = e.getEnergy()));
      Point2D.Double enemyLocation = project(myLocation, absBearing, eDistance);
      myLocations.add(0,myLocation);
      enemyLocations.add(0,enemyLocation);
      enemyHeadings.add(0,e.getHeadingRadians());
      enemyVelocities.add(0,e.getVelocity());
        
      if(bot.getTime() >= nextFireTime && lastEnemyLocation != null && (Math.min(lastEnemyEnergy,0.0999)  < deltaE && deltaE < 3.001)){
      
         nextFireTime = bot.getTime() + (long)Math.ceil(Rules.getGunHeat(deltaE)/bot.getGunCoolingRate());
         double enemyBulletVelocity = Rules.getBulletSpeed(deltaE);
         
         double[] options = new double[OPTIONS];
         options[0] = absoluteBearing(enemyLocations.get(2),myLocations.get(2));
         options[1] = absoluteBearing(enemyLocations.get(2),myLocations.get(2))
            + enemyHeadings.get(1) - enemyHeadings.get(2);
         options[2] = absoluteBearing(enemyLocations.get(1),myLocations.get(2));
         options[3] = absoluteBearing(project(enemyLocations.get(2),enemyHeadings.get(2),enemyVelocities.get(2)),myLocations.get(2));
         for(int i = 0; i < OPTIONS/3; i++){
            options[i + OPTIONS/3] = options[i] + offsets[i];
            options[i + 2*OPTIONS/3] = options[i] + direction*dirOffsets[i];
         }
         
         int maxIndex = 0;
         for(int i = 0; i < OPTIONS; i++)
            if(optionScores[i] > optionScores[maxIndex])
               maxIndex = i;
         // System.out.println("Max index: " + maxIndex);
         double bulletBearing = options[maxIndex];
         double[] moveOptions = {-0.4,0.4};
         if(Math.abs(enemyVelocities.get(1)*Math.sin(enemyHeadings.get(1) - bulletBearing)) > 0.1 && maxIndex%(OPTIONS/3) < 2)
            moveOptions = new double[]{0d};
         double maxDiff = 0;
         for(int j = 0; j < moveOptions.length; j++){
            Point2D.Double fireLoc = project(myLocation,bot.getHeadingRadians(),moveOptions[j]);
            double fireBearing = absoluteBearing(fireLoc,lastEnemyLocation);
            double diff = Math.abs(Utils.normalRelativeAngle(fireBearing + Math.PI - bulletBearing));
            if(diff > maxDiff){
               moveAmount = moveOptions[j];
               maxDiff = diff;  
            }
         }
         //options[4] = absoluteBearing(enemyLocations.get(1),project(myLocations.get(2),getHeadingRadians(),moveAmount*0.5));
      
         Point2D.Double fireLoc = project(myLocation,bot.getHeadingRadians(),moveAmount);
        
         double maxWidth = 0;
         double bestAngle = absoluteBearing(fireLoc,enemyLocations.get(1));
         double bestPower = 0.1;
         long hitTime = 0;
         double maxPower = Math.min(deltaE-0.01,deltaE*(bot.getEnergy()-10)/e.getEnergy());
         for(int i = 0; i < 20; i++){
            double bulletPower = 0.1 + Math.max(0,(maxPower-0.1)*0.05*i);
            double myBulletVelocity = Rules.getBulletSpeed(bulletPower);
            Point2D.Double bLoc = lastEnemyLocation;
            double mDist = 0;
            double eFireTime = -1;
            double mFireTime = 1;
            double t = 0;
            while(true){
               t+= 1;
               bLoc = project(lastEnemyLocation,bulletBearing,(t-eFireTime)*enemyBulletVelocity);
               double bDistSq = bLoc.distanceSq(fireLoc);
               mDist = (t - mFireTime)*myBulletVelocity;
               if(mDist*mDist >= bDistSq || t > 100)
                  break;
            }
            
            Point2D.Double bP = bLoc;
            Point2D.Double lastbP = project(bP,bulletBearing,-enemyBulletVelocity);
            
            double en = bP.distance(fireLoc);
            double len = lastbP.distance(fireLoc);
            
            double actDistTrav = mDist;
            double lastDistTraveled = actDistTrav - myBulletVelocity;
         
            if(!( en < actDistTrav && len > lastDistTraveled && en < len )){
               System.out.println("Prediction error!");
               continue;
            }
            Point2D.Double p1, p2;
            if(en >= lastDistTraveled)
               p1 = bP;
            else{
               PreciseWave wv = new PreciseWave();
               wv.fireLocation = fireLoc;
               wv.distanceTraveled = lastDistTraveled;
               p1 = PreciseUtils.intersection(lastbP,bP,wv);      
            }
            if(len > actDistTrav){
               PreciseWave wv = new PreciseWave();
               wv.fireLocation = fireLoc;
               wv.distanceTraveled = actDistTrav;
               p2 = PreciseUtils.intersection(lastbP,bP,wv);
            }
            else
               p2 = lastbP;
         
            
            double d1 = absoluteBearing(fireLoc,p1);
            double d2 = absoluteBearing(fireLoc,p2);
              
            //double pDist = p1.distance(p2);
         
            Point2D.Double midPoint = new Point2D.Double(0.5*(p1.x+p2.x) , 0.5*(p1.y+p2.y));
         
            Rectangle2D.Double me = new Rectangle2D.Double(myLocation.x - 18.1, myLocation.y - 18.1, 36.2, 36.2);
            
            double diff = Utils.normalRelativeAngle(d1-d2);
            double fireAngle = d1 - 0.5*diff;
            double width = Math.abs(diff);
            if(width > maxWidth && !me.contains(p2)){
               maxWidth = width;
               bestAngle = fireAngle;
               bestPower = bulletPower;
               hitTime = (long)t;
            }
         }
            
         aim = true;
         aimAngle = bestAngle;
         
         firePower = bestPower;
         move = true;
         moveVal = moveAmount;
         
         DetectWave dw = new DetectWave();
         dw.fireLocation = enemyLocations.get(1);
         dw.fireTime = bot.getTime() - 2;
         dw.bulletBearings = options;
         dw.bearingAttempts = new boolean[OPTIONS];
         dw.bearingAttempts[maxIndex] = true;
         dw.bulletVelocity = enemyBulletVelocity;
         dw.interceptTime = hitTime;
         dw.direction = direction;
         enemyWaves.add(dw);
      
      }
      
         
      if(!move && bot.getDistanceRemaining() == 0 && !aim && bot.getGunTurnRemaining() == 0 && !fire && firePower > 0){
      
         fire = true;
         move = true;
         moveVal = -moveAmount;
         
      }
      updateWaves();
      
      if(firePower == 0 && !aim){
         aim = true;
         aimAngle = absBearing;
      
         if(e.getEnergy() < 0.1 && e.getVelocity() == 0 &&enemyVelocities.size() > 1 && bot.getTime() > nextFireTime + 3 &&
          enemyVelocities.get(1) == 0 && enemyWaves.size() == 0 && bot.getEnergy() > 1 + e.getEnergy()
          ){
            fire = true;
            firePower = 0.1;
         }
         else
            if( bot.getDistanceRemaining() == 0 && !move && !turn){
               turn = true;
               turnAngle = absBearing + Math.PI/2;
               
            }
      }
      
      if(lastEnemyLocation != null)
         lastBearing = absoluteBearing(lastEnemyLocation,myLocation);
      lastEnemyLocation = enemyLocation;
   }
   public void applyActions(){
      if(fire){
         bot.setFire(firePower);
         firePower = 0;
         fire = false;
      }
      
      if(aim){
         bot.setTurnGunRightRadians(Utils.normalRelativeAngle(aimAngle - bot.getGunHeadingRadians()));
         aim = false;
      }
      
      if(move){
         bot.setAhead(moveVal);
         move = false;
      }
   
      if(turn){
         bot.setTurnRightRadians(Math.tan(turnAngle - bot.getHeadingRadians()));
         turn = false;
      }
   
   }
   
   void updateWaves(){
      Iterator<DetectWave> it = enemyWaves.iterator();
      long time = bot.getTime();
      while(it.hasNext()){
         DetectWave dw = it.next();
         dw.distanceTraveled = dw.bulletVelocity*(time - dw.fireTime);
         if(dw.distanceTraveled - 18 > dw.fireLocation.distance(myLocations.get(0)))
            it.remove();
      }
   }
   boolean logBullet(Bullet b){
      double heading = b.getHeadingRadians();
      Iterator<DetectWave> it = enemyWaves.iterator();
      long time = bot.getTime();
      while(it.hasNext()){
         DetectWave dw = it.next();
         dw.distanceTraveled = dw.bulletVelocity*(time - dw.fireTime);
         Point2D.Double bloc = new Point2D.Double(b.getX(),b.getY());
         if(Math.abs(dw.distanceTraveled -  dw.fireLocation.distance(bloc) - dw.bulletVelocity) < 1.1*dw.bulletVelocity){
            boolean matched = false;
            
            for(int i = 0; i < OPTIONS; i++){
               double diff = (Utils.normalRelativeAngle(heading - dw.bulletBearings[i]));
               if(Math.abs(diff) < 0.00001){
                  optionScores[i]++;
                  matched = true;
               }
               if( i < OPTIONS/3){
                  offsets[i] = (offsets[i]*offsetCounts[i] + diff)/(offsetCounts[i]+1);
                  dirOffsets[i] = (dirOffsets[i]*offsetCounts[i] + dw.direction*diff)/(offsetCounts[i]+1);
                  offsetCounts[i]++;
               }
            }
            it.remove();
            return matched;
         }     
      }
   
      System.out.println("No bullet detected");
      return false;
   }
   public void onHitByBullet(HitByBulletEvent e){
      
      lastEnemyEnergy += 20 - (e.getVelocity());	
      boolean matched = logBullet(e.getBullet());
      double damage = Rules.getBulletDamage(e.getBullet().getPower());
      enemyDamage += damage;
      if(!matched){
         unmatchedEnemyDamage += damage;
      }
   }
   
   public boolean aboveScore(double goal){
      double Q = goal*0.01;
      double x = (2100 + myDamage)*(1-Q)/Q;
      return (unmatchedEnemyDamage <= x);
   }
   public void onBulletHitBullet(BulletHitBulletEvent e){
      logBullet(e.getHitBullet());
   }
   public void onBulletHit(BulletHitEvent e){
      lastEnemyEnergy -= Rules.getBulletDamage(e.getBullet().getPower());   
      myDamage += Rules.getBulletDamage(e.getBullet().getPower());
   }
   static Point2D.Double project(Point2D.Double location, double angle, double distance){
      return new Point2D.Double(location.x + distance*Math.sin(angle), location.y + distance*Math.cos(angle));
   }
   private double absoluteBearing(Point2D source, Point2D target) {
      return Math.atan2(target.getX() - source.getX(), target.getY() - source.getY());
   }
   
   
   class DetectWave extends PreciseWave{
   
      long fireTime;
      double direction;
      
      double[] bulletBearings;
      boolean[] bearingAttempts;
   
      long interceptTime;
   
   }
}

public class Teste {
 
   Hashtable<String,EnemyInfo> enemies = new Hashtable<String,EnemyInfo>();
   AdvancedRobot bot;
   Point2D.Double myLocation;
   public teste {
      bot = _bot;
   
      
      enemies.clear();
      // {
         // Enumeration<EnemyInfo> e = enemies.elements();
         // while(e.hasMoreElements()){
            // EnemyInfo eInfo = e.nextElement();
         // 
            // eInfo.lastScanTime =0;
         // 
         // }
      //     
      // }
   	
   }
   public void onTick(){
      if(bot.getRadarTurnRemaining() == 0)
         bot.setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            
      myLocation =  new Point2D.Double(bot.getX(), bot.getY());   
   }

   public void onScannedRobot(ScannedRobotEvent e){   
      String eName = e.getName();
                         
      EnemyInfo eInfo;
      if((eInfo = enemies.get(eName)) == null){
         enemies.put(eName,eInfo = new EnemyInfo());
         eInfo.name = eName;
      }
         
      eInfo.lastScanTime = (int)bot.getTime();
      double otherAngle;// = getHeadingRadians() + e.getBearingRadians();
      eInfo.location = project(myLocation,otherAngle = bot.getHeadingRadians() + e.getBearingRadians(), e.getDistance());
         
         
         
      if(bot.getOthers() <= enemies.size()){
         Enumeration<EnemyInfo> all = enemies.elements();
         int oldestScan = eInfo.lastScanTime;
         while(all.hasMoreElements()){
            EnemyInfo tmp = all.nextElement();
            if(tmp.lastScanTime < oldestScan){
               otherAngle = absoluteAngle(myLocation,tmp.location);
               oldestScan = tmp.lastScanTime;
            }
         }
         if(bot.getOthers() == 1 && oldestScan == eInfo.lastScanTime){
            double angle = Utils.normalRelativeAngle(otherAngle - bot.getRadarHeadingRadians());
            bot.setTurnRadarRightRadians(Math.signum(angle)*limit(0,Math.abs(angle) + (Math.PI/4 - Math.PI/8 - Math.PI/18),Math.PI/4));
            
         }
         else
            bot.setTurnRadarRightRadians(Utils.normalRelativeAngle(otherAngle - bot.getRadarHeadingRadians())*Double.POSITIVE_INFINITY);
      }
      else
         bot.setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
      
   }
 
   static  Point2D.Double project(Point2D.Double location, double angle, double distance){
      return new Point2D.Double(location.x + distance*FastTrig.sin(angle), location.y + distance*FastTrig.cos(angle));
   }
   static double absoluteAngle(Point2D source, Point2D target) {
      return FastTrig.atan2(target.getX() - source.getX(), target.getY() - source.getY());
   }

    
   public void onRobotDeath(RobotDeathEvent e){
   
      enemies.remove(e.getName());
   }
   
   public static double limit(double min, double value, double max) {
      if(value > max)
         return max;
      if(value < min)
         return min;
      
      return value;
   }

   class EnemyInfo{
      String name;
      int lastScanTime;
      Point2D.Double location = new Point2D.Double();
   }
}
public class Teste {
      public static final double PI        = 3.1415926535897932384626433832795D;
      public static final double TWO_PI    = 6.2831853071795864769252867665590D;
      public static final double HALF_PI   = 1.5707963267948966192313216916398D;
      public static final double QUARTER_PI = 0.7853981633974483096156608458199D;
      public static final double THREE_OVER_TWO_PI
                                            = 4.7123889803846898576939650749193D;
   
      private static final int TRIG_DIVISIONS = 8192*2;//MUST be power of 2!!!
      private static final int TRIG_HIGH_DIVISIONS = 131072;//MUST be power of 2!!!
      private static final double K           = TRIG_DIVISIONS / TWO_PI;
      private static final double ACOS_K      = (TRIG_HIGH_DIVISIONS - 1)/ 2;
      private static final double TAN_K      = TRIG_HIGH_DIVISIONS / PI;
   
      private static final double[] sineTable = new double[TRIG_DIVISIONS];
      private static final double[] tanTable  = new double[TRIG_HIGH_DIVISIONS];
      private static final double[] acosTable = new double[TRIG_HIGH_DIVISIONS];
   
      static{
         init();
      }
      
      public static final void init() {
         for (int i = 0; i < TRIG_DIVISIONS; i++) {
            sineTable[i] = Math.sin(i/K);
         }
         for(int i = 0; i < TRIG_HIGH_DIVISIONS; i++){
            tanTable[i]  = Math.tan(i/TAN_K);
            acosTable[i] = Math.acos(i / ACOS_K - 1);
         }
      }
      public static void main(String[] args){
         init();
         double maxdiff = 0;
         for(int i = 0; i < 500000; i++){
            double p = (i-250000)*(1.0/250001);
                        // System.out.println(p);
            double diff = Math.abs(Math.acos(p) - acos(p));
         
            if(diff > maxdiff)
               maxdiff = diff;
         }
         System.out.println(maxdiff);
      }
   
      public static final double sin(double value) {
         return sineTable[(int)(((value * K + 0.5) % TRIG_DIVISIONS + TRIG_DIVISIONS) )&(TRIG_DIVISIONS - 1)];
      }
   
      public static final double cos(double value) {
         return sineTable[(int)(((value * K + 0.5) % TRIG_DIVISIONS + 1.25 * TRIG_DIVISIONS) )&(TRIG_DIVISIONS - 1)];
      }
       
      public static final double sinInBounds(double value) {
         return sineTable[(int)(value * K + 0.5)&(TRIG_DIVISIONS - 1)];
      }
   
      public static final double cosInBounds(double value) {
         return sineTable[(int)(value * K + 0.5 + 0.25 * TRIG_DIVISIONS)&(TRIG_DIVISIONS - 1)];
      }
   
      public static final double tan(double value) {
         return tanTable[(int)(((value * TAN_K + 0.5) % TRIG_HIGH_DIVISIONS + TRIG_HIGH_DIVISIONS) )&(TRIG_HIGH_DIVISIONS - 1)];	
      }
   
      public static final double asin(double value) {
      //return atan(x / Math.sqrt(1 - x*x));
         return HALF_PI - acos(value);
      }
   
      public static final double acos(double value) {
         // double d = value*ACOS_K + ACOS_K;
         // int i = (int)d;
         // double ratio = d-i;
         // return acosTable[i]*(1-ratio) + acosTable[(int)Math.ceil(d)]*(ratio);
         return acosTable[(int)(value*ACOS_K + (ACOS_K + 0.5))];
      }
   
      public static final double atan(double value) {
         return (value >= 0 ? acos(1 / sqrt(value * value + 1)) : -acos(1 / sqrt(value * value + 1)));
      }
   
      public static final double atan2(double x, double y) {
         return (x >= 0 ? acos(y / sqrt(x*x + y*y)) : -acos(y / sqrt(x*x + y*y)));
      }
   
      public static final double sqrt(double x){
         return Math.sqrt(x);
      //return x * (1.5d - 0.5*x* (x = Double.longBitsToDouble(0x5fe6ec85e7de30daL - (Double.doubleToLongBits(x)>>1) )) *x) * x;
      }
   }
   
