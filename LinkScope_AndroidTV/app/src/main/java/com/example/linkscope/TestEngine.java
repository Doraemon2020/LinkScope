package com.example.linkscope;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/**
 * Independent HTTP-based connectivity and region-check probes.
 * Adapted from the public Bettbox media-unlock-check algorithms (GPL-3.0).
 * These tests probe HTTP endpoint responses, NOT video playback/account entitlement.
 */
public final class TestEngine {
    public enum Status { IDLE, TESTING, UNLOCKED, BLOCKED, LIMITED, UNCERTAIN, FAILED }

    public static final class Target {
        public final String id, title, category, icon;
        Target(String id, String title, String category, String icon) {
            this.id=id; this.title=title; this.category=category; this.icon=icon;
        }
    }
    public static final List<Target> TARGETS = Collections.unmodifiableList(Arrays.asList(
        new Target("viutv", "ViuTV", "香港串流", "viutv.png"),
        new Target("mytvsuper", "myTV SUPER", "香港串流", "mytvsuper.png"),
        new Target("gemini", "Gemini", "AI 服务", "gemini.png"),
        new Target("openai", "OpenAI", "AI 服务", "openai.png"),
        new Target("hoytv", "HOY TV", "香港串流", ""),
        new Target("rthk", "RTHK", "香港串流", ""),
        new Target("netflix", "Netflix", "国际串流", ""),
        new Target("youtube", "YouTube", "国际串流", ""),
        new Target("cloudflare", "Cloudflare", "网络服务", ""),
        new Target("github", "GitHub", "网络服务", "")
    ));

    public static final class Result {
        public final Status status;
        public final String region, explanation, endpoint, ip;
        public final int httpCode;
        public final long latency;
        public Result(Status status, String region, String explanation, String endpoint,
                      String ip, int httpCode, long latency) {
            this.status=status; this.region=region; this.explanation=explanation;
            this.endpoint=endpoint; this.ip=ip; this.httpCode=httpCode; this.latency=latency;
        }
    }
    private static final int TIMEOUT = 4500;
    private static final int MAX_BODY = 384 * 1024;
    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final Set<String> OPENAI_UNSUPPORTED = new HashSet<>(Arrays.asList(
        "CN", "HK", "MO", "RU", "IR", "KP", "SY", "CU", "VE", "BY", "AF", "SS", "YE", "ZW", "MM", "SD", "SO", "CF"));
    private static final Set<String> GEMINI_UNSUPPORTED = new HashSet<>(Arrays.asList(
        "CN", "MO", "RU", "BY", "IR", "KP", "SY", "CU", "VE", "MM", "SD", "AF", "SS", "YE", "ZW"));

