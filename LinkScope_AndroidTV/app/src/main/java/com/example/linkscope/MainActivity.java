package com.example.linkscope;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int WHITE = Color.rgb(247,249,255);
    private static final int MUTED = Color.rgb(141,159,193);
    private static final int BLUE = Color.rgb(92,142,255);
    private static final int GREEN = Color.rgb(0,233,163);
    private static final int RED = Color.rgb(255,85,102);
    private static final int AMBER = Color.rgb(255,191,36);
    private static final int PANEL = Color.rgb(16,29,51);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(4);
    private final Map<String,TestEngine.Result> results=new HashMap<>();
    private final Map<String,CardParts> cardParts=new HashMap<>();
    private final Set<String> testing=new HashSet<>();
    private SharedPreferences prefs;
    private LinearLayout body;
    private TextView refreshButton, smallStatus;
    private int generation=0, dotPhase=0;
    private boolean destroyed=false;

    private static class CardParts {
        final TestEngine.Target target;
        final TextView pill, latency, info;
        CardParts(TestEngine.Target target,TextView pill,TextView latency,TextView info) {
            this.target=target; this.pill=pill; this.latency=latency; this.info=info;
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("linkscope", MODE_PRIVATE);
        getWindow().setFlags(1024, 1024); // Fullscreen.
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        buildPage();
        ui.postDelayed(this::testAll, 450);
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                if(destroyed)return;
                dotPhase=(dotPhase+1)%3;
                for(String id:new ArrayList<>(testing))updateCard(id);
                ui.postDelayed(this,360);
            }
        },360);
    }
    @Override protected void onDestroy(){ destroyed=true;generation++;workers.shutdownNow();super.onDestroy(); }
    @Override public void onConfigurationChanged(Configuration newConfig){super.onConfigurationChanged(newConfig);buildPage();}

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String s,float sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER);
        return t;
    }
    private GradientDrawable background(int[] colors,int angle,float radius,int border,int borderColor){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.values()[angle],colors);
        d.setCornerRadius(dp(radius));if(border>0)d.setStroke(dp(border),borderColor);return d;
    }
    private GradientDrawable solid(int color,int radius,int stroke,int strokeColor){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke>0)d.setStroke(dp(stroke),strokeColor);return d;
    }
    private static LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}

    private int cols(){
        float screen=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
        int pixels=getResources().getDisplayMetrics().widthPixels;
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        return screen >= 930 || (landscape && pixels >= 1200) ? 4 : screen>=570?2:1;
    }
    private void buildPage(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setBackground(background(new int[]{0xff090F20,0xff0B1931,0xff080E1E},0,0,0,0));
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18),dp(20),dp(18),dp(20));
        scroll.addView(shell,new ScrollView.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(background(new int[]{0xff15213B,0xff0B162B,0xff172B52},7,30,1,0xff344B86));
        panel.setPadding(dp(21),dp(12),dp(21),dp(18));
        shell.addView(panel,lp(-1,-2));

        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(3),dp(5),dp(3),dp(9));
        panel.addView(header,lp(-1,dp(cols()==1?86:91)));
        TextView chain=text("↪",35,0xffA8C4FF,true);
        header.addView(chain,lp(dp(46),dp(50)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ttl=new LinearLayout.LayoutParams(0,-2,1);ttl.leftMargin=dp(8);header.addView(titles,ttl);
        TextView title=text("连通性测试",cols()==1?29:38,WHITE,true);
        title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);titles.addView(title,lp(-1,-2));
        smallStatus=text("独立网络检测  ·  " + (isProxyOn()?"HTTP PROXY":"SYSTEM NETWORK"),11,MUTED,false);
        smallStatus.setGravity(Gravity.START);LinearLayout.LayoutParams sub=lp(-1,-2);sub.topMargin=dp(5);titles.addView(smallStatus,sub);
        TextView configure=action("☷", "管理测试项目",this::chooseTargets);
        LinearLayout.LayoutParams b=lp(dp(52),dp(52));b.rightMargin=dp(10);header.addView(configure,b);
        TextView proxy=action("⚙", "代理设置",this::proxyDialog);
        b=lp(dp(52),dp(52));b.rightMargin=dp(10);header.addView(proxy,b);
        refreshButton=action("⟳", "全部重新检测",this::testAll);
        header.addView(refreshButton,lp(dp(52),dp(52)));

        View line=new View(this);line.setBackgroundColor(0x29374667);panel.addView(line,lp(-1,dp(1)));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp=lp(-1,-2);bp.topMargin=dp(18);panel.addView(body,bp);
        renderCards();

        TextView foot=text("点击卡片重新检测  ·  长按查看响应详情  ·  结果不代表账号订阅或实际视频播放能力",11,0xff8A9CBA,false);
        foot.setGravity(Gravity.CENTER);foot.setPadding(dp(12),dp(20),dp(12),dp(8));shell.addView(foot,lp(-1,-2));
        setContentView(scroll);
    }
    private TextView action(String label,String hint,Runnable call){
        TextView b=text(label,30,0xffAAC5FF,true);
        b.setBackground(solid(0xff1D2B4D,50,1,0xff34456B));
        b.setContentDescription(hint);b.setFocusable(true);b.setClickable(true);
        b.setOnClickListener(v->call.run());
        b.setOnFocusChangeListener((v,has)->{
            b.setBackground(solid(has?0xff2D4272:0xff1D2B4D,50,has?2:1,has?BLUE:0xff34456B));
            v.animate().scaleX(has?1.08f:1f).scaleY(has?1.08f:1f).setDuration(110).start();
        });
        return b;
    }
    private Set<String> selected(){
        String str=prefs.getString("targets","viutv,mytvsuper,gemini,openai");
        return new LinkedHashSet<>(Arrays.asList(str.split(",")));
    }
    private void renderCards(){
        if(body==null)return;
        body.removeAllViews();cardParts.clear();
        List<TestEngine.Target> chosen=new ArrayList<>();
        Set<String> saved=selected();
        for(TestEngine.Target t:TestEngine.TARGETS) if(saved.contains(t.id))chosen.add(t);
        int perRow=cols();
        for(int i=0;i<chosen.size();i+=perRow){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            body.addView(row,lp(-1,-2));
            int end=Math.min(i+perRow,chosen.size());
            for(int j=i;j<end;j++) {
                LinearLayout card=makeCard(chosen.get(j));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(perRow==4?345:perRow==2?345:330),1);
                cp.setMargins(dp(6),dp(6),dp(6),dp(8));row.addView(card,cp);
            }
            for(int j=end;j<i+perRow;j++){
                View gap=new View(this);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,dp(1),1);
                gp.setMargins(dp(6),0,dp(6),0);row.addView(gap,gp);
            }
        }
        for(String id:cardParts.keySet()) updateCard(id);
    }
    private LinearLayout makeCard(TestEngine.Target target){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(12),dp(23),dp(12),dp(21));
        card.setBackground(background(new int[]{0xff1B2848,0xff111F34,0xff1B2949},7,27,1,0xff314267));
        card.setFocusable(true);card.setClickable(true);
        card.setOnClickListener(v->testOne(target.id));
        card.setOnLongClickListener(v->{showDetail(target);return true;});
        card.setOnFocusChangeListener((v,on)->{
            card.setBackground(background(new int[]{0xff26395F,0xff142742,0xff26385B},7,27,on?2:1,on?BLUE:0xff314267));
            card.animate().scaleX(on?1.027f:1f).scaleY(on?1.027f:1f).setDuration(115).start();
        });
        FrameLayout top=new FrameLayout(this);
        card.addView(top,lp(-1,dp(118)));
        if(target.icon!=null&&!target.icon.isEmpty()) {
            try {
                InputStream in=getAssets().open("icons/"+target.icon);
                android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeStream(in);in.close();
                ImageView icon=new ImageView(this);icon.setImageBitmap(image);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(114),dp(114),Gravity.CENTER);
                top.addView(icon,ip);
            } catch(Exception e){addFallbackLogo(top,target);}
        } else addFallbackLogo(top,target);
        float cardDp=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density/cols();
        TextView name=text(target.title,cols()==4 && cardDp<265?21:cols()==4?27:29,WHITE,true);
        name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np=lp(-1,dp(45));np.topMargin=dp(4);card.addView(name,np);
        TextView kind=text(target.category,12,0xff879ABC,false);
        LinearLayout.LayoutParams kp=lp(-1,dp(21));card.addView(kind,kp);
        View spacer=new View(this);card.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        TextView pill=text("未测试",20,0xffB0BBD1,true);
        pill.setBackground(solid(0xff263249,50,1,0xff43546E));
        card.addView(pill,lp(-1,dp(49)));
        TextView latency=text("— ms",31,WHITE,true);
        LinearLayout.LayoutParams lat=lp(-1,dp(49));lat.topMargin=dp(8);card.addView(lat,lat);
        TextView info=text("",11,MUTED,false);
        info.setSingleLine(true);info.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(info,lp(-1,dp(18)));
        cardParts.put(target.id,new CardParts(target,pill,latency,info));
        return card;
    }
    private void addFallbackLogo(FrameLayout root,TestEngine.Target target){
        String letters=target.title.length()>2?target.title.substring(0,2).toUpperCase(Locale.ROOT):target.title;
        int color=target.id.equals("netflix")?0xffED2834:target.id.equals("youtube")?0xffFF4444:
                  target.id.equals("hoytv")?0xff36BCE5:target.id.equals("rthk")?0xff31B483:0xff6699FF;
        TextView mark=text(letters,30,WHITE,true);mark.setBackground(solid(color,62,0,color));
        FrameLayout.LayoutParams m=new FrameLayout.LayoutParams(dp(106),dp(106),Gravity.CENTER);root.addView(mark,m);
    }
    private static int statusColor(TestEngine.Status status) {
        switch(status) {
            case UNLOCKED:return GREEN;
            case BLOCKED:case FAILED:return RED;
            case TESTING:case LIMITED:case UNCERTAIN:return AMBER;
            default:return MUTED;
        }
    }
    private void updateCard(String id){
        CardParts c=cardParts.get(id);if(c==null)return;
        TestEngine.Result r=results.get(id);
        boolean active=testing.contains(id);
        TestEngine.Status status=active?TestEngine.Status.TESTING:r==null?TestEngine.Status.IDLE:r.status;
        String label;
        switch(status){
            case TESTING:label=" ●   测试中";break;
            case UNLOCKED:
                if(!empty(r.region))label="●  "+flag(r.region)+" "+r.region;
                else label="●  "+(id.equals("openai")||id.equals("gemini")?"可访问":"已解锁");break;
            case BLOCKED:label="●  未解锁";break;
            case LIMITED:label="●  有限解锁";break;
            case UNCERTAIN:label="●  待确认";break;
            case FAILED:label="●  连接失败";break;
            default:label="○  未测试";
        }
        int color=statusColor(status);
        c.pill.setText(label);c.pill.setTextColor(color);
        int bg=status==TestEngine.Status.UNLOCKED?0xff0A493C:
            status==TestEngine.Status.BLOCKED||status==TestEngine.Status.FAILED?0xff452332:
            status==TestEngine.Status.TESTING||status==TestEngine.Status.UNCERTAIN||status==TestEngine.Status.LIMITED?0xff493A23:0xff253248;
        c.pill.setBackground(solid(bg,50,1,color));
        if(active){
            String dots=dotPhase==0?"•  ·  ·":dotPhase==1?"·  •  ·":"·  ·  ●";
            SpannableString span=new SpannableString(dots+"  ms");
            span.setSpan(new ForegroundColorSpan(BLUE),dots.length(),span.length(),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            c.latency.setText(span);c.info.setText("正在请求测试接口…");
        }else if(r!=null){
            String num=r.latency+"";
            SpannableString s=new SpannableString(num+" ms");
            s.setSpan(new ForegroundColorSpan(BLUE),num.length(),s.length(),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new RelativeSizeSpan(.70f),num.length(),s.length(),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            c.latency.setText(s);
            c.info.setText(r.httpCode>0?"HTTP "+r.httpCode:"网络超时或异常");
        } else {c.latency.setText("— ms");c.info.setText("等待检测");}
    }
    private static boolean empty(String v){return v==null||v.isEmpty();}
    private static String flag(String region){
        if(region==null||region.length()!=2)return "🌐";
        String s=region.toUpperCase(Locale.ROOT);
        return new String(Character.toChars(0x1f1e6+(s.charAt(0)-'A')))+
               new String(Character.toChars(0x1f1e6+(s.charAt(1)-'A')));
    }
    private boolean isProxyOn(){return !empty(prefs.getString("proxyHost",""));}
    private TestEngine engine(){
        String h=prefs.getString("proxyHost","");int p=prefs.getInt("proxyPort",7890);
        return new TestEngine(h,p);
    }
    private void testAll(){
        generation++;final int token=generation;
        testing.clear();Set<String> ids=selected();testing.addAll(ids);
        for(String id:ids){results.remove(id);updateCard(id);}
        if(refreshButton!=null)refreshButton.setText("◌");
        final TestEngine engine=engine();
        for(String id:ids) dispatch(id,token,engine);
    }
    private void testOne(String id){
        if(testing.contains(id))return;
        testing.add(id);results.remove(id);updateCard(id);
        dispatch(id,generation,engine());
    }
    private void dispatch(String id,int token,TestEngine checker){
        workers.submit(()->{
            TestEngine.Result r=checker.run(id);
            ui.post(()->{
                if(destroyed||token!=generation)return;
                testing.remove(id);results.put(id,r);updateCard(id);
                if(testing.isEmpty()&&refreshButton!=null)refreshButton.setText("⟳");
            });
        });
    }
    private void showDetail(TestEngine.Target target){
        TestEngine.Result r=results.get(target.id);
        if(r==null){new AlertDialog.Builder(this).setTitle(target.title).setMessage("还没有检测结果。点击卡片开始测试。")
            .setPositiveButton("确定",null).show();return;}
        String value="状态："+r.status+"\n地区："+(empty(r.region)?"未知":r.region)+
            "\nHTTP："+(r.httpCode<=0?"未连接":r.httpCode)+"\n响应耗时："+r.latency+" ms\n"+
            "出口 IP："+(empty(r.ip)?"未知":r.ip)+"\n测试地址："+r.endpoint+"\n\n"+
            r.explanation+"\n\n注意：HTTP 响应耗时不是 ICMP Ping；解锁检测可能随网站接口变更而失效。";
        new AlertDialog.Builder(this).setTitle(target.title+" · 检测详情").setMessage(value)
            .setNegativeButton("关闭",null).setPositiveButton("重新检测",(d,w)->testOne(target.id)).show();
    }
    private void chooseTargets(){
        final List<TestEngine.Target> all=TestEngine.TARGETS;
        String[] titles=new String[all.size()];boolean[] checked=new boolean[all.size()];
        Set<String> saved=selected();
        for(int i=0;i<all.size();i++){titles[i]=all.get(i).title+"  ·  "+all.get(i).category;checked[i]=saved.contains(all.get(i).id);}
        new AlertDialog.Builder(this).setTitle("选择测试项目")
            .setMultiChoiceItems(titles,checked,(d,i,b)->checked[i]=b)
            .setNegativeButton("取消",null).setPositiveButton("保存并测试",(d,w)->{
                List<String> out=new ArrayList<>();for(int i=0;i<all.size();i++)if(checked[i])out.add(all.get(i).id);
                if(out.isEmpty()){Toast.makeText(this,"至少需要一个测试项目",Toast.LENGTH_SHORT).show();return;}
                prefs.edit().putString("targets",android.text.TextUtils.join(",",out)).apply();
                generation++;testing.clear();buildPage();testAll();
            }).show();
    }
    private void proxyDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(3),dp(22),0);
        TextView help=text("留空主机表示使用系统网络；可填 Mihomo 本地 HTTP/mixed 端口。",14,0xff4A5874,false);
        help.setGravity(Gravity.START);box.addView(help,lp(-1,dp(55)));
        EditText host=new EditText(this);host.setSingleLine(true);host.setHint("HTTP 代理主机，例如 127.0.0.1");host.setText(prefs.getString("proxyHost",""));box.addView(host,lp(-1,dp(58)));
        EditText port=new EditText(this);port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);port.setHint("代理端口，例如 7890");
        port.setText(String.valueOf(prefs.getInt("proxyPort",7890)));box.addView(port,lp(-1,dp(58)));
        new AlertDialog.Builder(this).setTitle("测试连接方式").setView(box)
            .setNeutralButton("使用系统网络",(d,w)->{
                prefs.edit().remove("proxyHost").apply();buildPage();testAll();
            }).setNegativeButton("取消",null).setPositiveButton("应用 HTTP 代理",(d,w)->{
                String h=host.getText().toString().trim();int p;
                try{p=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){p=-1;}
                if(h.isEmpty()||p<1||p>65535){Toast.makeText(this,"请填写正确的代理主机和端口",Toast.LENGTH_LONG).show();return;}
                prefs.edit().putString("proxyHost",h).putInt("proxyPort",p).apply();buildPage();testAll();
            }).show();
    }
}
