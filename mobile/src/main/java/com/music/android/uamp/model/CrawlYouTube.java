package com.music.android.uamp.model;

import android.text.TextUtils;

import com.google.android.gms.cast.MediaMetadata;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.music.android.uamp.utils.LogHelper;
import com.music.android.uamp.utils.ParserHelper;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sagar on 14/12/16.
 */

public class CrawlYouTube {


    private static Map<String, String> FORMAT_LABEL = new HashMap<>();
    private Document doc;

    {
        FORMAT_LABEL.put("18", "MP4 360p");
        FORMAT_LABEL.put("22", "MP4 720p");
        FORMAT_LABEL.put("43", "WebM 360p");
        FORMAT_LABEL.put("44", "WebM 480p");
        FORMAT_LABEL.put("45", "WebM 720p");
        FORMAT_LABEL.put("46", "WebM 1080p");
        FORMAT_LABEL.put("135", "MP4 480p - no audio");
        FORMAT_LABEL.put("137", "MP4 1080p - no audio");
        FORMAT_LABEL.put("138", "MP4 2160p - no audio");
        FORMAT_LABEL.put("140", "M4A 128kbps - audio");
        FORMAT_LABEL.put("264", "MP4 1440p - no audio");
        FORMAT_LABEL.put("266", "MP4 2160p - no audio");
        FORMAT_LABEL.put("298", "MP4 720p60 - no audio");
        FORMAT_LABEL.put("299", "MP4 1080p60 - no audio");
        FORMAT_LABEL.put("171", "audio");
        FORMAT_LABEL.put("249", "audio");
        FORMAT_LABEL.put("250", "audio");
        FORMAT_LABEL.put("251", "audio");
    }

    ;


    private static Map<String, String> FORMAT_TYPE = new HashMap<>();

    {
        FORMAT_TYPE.put("18", "mp4");
        FORMAT_TYPE.put("22", "mp4");
        FORMAT_TYPE.put("43", "webm");
        FORMAT_TYPE.put("44", "webm");
        FORMAT_TYPE.put("45", "webm");
        FORMAT_TYPE.put("46", "webm");
        FORMAT_TYPE.put("135", "mp4");
        FORMAT_TYPE.put("137", "mp4");
        FORMAT_TYPE.put("138", "mp4");
        FORMAT_TYPE.put("140", "m4a");
        FORMAT_TYPE.put("264", "mp4");
        FORMAT_TYPE.put("266", "mp4");
        FORMAT_TYPE.put("298", "mp4");
        FORMAT_TYPE.put("299", "mp4");
        FORMAT_TYPE.put("171", "mp4");
        FORMAT_TYPE.put("249", "webm");
        FORMAT_TYPE.put("250", "webm");
        FORMAT_TYPE.put("251", "webm");
    }

    ;


    String FORMAT_ORDER[] = {"18", "43", "135", "44", "22", "298", "45", "137", "299", "46", "264", "138", "266", "140", "171",
            "249",
            "250",
            "251"};
    String FORMAT_RULE[] = {"mp4", "all", "webm", "none", "m4a", "all"};
    // all=display all versions, max=only highest quality version, none=no version
    // the default settings show all MP4 videos
    Boolean SHOW_DASH_FORMATS = false;

    Integer DECODE_RULE[] = {};
    Long RANDOM = 7489235179L; // Math.floor(Math.random()*1234567890);
    String CONTAINER_ID = "#download-youtube-video" + RANDOM;
    String LISTITEM_ID = "#download-youtube-video-fmt" + RANDOM;
    String BUTTON_ID = "#download-youtube-video-button" + RANDOM;
    String DEBUG_ID = "#download-youtube-video-debug-info";
    String STORAGE_URL = "#download-youtube-script-url";
    String STORAGE_CODE = "#download-youtube-signature-code";
    String STORAGE_DASH = "#download-youtube-dash-enabled";
    Boolean isDecodeRuleUpdated = false;

