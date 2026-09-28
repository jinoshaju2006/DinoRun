package com.dinorun.game;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaPlayer;
import android.view.*;
import android.os.SystemClock;
import java.util.*;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private String fmt(float v) { return String.valueOf((int) v); }
    enum State { MENU, HOW, CHARACTERS, SETTINGS, PLAYING, PAUSED, GAME_OVER, WIN }
    private final SurfaceHolder holder;
    private Thread thread;
    private volatile boolean running;
    private State state=State.MENU;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap title, lose, win;
    private final Bitmap[] dragons=new Bitmap[4];
    private final RectF player=new RectF();
    private final ArrayList<RectF> cacti=new ArrayList<>();
    private final Random rng=new Random();
    private int selected=0, fps=60;
    private float worldSpeed=420, score=0, best=0, spawnTimer=0, groundX=0, cloudX=0;
    private float playerX=0, playerY=0, vy=0, jumpVelocity=980;
    private boolean grounded=true;
    private long lastFrame;
    private final android.content.SharedPreferences prefs;
    private MediaPlayer music;
    private float scale=1, W=1280,H=720;
    private final HashMap<String,RectF> buttons=new HashMap<>();

    public GameView(Context c){
        super(c); holder=getHolder(); holder.addCallback(this); setFocusable(true);
        prefs=c.getSharedPreferences("dino",Context.MODE_PRIVATE);
        selected=prefs.getInt("dragon",0); fps=prefs.getInt("fps",60); best=prefs.getFloat("best",0);
        title=load(c,R.drawable.title); dragons[0]=load(c,R.drawable.dragon1); dragons[1]=load(c,R.drawable.dragon2); dragons[2]=load(c,R.drawable.dragon3); dragons[3]=load(c,R.drawable.dragon4); lose=load(c,R.drawable.lose); win=load(c,R.drawable.win);
        text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
    }
    private Bitmap load(Context c,int id){return BitmapFactory.decodeResource(c.getResources(),id);}
    @Override public void surfaceCreated(SurfaceHolder h){ startMusic(); start(); }
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int h2){ W=w;H=h2; }
    @Override public void surfaceDestroyed(SurfaceHolder h){ stop(); stopMusic(); }
    private void start(){ if(running)return; running=true; thread=new Thread(this,"DinoGame"); thread.start(); }
    private void stop(){running=false; if(thread!=null)try{thread.join(500);}catch(Exception ignored){} }
    private void startMusic(){ try{music=MediaPlayer.create(getContext(),R.raw.music); if(music!=null){music.setLooping(true); music.setVolume(.18f,.18f); music.start();}}catch(Exception ignored){} }
    private void stopMusic(){if(music!=null){try{music.stop();}catch(Exception ignored){} music.release(); music=null;}}
    private void sfx(int id){ if(!prefs.getBoolean("sfx",true))return; try{MediaPlayer m=MediaPlayer.create(getContext(),id); if(m!=null){m.setOnCompletionListener(MediaPlayer::release);m.start();}}catch(Exception ignored){}}

    @Override public void run(){
        lastFrame=SystemClock.elapsedRealtime();
        while(running){
            long now=SystemClock.elapsedRealtime(); float dt=Math.min(.05f,(now-lastFrame)/1000f); lastFrame=now;
            if(state==State.PLAYING) update(dt);
            drawFrame();
            long target=1000L/Math.max(30,fps); long used=SystemClock.elapsedRealtime()-now; long sleep=target-used;
            if(sleep>1) SystemClock.sleep(sleep);
        }
    }

    private void resetGame(){
        state=State.PLAYING; score=0; worldSpeed=baseSpeed(selected); spawnTimer=.8f; cacti.clear(); playerX=W*.18f; playerY=H*.67f; vy=0; grounded=true; groundX=0; cloudX=0; sfx(R.raw.select);
    }
    private float baseSpeed(int i){return new float[]{360,405,450,495}[i];}
    private float difficulty(){return Math.min(380, score*0.085f);}
    private void update(float dt){
        worldSpeed=baseSpeed(selected)+difficulty();
        score += worldSpeed*dt*.055f;
        groundX=(groundX-worldSpeed*dt)%120; cloudX=(cloudX-worldSpeed*dt*.12f)%W;
        // Physics
        if(!grounded){vy+=2350*dt; playerY+=vy*dt; if(playerY>=H*.67f){playerY=H*.67f;vy=0;grounded=true;}}
        // Cactus spawn with safe reaction window
        spawnTimer-=dt;
        if(spawnTimer<=0){
            float gap=Math.max(430, 650-difficulty()*.45f)+rng.nextInt(160);
            cacti.add(new RectF(W+40,H*.69f-105,W+40+70+rng.nextInt(35),H*.69f));
            spawnTimer=gap/worldSpeed;
        }
        for(int i=cacti.size()-1;i>=0;i--){RectF r=cacti.get(i); r.offset(-worldSpeed*dt,0); if(r.right<-30)cacti.remove(i);}
        player.set(playerX,H*.67f-120,playerX+110,H*.67f);
        RectF hit=new RectF(player.left+25,player.top+20,player.right-15,player.bottom-8);
        for(RectF c:cacti){RectF cr=new RectF(c.left+10,c.top+8,c.right-10,c.bottom); if(RectF.intersects(hit,cr)){gameOver();return;}}
    }
    private void jump(){if(state!=State.PLAYING)return; if(grounded){grounded=false;vy=-jumpVelocity;sfx(R.raw.jump);}}
    private void gameOver(){state=State.GAME_OVER; if(score>best){best=score;prefs.edit().putFloat("best",best).apply();} sfx(R.raw.gameover);}

    private void drawFrame(){
        Canvas c=null; try{c=holder.lockCanvas(); if(c==null)return; W=c.getWidth();H=c.getHeight(); scale=Math.min(W/1280f,H/720f); c.drawColor(Color.BLACK); if(state==State.MENU)drawMenu(c); else if(state==State.HOW)drawHow(c); else if(state==State.CHARACTERS)drawCharacters(c); else if(state==State.SETTINGS)drawSettings(c); else if(state==State.PLAYING)drawGame(c); else if(state==State.PAUSED){drawGame(c); overlay(c); drawPause(c);} else if(state==State.GAME_OVER)drawGameOver(c); else if(state==State.WIN)drawWin(c);}finally{if(c!=null)holder.unlockCanvasAndPost(c);}}

    private void bg(Canvas c){
        LinearGradient g=new LinearGradient(0,0,0,H,Color.rgb(6,18,20),Color.rgb(41,90,35),Shader.TileMode.CLAMP);p.setShader(g);c.drawRect(0,0,W,H,p);p.setShader(null);
        p.setColor(0x552A5D2B); for(int i=0;i<9;i++){float x=(i*210+cloudX+W)%W;c.drawCircle(x,H*.23f,38,p);c.drawCircle(x+35,H*.22f,28,p);}
        p.setColor(0xFF1A3D20);Path hills=new Path();hills.moveTo(0,H*.58f);for(int i=0;i<=8;i++)hills.lineTo(i*W/8f,H*.48f-(i%2)*45);hills.lineTo(W,H*.58f);hills.close();c.drawPath(hills,p);
        p.setColor(0xFF173318);c.drawRect(0,H*.69f,W,H,p);p.setColor(0xFF8CC63F);c.drawRect(0,H*.69f,W,H*.705f,p);
        p.setColor(0xFF244D24);for(int i=-1;i<20;i++)c.drawRect(i*120+groundX,H*.705f,i*120+groundX+55,H*.715f,p);
    }
    private void drawMenu(Canvas c){
        bg(c); RectF tr=new RectF(W*.10f,H*.03f,W*.90f,H*.48f); drawBitmap(c,title,tr);
        button(c,"PLAY",W*.38f,H*.49f,W*.62f,H*.59f,0xFF76C72B);button(c,"HOW TO PLAY",W*.34f,H*.61f,W*.66f,H*.70f,0xFF3C8BCF);button(c,"CHARACTERS",W*.34f,H*.72f,W*.66f,H*.81f,0xFFE65B9A);button(c,"SETTINGS",W*.40f,H*.83f,W*.60f,H*.91f,0xFF666666);
        small(c,"BEST  "+fmt(best),W*.03f,H*.06f,24,Color.WHITE);
    }
    private void drawHow(Canvas c){bg(c); titleText(c,"HOW TO PLAY",W/2f,H*.12f,48,Color.WHITE);String[] a={"RUN  🏃  — Your dragon runs automatically.","JUMP 🦖  — Tap JUMP or the screen to jump.","AVOID 🌵 — Touching a cactus ends the run.","KEEP RUNNING! — Score keeps increasing forever.","Difficulty rises gradually, but stays playable."};float y=H*.28f;for(String s:a){small(c,s,W*.10f,y,27,Color.WHITE);y+=58;}button(c,"BACK",W*.42f,H*.80f,W*.58f,H*.90f,0xFF555555);}
    private void drawCharacters(Canvas c){bg(c);titleText(c,"CHOOSE YOUR DRAGON",W/2f,H*.10f,40,Color.WHITE);RectF r=new RectF(W*.34f,H*.18f,W*.66f,H*.67f);drawBitmap(c,dragons[selected],r);button(c,"◀",W*.10f,H*.70f,W*.24f,H*.82f,0xFF555555);button(c,"SELECT",W*.39f,H*.70f,W*.61f,H*.82f,0xFF76C72B);button(c,"▶",W*.76f,H*.70f,W*.90f,H*.82f,0xFF555555);String[] n={"DRAGON 1 • SLOW & FUNNY","DRAGON 2 • GETTING FASTER","DRAGON 3 • SUPER FAST","DRAGON 4 • MAX SPEED"};titleText(c,n[selected],W/2f,H*.91f,25,Color.WHITE);}
    private void drawSettings(Canvas c){bg(c);titleText(c,"SETTINGS",W/2f,H*.13f,48,Color.WHITE);small(c,"FPS",W*.24f,H*.30f,30,Color.WHITE);button(c,"30",W*.31f,H*.38f,W*.42f,H*.49f,fps==30?0xFF76C72B:0xFF555555);button(c,"60",W*.445f,H*.38f,W*.555f,H*.49f,fps==60?0xFF76C72B:0xFF555555);button(c,"90",W*.58f,H*.38f,W*.69f,H*.49f,fps==90?0xFF76C72B:0xFF555555);button(c,"SFX: "+(prefs.getBoolean("sfx",true)?"ON":"OFF"),W*.31f,H*.56f,W*.69f,H*.67f,0xFF555555);button(c,"MUSIC: "+(prefs.getBoolean("music",true)?"ON":"OFF"),W*.31f,H*.71f,W*.69f,H*.82f,0xFF555555);button(c,"BACK",W*.42f,H*.87f,W*.58f,H*.96f,0xFF555555);}
    private void drawGame(Canvas c){bg(c); // cactus
        p.setColor(0xFF4B8E32);for(RectF r:cacti){c.drawRoundRect(r,16,16,p);c.drawRoundRect(r.left-22,r.top+35,r.left+5,r.top+75,12,12,p);c.drawRoundRect(r.right-5,r.top+52,r.right+22,r.top+88,12,12,p);}drawBitmap(c,dragons[selected],new RectF(playerX,H*.67f-155,playerX+140,H*.67f+5));
        small(c,"SCORE  "+fmt(score),30,42,28,Color.WHITE);small(c,"BEST  "+fmt(best),30,76,20,0xFFE8FFD9);small(c,"FPS "+fps, W-100,42,20,Color.WHITE);button(c,"JUMP",W*.80f,H*.78f,W*.97f,H*.95f,0xFF76C72B);button(c,"Ⅱ",W*.70f,H*.04f,W*.77f,H*.13f,0xFF555555);
    }
    private void overlay(Canvas c){p.setColor(0xAA000000);c.drawRect(0,0,W,H,p);}
    private void drawPause(Canvas c){titleText(c,"PAUSED",W/2f,H*.22f,52,Color.WHITE);button(c,"RESUME",W*.40f,H*.35f,W*.60f,H*.46f,0xFF76C72B);button(c,"RESTART",W*.40f,H*.50f,W*.60f,H*.61f,0xFF3C8BCF);button(c,"MAIN MENU",W*.37f,H*.65f,W*.63f,H*.76f,0xFF555555);}
    private void drawGameOver(Canvas c){bg(c);drawBitmap(c,lose,new RectF(W*.27f,H*.08f,W*.73f,H*.70f));titleText(c,"Ayyoo Munji 🥴😫",W/2f,H*.72f,40,Color.WHITE);small(c,"Better luck next time 🤧",W/2f-145,H*.79f,25,0xFFFFE6E6);button(c,"RETRY",W*.20f,H*.84f,W*.38f,H*.94f,0xFF76C72B);button(c,"CHARACTER",W*.41f,H*.84f,W*.59f,H*.94f,0xFFE65B9A);button(c,"MAIN MENU",W*.62f,H*.84f,W*.80f,H*.94f,0xFF555555);}
    private void drawWin(Canvas c){bg(c);drawBitmap(c,win,new RectF(W*.30f,H*.06f,W*.70f,H*.70f));titleText(c,"Jeyiccheee 🙌 😸",W/2f,H*.74f,42,Color.WHITE);button(c,"PLAY AGAIN",W*.36f,H*.82f,W*.64f,H*.93f,0xFF76C72B);}
    private void drawBitmap(Canvas c,Bitmap b,RectF r){
        if(b==null)return;
        p.setAlpha(255);
        float bw=b.getWidth(), bh=b.getHeight();
        float rw=r.width(), rh=r.height();
        float sc=Math.min(rw/bw,rh/bh);
        float nw=bw*sc, nh=bh*sc;
        float l=r.left+(rw-nw)/2f;
        float t=r.top+(rh-nh)/2f;
        RectF dst=new RectF(l,t,l+nw,t+nh);
        c.drawBitmap(b,null,dst,p);
    }
    private void titleText(Canvas c,String s,float x,float y,float size,int color){text.setTextSize(size);text.setColor(color);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,text);}
    private void small(Canvas c,String s,float x,float y,float size,int color){text.setTextSize(size);text.setColor(color);text.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,text);}
    private void button(Canvas c,String label,float l,float t,float r,float b,int color){RectF q=new RectF(l,t,r,b);buttons.put(label+"@"+l,q);p.setColor(0x99000000);c.drawRoundRect(new RectF(l+4,t+5,r+4,b+5),18,18,p);p.setColor(color);c.drawRoundRect(q,18,18,p);text.setTextSize(Math.min(30,(b-t)*.42f));text.setColor(Color.WHITE);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));c.drawText(label,(l+r)/2f,(t+b)/2f-text.ascent()/2f-3,text);}
    private boolean hit(float x,float y,float l,float t,float r,float b){return x>=l&&x<=r&&y>=t&&y<=b;}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        float x=e.getX(),y=e.getY();

        if(e.getAction()==MotionEvent.ACTION_DOWN){
            if(state==State.PLAYING){
                if(hit(x,y,W*.65f,H*.65f,W*.99f,H*.99f)){
                    jump();
                    return true;
                }
                if(hit(x,y,W*.70f,H*.04f,W*.77f,H*.13f)){
                    state=State.PAUSED;
                    sfx(R.raw.click);
                    return true;
                }
            }
            return true;
        }

        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        if(state==State.MENU){if(hit(x,y,W*.38f,H*.49f,W*.62f,H*.59f)){resetGame();}else if(hit(x,y,W*.34f,H*.61f,W*.66f,H*.70f)){state=State.HOW;sfx(R.raw.click);}else if(hit(x,y,W*.34f,H*.72f,W*.66f,H*.81f)){state=State.CHARACTERS;sfx(R.raw.click);}else if(hit(x,y,W*.40f,H*.83f,W*.60f,H*.91f)){state=State.SETTINGS;sfx(R.raw.click);}}
        else if(state==State.HOW){if(hit(x,y,W*.42f,H*.80f,W*.58f,H*.90f)){state=State.MENU;sfx(R.raw.click);}}
        else if(state==State.CHARACTERS){if(hit(x,y,W*.10f,H*.70f,W*.24f,H*.82f)){selected=(selected+3)%4;prefs.edit().putInt("dragon",selected).apply();sfx(R.raw.select);}else if(hit(x,y,W*.76f,H*.70f,W*.90f,H*.82f)){selected=(selected+1)%4;prefs.edit().putInt("dragon",selected).apply();sfx(R.raw.select);}else if(hit(x,y,W*.39f,H*.70f,W*.61f,H*.82f)){state=State.MENU;sfx(R.raw.dragon);}}
        else if(state==State.SETTINGS){if(hit(x,y,W*.31f,H*.38f,W*.42f,H*.49f)){fps=30;}else if(hit(x,y,W*.445f,H*.38f,W*.555f,H*.49f)){fps=60;}else if(hit(x,y,W*.58f,H*.38f,W*.69f,H*.49f)){fps=90;}else if(hit(x,y,W*.31f,H*.56f,W*.69f,H*.67f)){prefs.edit().putBoolean("sfx",!prefs.getBoolean("sfx",true)).apply();}else if(hit(x,y,W*.31f,H*.71f,W*.69f,H*.82f)){prefs.edit().putBoolean("music",!prefs.getBoolean("music",true)).apply(); if(music!=null){if(prefs.getBoolean("music",true))music.start();else music.pause();}}else if(hit(x,y,W*.42f,H*.87f,W*.58f,H*.96f)){prefs.edit().putInt("fps",fps).apply();state=State.MENU;}prefs.edit().putInt("fps",fps).apply();sfx(R.raw.click);}
        else if(state==State.PLAYING){
    if(hit(x,y,W*.70f,H*.04f,W*.77f,H*.13f)){
        state=State.PAUSED;
        sfx(R.raw.click);
    }else if(hit(x,y,W*.75f,H*.70f,W*.99f,H*.98f)){
        jump();
    }
}
        else if(state==State.PAUSED){if(hit(x,y,W*.40f,H*.35f,W*.60f,H*.46f)){state=State.PLAYING;sfx(R.raw.click);}else if(hit(x,y,W*.40f,H*.50f,W*.60f,H*.61f)){resetGame();}else if(hit(x,y,W*.37f,H*.65f,W*.63f,H*.76f)){state=State.MENU;sfx(R.raw.click);}}
        else if(state==State.GAME_OVER){if(hit(x,y,W*.20f,H*.84f,W*.38f,H*.94f)){resetGame();}else if(hit(x,y,W*.41f,H*.84f,W*.59f,H*.94f)){state=State.CHARACTERS;}else if(hit(x,y,W*.62f,H*.84f,W*.80f,H*.94f)){state=State.MENU;sfx(R.raw.click);}}
        else if(state==State.WIN){if(hit(x,y,W*.36f,H*.82f,W*.64f,H*.93f)){resetGame();}}
        return true;
    }
}