    private final Proxy proxy;
    public TestEngine(String host, int port) {
        if (host != null && !host.trim().isEmpty() && port > 0 && port <= 65535) {
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host.trim(), port));
        } else proxy = Proxy.NO_PROXY;
    }

    private static class Reply {
        int code; String body, url, location;
        Reply(int code,String body,String url,String location) {
            this.code=code; this.body=body; this.url=url; this.location=location;
        }
    }

    private Reply request(String endpoint, String method, String body, boolean follow) throws IOException {
        URL url = new URL(endpoint);
        URLConnection raw = proxy == Proxy.NO_PROXY ? url.openConnection() : url.openConnection(proxy);
        if (!(raw instanceof HttpsURLConnection)) throw new IOException("只允许 HTTPS 检测");
        HttpsURLConnection conn=(HttpsURLConnection)raw;
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setInstanceFollowRedirects(follow);
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", BROWSER_UA);
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setRequestProperty("Accept", "*/*");
        conn.setUseCaches(false);
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
        }
        try {
            if(body!=null) try(OutputStream out=conn.getOutputStream()) {out.write(body.getBytes(StandardCharsets.UTF_8));}
            int code=conn.getResponseCode();
            String loc=conn.getHeaderField("Location");
            String content="";
            if(!"HEAD".equals(method)) {
                InputStream in = code>=400 ? conn.getErrorStream() : conn.getInputStream();
                if(in!=null) try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buf=new byte[4096]; int got,remaining=MAX_BODY;
                    while(remaining>0&&(got=stream.read(buf,0,Math.min(buf.length,remaining)))!=-1) {
                        out.write(buf,0,got);remaining-=got;
                    }
                    content=out.toString("UTF-8");
                }
            }
            return new Reply(code,content,conn.getURL().toString(),loc);
        } finally {conn.disconnect();}
    }
    private static String field(String raw,String key) {
        try { return new JSONObject(raw).optString(key, ""); } catch(Exception e){return "";}
    }
    private Result result(Status status,String region,String reason,String url,int code,String ip,long ms) {
        return new Result(status,region,reason,url,ip,code,Math.max(1,ms));
    }
    private Result checkViu(long started) throws IOException {
        final String url="https://api.viu.now.com/p8/3/getLiveURL";
        final String payload="{\"callerReferenceNo\":\"20210726112323\",\"contentId\":\"099\",\"contentType\":\"Channel\",\"channelno\":\"099\",\"mode\":\"prod\",\"deviceId\":\"29b3cb117a635d5b56\",\"deviceType\":\"ANDROID_WEB\"}";
        Reply r=request(url,"POST",payload,true);
        String rc=field(r.body,"responseCode");
        Status s="SUCCESS".equalsIgnoreCase(rc)?Status.UNLOCKED:
                "GEO_CHECK_FAIL".equalsIgnoreCase(rc)?Status.BLOCKED:Status.UNCERTAIN;
        String reason=s==Status.UNLOCKED?"ViuTV 播放接口通过地区检测":s==Status.BLOCKED?
                "播放接口返回 GEO_CHECK_FAIL":"响应不能明确判断解锁状态（responseCode="+rc+"）";
        return result(s,s==Status.UNLOCKED?"HK":"",reason,url,r.code,"",elapsed(started));
    }
    private Result checkMyTv(long started) throws IOException {
        final String url="https://www.mytvsuper.com/api/auth/getSession/self/";
        Reply r=request(url,"GET",null,true);
        try {
            JSONObject data=new JSONObject(r.body);
            if(r.code==200&&data.has("supported_country")) {
                boolean supported=data.optBoolean("supported_country",false);
                String region=data.optString("country_code","").toUpperCase(Locale.ROOT);
                return result(supported?Status.UNLOCKED:Status.BLOCKED,region,
                    supported?"myTV SUPER 确认当前 IP 所在地区可用":"myTV SUPER 判定当前 IP 所在地区不支持",url,r.code,"",elapsed(started));
            }
        } catch(Exception ignored) {}
        return result(Status.UNCERTAIN,"","接口返回的数据不包含有效地区判断",url,r.code,"",elapsed(started));
    }
    private Result checkGemini(long started) throws IOException {
        final String url="https://gemini.google.com/";
        Reply r=request(url,"GET",null,true);
        String lower=r.body.toLowerCase(Locale.ROOT);
        String[] denied={"is not supported in your country","isn't supported in your country",
            "not available in your country","unavailable in your country",
            "is not currently supported","isn't currently supported"};
        boolean blocked=false;
        for(String p:denied)if(lower.contains(p)){blocked=true;break;}
        String region="";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(",2,1,200,\"([A-Z]{3})\"").matcher(r.body);
        if(m.find())region=region3to2(m.group(1));
        else {
            m=java.util.regex.Pattern.compile("\\[1,null,null,\\d+,\\d+,\"([A-Z]{3})\"").matcher(r.body);
            if(m.find())region=region3to2(m.group(1));
        }
        if(!region.isEmpty()&&GEMINI_UNSUPPORTED.contains(region))blocked=true;
        Status s=blocked?Status.BLOCKED:r.code==200?Status.UNLOCKED:Status.UNCERTAIN;
        return result(s,region,blocked?"检测到 Gemini 地区限制提示":r.code==200?
            "Gemini 页面正常响应，不能保证登录及全部功能可用":"HTTP 状态未能确认 Gemini 服务可用",url,r.code,"",elapsed(started));
    }
    private static String region3to2(String code) {
        switch(code) {case "HKG":return "HK";case "CHN":return "CN";case "USA":return "US";
            case "JPN":return "JP";case "SGP":return "SG";case "TWN":return "TW";
            case "MAC":return "MO";case "RUS":return "RU";case "GBR":return "GB";
            default:return code;}
    }
    private Result checkOpenAI(long started) throws IOException {
        final String url="https://chatgpt.com/cdn-cgi/trace";
        Reply r=request(url,"GET",null,true);
        String region="",ip="";
        if(r.code==200)for(String line:r.body.split("\\r?\\n")) {
            if(line.startsWith("loc="))region=line.substring(4).trim().toUpperCase(Locale.ROOT);
            if(line.startsWith("ip="))ip=line.substring(3).trim();
        }
        Status s;
        String why;
        if(!ip.isEmpty()&&!region.isEmpty()) {
            s=OPENAI_UNSUPPORTED.contains(region)?Status.BLOCKED:Status.UNLOCKED;
            why=s==Status.UNLOCKED?"Cloudflare 检测位置不在已知限制区域；仅作初步判断":"Cloudflare 检测位置属于已知限制区域";
        } else if(r.code==403 || r.code==503 || r.body.contains("cf-chl-")) {
            s=Status.UNCERTAIN;why="存在 Cloudflare 安全验证，无法可靠判断解锁";
        } else {s=Status.UNCERTAIN;why="未取得可供判断的 Cloudflare trace 数据";}
        return result(s,region,why,url,r.code,ip,elapsed(started));
    }
    private Result checkHoy(long started) throws IOException {
        final String url="https://hoytv-live-stream.hoy.tv/ch77/index-fhd.m3u8";
        Reply r=request(url,"HEAD",null,false);
        Status s=r.code==200?Status.UNLOCKED:r.code==403?Status.BLOCKED:Status.UNCERTAIN;
        return result(s,s==Status.UNLOCKED?"HK":"", "HOY TV 直播地址 HTTP "+r.code,url,r.code,"",elapsed(started));
    }
    private Result checkRthk(long started) throws IOException {
        final String url="https://rthktv31-live.akamaized.net/hls/live/2036818/RTHKTV31/stream1/streamPlaylist.m3u8";
        Reply r=request(url,"HEAD",null,false);
        Status s=r.code==200?Status.UNLOCKED:r.code==403?Status.BLOCKED:Status.UNCERTAIN;
        return result(s,s==Status.UNLOCKED?"HK":"", "RTHK 直播地址 HTTP "+r.code,url,r.code,"",elapsed(started));
    }
    private Result checkNetflix(long started) throws IOException {
        final String url="https://www.netflix.com/title/70143836";
        Reply r=request(url,"GET",null,false);
        boolean full=r.code==200||((r.code==301||r.code==302)&&r.location!=null&&r.location.contains("/title/70143836"));
        if(full) return result(Status.UNLOCKED,"","Netflix 完整解锁探测 URL 可访问（仅作为参考）",url,r.code,"",elapsed(started));
        final String limitedUrl="https://www.netflix.com/title/81280792";
        Reply r2=request(limitedUrl,"GET",null,false);
        boolean limited=(r2.code==301||r2.code==302)&&r2.location!=null&&r2.location.contains("/title/81280792");
        return result(limited?Status.LIMITED:Status.UNCERTAIN,"",limited?
            "仅检测到 Netflix 有限内容可用":"Netflix 响应未能确定解锁情况",limitedUrl,r2.code,"",elapsed(started));
    }
    private Result checkYouTube(long started) throws IOException {
        final String url="https://www.youtube.com/premium";
        Reply r=request(url,"GET",null,true);
        String text=r.body.toLowerCase(Locale.ROOT);
        boolean limited=text.contains("premium is not available in your country")||text.contains("youtube premium is not available");
        Status s=limited?Status.BLOCKED:r.code==200?Status.UNLOCKED:Status.UNCERTAIN;
        return result(s,"",limited?"YouTube Premium 页面提示地区不支持":
            r.code==200?"YouTube Premium 页面可访问（不代表已经订阅）":"无法判断 YouTube Premium 可用情况",url,r.code,"",elapsed(started));
    }
    private Result checkCloudflare(long started) throws IOException {
        final String url="https://www.cloudflare.com/cdn-cgi/trace";
        Reply r=request(url,"GET",null,true);
        String loc="",ip="";
        if(r.code==200)for(String line:r.body.split("\\r?\\n")) {
            if(line.startsWith("loc="))loc=line.substring(4).trim();
            if(line.startsWith("ip="))ip=line.substring(3).trim();
        }
        return result(ip.isEmpty()?Status.UNCERTAIN:Status.UNLOCKED,loc,
            ip.isEmpty()?"无法读取 Cloudflare Trace":"Cloudflare 网络访问正常",url,r.code,ip,elapsed(started));
    }
    private Result checkGithub(long started) throws IOException {
        final String url="https://github.com/";
        Reply r=request(url,"HEAD",null,true);
        Status s=(r.code>=200&&r.code<400)?Status.UNLOCKED:Status.UNCERTAIN;
        return result(s,"","GitHub HTTP "+r.code,url,r.code,"",elapsed(started));
    }
    private static long elapsed(long nanos){return (System.nanoTime()-nanos)/1000000;}
    public Result run(String id) {
        long started=System.nanoTime();
        try {
            switch(id) {
                case "viutv":return checkViu(started);
                case "mytvsuper":return checkMyTv(started);
                case "gemini":return checkGemini(started);
                case "openai":return checkOpenAI(started);
                case "hoytv":return checkHoy(started);
                case "rthk":return checkRthk(started);
                case "netflix":return checkNetflix(started);
                case "youtube":return checkYouTube(started);
                case "cloudflare":return checkCloudflare(started);
                case "github":return checkGithub(started);
                default:return result(Status.FAILED,"","未知测试项目","",0,"",elapsed(started));
            }
        } catch(Exception err) {
            String msg=err.getMessage();
            if(msg==null||msg.isEmpty())msg=err.getClass().getSimpleName();
            return result(Status.FAILED,"","网络请求失败："+msg,"",0,"",elapsed(started));
        }
    }
}