    public String run(String videoId) {
        String videoID, videoFormats, videoAdaptFormats, videoManifestURL, scriptURL = null;
        Boolean isSignatureUpdatingStarted = false;
        //var language=document.documentElement.getAttribute("lang");
        String textDirection = "left";
        try {
            Long currentTimeMilisec= System.currentTimeMillis();
            doc = Jsoup.connect("https://www.youtube.com/watch?v="+videoId)
                    .userAgent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:44.0) Gecko/20100101 Firefox/44.0")
                    .timeout(5000)
                    .get();
            LogHelper.e("Tag","Time to load main page"+(System.currentTimeMillis()-currentTimeMilisec)/1000);
            String title=doc.title();
            Element element=doc.getElementById("player-mole-container");
            if(element == null){
                LogHelper.e("STM","Unable to get stream data");
                return null;
            }
            String iv_invideo_url=null;
            String bodyContent = element.html();
            if (bodyContent != null) {
                String regx = "\"video_id\":s*\"([^\"]+)\"";
                videoID = findMatch(bodyContent, regx);
                regx = "\"iv_invideo_url\":s*\"([^\"]+)\"";
                iv_invideo_url = findMatch(bodyContent, regx);
                regx = "\"url_encoded_fmt_stream_map\":s*\"([^\"]+)\"";
                videoFormats = findMatch(bodyContent, regx);
                regx = "\"adaptive_fmts\":s*\"([^\"]+)\"";
                videoAdaptFormats = findMatch(bodyContent, regx);
                regx = "\"dashmpd\":s*\"([^\"]+)\"";
                videoManifestURL = findMatch(bodyContent, regx);
                regx = "\"js\":s*\"([^\"]+)\"";
                if (scriptURL == null) {
                    scriptURL = findMatch(bodyContent, regx);
                    if (scriptURL != null) {
                        scriptURL = scriptURL.replace("\\", "");
                        scriptURL = "https:" + scriptURL;
                        currentTimeMilisec= System.currentTimeMillis();
                        String data = fetchDataFromUrl(scriptURL);
                        LogHelper.e("Tag","TIme took to load javascript"+(System.currentTimeMillis()-currentTimeMilisec)/1000);
                        findSignatureCode(data);
                        // System.out.print(js.html());
                    }
                }
                String sep1 = "%2C", sep2 = "%26", sep3 = "%3D";
                if (videoFormats.contains(",") ) {
                    sep1 = ",";
                    sep2 = (videoFormats.contains("&") ) ? "&" : "\\\\u0026";
                    sep3 = "=";
                }
                Map<String,String> videoURL = new HashMap();
                Map<String,String> videoSignature = new HashMap();
                if (videoAdaptFormats != null) {
                    videoFormats = videoFormats + sep1 + videoAdaptFormats;
                }
                String[] videoFormatsGroup = videoFormats.split(sep1);
                for (int i = 0; i < videoFormatsGroup.length; i++) {
                    String videoFormatsElem[] = videoFormatsGroup[i].split(sep2);
                    Map<String,String> videoFormatsPair = new HashMap();
                    for (int j = 0; j < videoFormatsElem.length; j++) {
                        String[] pair = videoFormatsElem[j].split(sep3);
                        if (pair.length == 2) {
                            videoFormatsPair.put(pair[0], pair[1]);
                        }
                    }
                    if (videoFormatsPair.get("url") == null) continue;
                    String url = StringEscapeUtils.unescapeJava(StringEscapeUtils.unescapeJava(videoFormatsPair.get("url"))).replace("/\\//g", "/").replace("/\\u0026/g", "&");
                    if (videoFormatsPair.get("itag") == null) continue;
                    String itag = videoFormatsPair.get("itag");
                    String sig = videoFormatsPair.get("sig");
                    if(sig == null){
                        sig=videoFormatsPair.get("signature");
                    }
                    if (sig != null) {
                        url = url + "&signature=" + sig;
                        videoSignature.put(itag, null);
                    } else if (videoFormatsPair.containsKey("s")) {
                        url = url + "&signature=" + decryptSignature(videoFormatsPair.get("s"));
                        videoSignature.put(itag,videoFormatsPair.get("s"));
                    }
                    if (url.toLowerCase().indexOf("ratebypass") == -1) { // speed up download for dash
                        url = url + "&ratebypass=yes";
                    }
                    if (url.toLowerCase().indexOf("http") == 0) { // validate URL
                        videoURL.put(itag, url + "&title=video");
                    }
                }

                Map<String,String> showFormat = new HashMap();
                for (String category : FORMAT_RULE) {
                    //String rule = FORMAT_RULE[category];
                    FORMAT_TYPE.get(category);
                    for (String index : FORMAT_TYPE.keySet()) {
                        String value= FORMAT_TYPE.get(index);
                        if (value.equalsIgnoreCase(category)) {
                            showFormat.put(index,  "all");
                        }
                    }
                    /*if (rule == "max") {
                        for (var i = FORMAT_ORDER.length - 1; i >= 0; i--) {
                            var format = FORMAT_ORDER[i];
                            if (FORMAT_TYPE[format] == category && videoURL[format] != undefined) {
                                showFormat[format] = true;
                                break;
                            }
                        }
                    }*/
                }

               /* String dashPref = getPref(STORAGE_DASH);
                if (dashPref == "1") {
                    SHOW_DASH_FORMATS = true;
                } else if (dashPref != "0") {
                    setPref(STORAGE_DASH, "0");
                }*/


                List<YoutubeMetaData> metaDataList=new ArrayList<>();
                for (int i = 0; i < FORMAT_ORDER.length; i++) {
                    String format = FORMAT_ORDER[i];
                    if (format.equalsIgnoreCase("37") && videoURL.get(format) == null) { // hack for dash 1080p
                        if (videoURL.get("137") != null) {
                            format = "137";
                        }
                        showFormat.put(format, showFormat.get("37"));
                    } else if (format == "38" && videoURL.get(format) == null) { // hack for dash 4K
                        if (videoURL.get("138") != null && videoURL.get("266") == null) {
                            format = "138";
                        }
                        showFormat.put(format, showFormat.get("38"));
                    }
                    //if (!SHOW_DASH_FORMATS && format.length > 2) continue;
                    if (videoURL.get(format) != null && FORMAT_LABEL.get(format) != null && showFormat.get(format) != null) {
                        YoutubeMetaData youtubeMetaData=new YoutubeMetaData();
                        youtubeMetaData.setUrl(java.net.URLDecoder.decode(videoURL.get(format), "UTF-8"));
                        youtubeMetaData.setFormat(format);
                        youtubeMetaData.setSig(videoSignature.get(format));
                        youtubeMetaData.setLabel(FORMAT_LABEL.get(format));
                        metaDataList.add(youtubeMetaData);
                        debug("DYVAM - Info: itag" + format + " url:" + java.net.URLDecoder.decode(videoURL.get(format)) + " label:" +FORMAT_LABEL.get(format));
                    }
                }

                Map<String, List<YoutubeMetaData>>data= new HashMap<>();
                YoutubeMetaDataList youtubeMetaDataList=new YoutubeMetaDataList();
                youtubeMetaDataList.setUrls(metaDataList);
                //data.put("urls",metaDataList);
                if(iv_invideo_url != null) {
                    try {
                        Set<Long> audioSlices=new HashSet<>();
                        String url = StringEscapeUtils.unescapeJava(StringEscapeUtils.unescapeJava(iv_invideo_url)).replace("/\\//g", "/").replace("/\\u0026/g", "&");
                        currentTimeMilisec= System.currentTimeMillis();
                        doc = Jsoup.connect(java.net.URLDecoder.decode(url))
                                .userAgent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:44.0) Gecko/20100101 Firefox/44.0")
                                .timeout(5000)
                                .parser(Parser.xmlParser())
                                .get();
                        LogHelper.e("Tag","Time took to load annotations"+(System.currentTimeMillis()-currentTimeMilisec)/1000);
                        currentTimeMilisec= System.currentTimeMillis();
                        for (Element e : doc.select("annotation")) {
                            Elements action = e.select("action");
                            if (action != null && !action.isEmpty() && action.first().attr("type").equalsIgnoreCase("openUrl")) {
                               Element urlEle= action.first().select("url").first();
                                if(urlEle.attr("target").equalsIgnoreCase("current")){
                                    String rawDur=findMatch(action.first().select("url").first().attr("value"),"\\#t=(.*)");
                                    if(rawDur == null){
                                        continue;
                                    }
                                    Long dur=ParserHelper.getDurationfromString(rawDur);
                                    audioSlices.add(dur);
                                }
                            }
                        }
                        LogHelper.e("Tag","Time took to parse annotations"+(System.currentTimeMillis()-currentTimeMilisec)/1000);
                        List<Long> durs=new ArrayList<Long>(audioSlices);
                        Collections.sort(durs);
                        LogHelper.e("Tag","durations list "+ durs.size());
                        youtubeMetaDataList.setDurations(durs);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
                Gson gson=new GsonBuilder()
                        .disableHtmlEscaping()
                        .create();
                String json=gson.toJson(youtubeMetaDataList);
                //debug(json);
                return json;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void debug(String message){
        System.out.println(message);
    }

    public void findSignatureCode(String sourceCode) {
        Long currentTimeMilisec= System.currentTimeMillis();

        //Logger ("DYVAM - Info: signature start " + getPref(STORAGE_CODE));
        String regex = "\\.set\\s*\\(\"signature\"\\s*,\\s*([a-zA-Z0-9_$][\\w$]*)\\(";
        String regex1 = "\\.sig\\s*\\|\\|\\s*([a-zA-Z0-9_$][\\w$]*)\\(/";
        String regex2 = "\\.signature\\s*=\\s*([a-zA-Z_$][\\w$]*)\\([a-zA-Z_$][\\w$]*\\)/";
        String signatureFunctionName =
                findMatch(sourceCode, regex);
        if (signatureFunctionName == null) {
            signatureFunctionName =
                    findMatch(sourceCode, regex1);
        }
        if (signatureFunctionName == null) {
            signatureFunctionName =
                    findMatch(sourceCode, regex2);
        }

        if (signatureFunctionName == null) {
            setPref(STORAGE_CODE, "error");
            return;
        }

        signatureFunctionName = signatureFunctionName.replace("$", "\\$");
        String regCode = signatureFunctionName + "\\s*=\\s*function" + "\\s*\\([\\w$]*\\)\\s*\\{[\\w$]*=[\\w$]*\\.split\\(\"\"\\);\n*(.+?);return [\\w$]*\\.join";
        String regCode2 = "function \\s*" + signatureFunctionName + "\\s*\\([\\w$]*\\)\\s*\\{[\\w$]*=[\\w$]*\\.split\\(\"\"\\);\n*(.+?);return [\\w$]*\\.join";
        String functionCode = findMatch(sourceCode, regCode);
        if (functionCode == null) {
            functionCode = findMatch(sourceCode, regCode2);
        }
        //debug("DYVAM - Info: signaturefunction " + signatureFunctionName + " -- " + functionCode);
        if (functionCode == null) {
            setPref(STORAGE_CODE, "error");
            return;
        }
        regex = "([\\w$]*)\\s*:\\s*function\\s*\\(\\s*[\\w$]*\\s*\\)\\s*\\{\\s*(?:return\\s*)?[\\w$]*\\.reverse\\s*\\(\\s*\\)\\s*\\}";
        String reverseFunctionName = findMatch(sourceCode, regex);
        //debug("DYVAM - Info: reversefunction " + reverseFunctionName);
        if (reverseFunctionName != null)
            reverseFunctionName = reverseFunctionName.replace("$", "\\$");
        String sliceFunctionName = findMatch(sourceCode,
                "([\\w$]*)\\s*:\\s*function\\s*\\(\\s*[\\w$]*\\s*,\\s*[\\w$]*\\s*\\)\\s*\\{\\s*(?:return\\s*)?[\\w$]*\\.(?:slice|splice)\\(.+\\)\\s*\\}");
        //debug("DYVAM - Info: slicefunction " + sliceFunctionName);
        if (sliceFunctionName != null) sliceFunctionName = sliceFunctionName.replace("$", "\\$");

        String regSlice = "\\.(?:" + "slice" + (sliceFunctionName != null ? "|" + sliceFunctionName : "") + ")\\s*\\(\\s*(?:[a-zA-Z_$][\\w$]*\\s*,)?\\s*([0-9]+)\\s*\\)";
        String regReverse = "\\.(?:" + "reverse" + (reverseFunctionName != null ? "|" + reverseFunctionName : "") + ")\\s*\\([^\\)]*\\)";

        String regSwap = "[\\w$]+\\s*\\(\\s*[\\w$]+\\s*,\\s*([0-9]+)\\s*\\)";
        String regInline = "[\\w$]+\\[0\\]\\s*=\\s*[\\w$]+\\[([0-9]+)\\s*%\\s*[\\w$]+\\.length\\]";
        String functionCodePieces[] = functionCode.split(";");
        List<Integer> decodeArray = new ArrayList<>();
        for (int i = 0; i < functionCodePieces.length; i++) {
            functionCodePieces[i] = functionCodePieces[i].trim();
            String codeLine = functionCodePieces[i];
            if (codeLine.length() > 0) {
                String arrSlice[] = matchforSlice(codeLine, regSlice);//codeLine.match(regSlice);
                String arrReverse[] = matchForReverse(codeLine, regReverse);//codeLine.match(regReverse);
                //debug(i + ": " + codeLine + " --" + (arrSlice ? " slice length " + arrSlice.length : "") + " " + (arrReverse ? "reverse" : ""));
                if (arrSlice != null && arrSlice.length >= 2) { // slice
                    Integer slice = parseInt(arrSlice[1], 10);
                    decodeArray.add(-slice);

                } else if (arrReverse != null && arrReverse.length >= 1) { // reverse
                    decodeArray.add(0);
                } else if (codeLine.contains("[0]")) { // inline swap
                    if (i + 2 < functionCodePieces.length &&
                            functionCodePieces[i + 1].contains(".length") &&
                            functionCodePieces[i + 1].contains("[0]")) {
                        String inline = findMatch(functionCodePieces[i + 1], regInline);
                        Integer value = parseInt(inline, 10);
                        decodeArray.add(value);
                        i += 2;
                    } else {
                        setPref(STORAGE_CODE, "error");
                        return;
                    }
                    ;
                } else if (codeLine.contains(",")) { // swap
                    String swap = findMatch(codeLine, regSwap);
                    Integer value = parseInt(swap, 10);
                    if (value != null && value > 0) {
                        decodeArray.add(value);
                    } else {
                        setPref(STORAGE_CODE, "error");
                        return;
                    }
                    ;
                } else {
                    setPref(STORAGE_CODE, "error");
                    return;
                }

            }
        }

        if (!decodeArray.isEmpty()) {
            //setPref(STORA
            // GE_URL, scriptURL);
            setPref(STORAGE_CODE, decodeArray.toString());
            DECODE_RULE = decodeArray.toArray(new Integer[decodeArray.size()]);
            //debug("DYVAM - Info: signature " + decodeArray.toString() + " " + scriptURL);
            // update download links and add file sizes
                   /* for (var i = 0; i < downloadCodeList.length; i++) {
                        var elem = $(LISTITEM_ID + downloadCodeList[i].format);
                        var url = downloadCodeList[i].url;
                        var sig = downloadCodeList[i].sig;
                        if (elem && url && sig) {
                            url = url.replace(/\&signature=[\w\.]+/, "&signature=" + decryptSignature(sig));
                            elem.parentNode.setAttribute("href", url);
                            addFileSize(url, downloadCodeList[i].format);
                        }
                    }*/
        }
        LogHelper.e("Tag","Time to get signature "+(System.currentTimeMillis()-currentTimeMilisec)/1000);
    }

    public String setPref(String code, String error) {
        return error;
    }

    public Integer parseInt(String value, Integer number) {
        try {
            return Integer.parseInt(value, number);
        } catch (Exception e) {
            return null;
        }
    }

    public String findMatch(String content, String regexp) {
        Matcher matcher = Pattern.compile(regexp).matcher(content);
        while (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public String[] matchforSlice(String content, String regexp) {
        Matcher matcher = Pattern.compile(regexp).matcher(content);
        String values[] = new String[2];
        while (matcher.find()) {
            values[0] = matcher.group(0);
            values[1] = matcher.group(1);
            return values;
        }
        return null;
    }

    public String[] matchForReverse(String content, String regexp) {
        Matcher matcher = Pattern.compile(regexp).matcher(content);
        String values[] = new String[1];
        while (matcher.find()) {
            values[0] = matcher.group(0);
            return values;
        }
        return null;
    }

    public String findfullMatch(String content, String regexp) {
        Matcher matcher = Pattern.compile(regexp).matcher(content);
        while (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private char [] swap(char a[], int b) {
        char c = a[0];
        a[0] = a[b % a.length];
        a[b] = c;
        return a;
    };

    private char[] reverse(char values[]){
       char[]reverse=new char[values.length];
        int x=0;
        for(int i=reverse.length-1;i >=0;i--){
           reverse[x++]=values[i];
        }
        return reverse;
    }

    private String decode(String sig, Integer arr[]) { // encoded decryption
        sig=sig.trim();
        char[] sigA = sig.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            Integer act = arr[i];
            sigA = (act > 0) ? swap(sigA, act) : ((act == 0) ? reverse(sigA) : slice(sigA,-act));
        }
        return new String(sigA);
    }


    private char[] slice(char[] sigA, int slice) {
        char[] slicelist=new char[(sigA.length-slice)];
        int x=0;
        for(int i=slice;i<sigA.length;i++){
            slicelist[x++]=sigA[i];
        }
        return slicelist;
    }

    public String decryptSignature(String sig) {

        if (sig == null) return "";
        Integer arr[] = DECODE_RULE;
        if (arr != null && arr.length >0) {
            String sig2 = decode(sig, arr);
            if (sig2 != null) return sig2;
        } else {
            setPref(STORAGE_URL, "");
            setPref(STORAGE_CODE, "");
        }
        return sig;
    }

    public static void main(String args[]) {
        CrawlYouTube crawlYouTube = new CrawlYouTube();
        crawlYouTube.run("jgvLkIc_MMY");
    }

    public static String fetchDataFromUrl(String urlString) throws JSONException {
        BufferedReader reader = null;
        try {
            URLConnection urlConnection = new URL(urlString).openConnection();
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            int status = httpURLConnection.getResponseCode();
            InputStream in;
            if (status >= 400) {
                in = httpURLConnection.getErrorStream();
            } else {
                in = urlConnection.getInputStream();
            }
            reader = new BufferedReader(new InputStreamReader(
                    in, "iso-8859-1"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            //LogHelper.e(TAG, "Failed to parse the json for media list", e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
