package bms.player.beatoraja.ir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import bms.player.beatoraja.ClearType;
import bms.model.Mode;

public class EungaIRConnection implements IRConnection {

    public static final String NAME = "EungaIR";
    public static final String HOME = "https://xn--o39a013c.tv/ir";
    public static final String VERSION = "0.0.1";

    private static final String apiUrl = "https://ir.xn--o39a013c.tv"; // Default EungaIR Backend Server URL

    private IRAccount account;

    private static File getJarDir() {
        try {
            java.security.CodeSource codeSource = EungaIRConnection.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                File jarFile = new File(codeSource.getLocation().toURI());
                return jarFile.getParentFile();
            }
        } catch (Exception e) {
            // fallback
        }
        return new File(".");
    }

    private java.util.List<String> scanDifficultyTables(String targetMd5, String targetSha256) {
        java.util.List<String> matches = new java.util.ArrayList<>();
        try {
            File tableDir = new File("table");
            if (!tableDir.exists() || !tableDir.isDirectory()) {
                tableDir = new File(getJarDir().getParentFile(), "table");
            }
            if (tableDir.exists() && tableDir.isDirectory()) {
                File[] files = tableDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".bmt")) {
                            try {
                                bms.player.beatoraja.TableData td = bms.player.beatoraja.TableData.read(f.toPath());
                                if (td != null && td.getFolder() != null) {
                                    String tableName = td.getName();
                                    for (bms.player.beatoraja.TableData.TableFolder folder : td.getFolder()) {
                                        if (folder != null && folder.getSong() != null) {
                                            String folderName = folder.getName();
                                            for (bms.player.beatoraja.song.SongData sd : folder.getSong()) {
                                                if (sd != null) {
                                                    boolean md5Match = sd.getMd5() != null && sd.getMd5().equalsIgnoreCase(targetMd5);
                                                    boolean shaMatch = sd.getSha256() != null && sd.getSha256().equalsIgnoreCase(targetSha256);
                                                    if (md5Match || shaMatch) {
                                                        matches.add(tableName + ":" + folderName);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore table read errors to avoid disrupting plays
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[EungaIR] Error scanning difficulty tables: " + e.getMessage());
        }
        return matches;
    }

    @Override
    public IRResponse<IRPlayerData> login(IRAccount account) {
        this.account = account;
        String keyToUse = account != null ? account.password : null;

        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            return createResponse(false, "EungaIR API Key is missing. Please enter your API Key in the Password field in the launcher IR settings.", null);
        }

        try {
            URL url = new URL(apiUrl + "/api/ir/auth/login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + keyToUse);
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write("{}".getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                return createResponse(false, "Authentication failed. Server returned status: " + code, null);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String respStr = response.toString();
            if (respStr.contains("\"success\":true")) {
                String whoami = extractJsonString(respStr, "whoami");
                String playerId = extractJsonString(respStr, "playerId");
                if (playerId == null || playerId.isEmpty()) {
                    playerId = extractJsonValue(respStr, "playerId");
                }

                System.out.println("[EungaIR] Authenticated successfully as player: " + whoami + " (ID: " + playerId + ")");
                IRPlayerData data = new IRPlayerData(playerId, whoami, "");
                return createResponse(true, "Authenticated successfully as " + whoami, data);
            } else {
                String error = extractJsonString(respStr, "error");
                return createResponse(false, "Auth error: " + error, null);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return createResponse(false, "Failed to connect to EungaIR backend: " + e.getMessage(), null);
        }
    }

    @Override
    public IRResponse<Object> sendPlayData(IRChartData chart, IRScoreData score) {
        String keyToUse = account != null ? account.password : null;

        if (keyToUse == null || keyToUse.trim().isEmpty()) {
            return createResponse(false, "EungaIR API Key is missing. Please configure it in the launcher.", null);
        }

        try {
            // Query detailed song metadata from local beatoraja database
            String subtitle = "";
            String subartist = "";
            String maker = "";
            String readme = "";
            int length = 0;
            double mainbpm = 0.0;
            double total = 0.0;
            int notes_normal = 0;
            int notes_ln = 0;
            int notes_scratch = 0;
            int notes_lnscratch = 0;
            boolean hasBga = false;

            try {
                bms.player.beatoraja.song.SongDatabaseAccessor accessor = bms.player.beatoraja.MainLoader.getScoreDatabaseAccessor();
                if (accessor != null) {
                    bms.player.beatoraja.song.SongData[] songDatas = accessor.getSongDatas("md5", chart.md5);
                    if (songDatas != null && songDatas.length > 0) {
                        bms.player.beatoraja.song.SongData sd = songDatas[0];
                        subtitle = sd.getSubtitle() != null ? sd.getSubtitle() : "";
                        subartist = sd.getSubartist() != null ? sd.getSubartist() : "";
                        length = sd.getLength() / 1000;
                        hasBga = sd.hasBGA();
                        bms.player.beatoraja.song.SongInformation info = null;
                        try {
                            bms.player.beatoraja.Config beatorajaConfig = bms.player.beatoraja.Config.read();
                            if (beatorajaConfig != null && beatorajaConfig.getSonginfopath() != null) {
                                bms.player.beatoraja.song.SongInformationAccessor infoAccessor = new bms.player.beatoraja.song.SongInformationAccessor(beatorajaConfig.getSonginfopath());
                                info = infoAccessor.getInformation(sd.getSha256());
                            }
                        } catch (Exception ex) {
                            System.err.println("[EungaIR] Failed to load songinfo database: " + ex.getMessage());
                        }
                        if (info != null) {
                            mainbpm = info.getMainbpm();
                            total = info.getTotal();
                            notes_normal = info.getN();
                            notes_ln = info.getLn();
                            notes_scratch = info.getS();
                            notes_lnscratch = info.getLs();
                        } else {
                            bms.player.beatoraja.song.SongInformation fallbackInfo = sd.getInformation();
                            if (fallbackInfo != null) {
                                mainbpm = fallbackInfo.getMainbpm();
                                total = fallbackInfo.getTotal();
                                notes_normal = fallbackInfo.getN();
                                notes_ln = fallbackInfo.getLn();
                                notes_scratch = fallbackInfo.getS();
                                notes_lnscratch = fallbackInfo.getLs();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[EungaIR] Failed to fetch detailed song metadata: " + e.getMessage());
            }

            // Extract pattern maker and readme from custom values map if present
            if (chart.values != null) {
                if (chart.values.containsKey("MAKER")) {
                    maker = chart.values.get("MAKER");
                } else if (chart.values.containsKey("maker")) {
                    maker = chart.values.get("maker");
                }
                
                if (chart.values.containsKey("README")) {
                    readme = chart.values.get("README");
                } else if (chart.values.containsKey("readme")) {
                    readme = chart.values.get("readme");
                }
            }

            // Scan difficulty tables for this song
            java.util.List<String> diffTables = scanDifficultyTables(chart.md5, chart.sha256);

            StringBuilder json = new StringBuilder();
            json.append("{");

            // 1. Chart
            json.append("\"chart\":{");
            json.append("\"md5\":\"").append(escapeJson(chart.md5)).append("\",");
            json.append("\"sha256\":\"").append(escapeJson(chart.sha256)).append("\",");
            json.append("\"title\":\"").append(escapeJson(chart.title)).append("\",");
            json.append("\"artist\":\"").append(escapeJson(chart.artist)).append("\",");
            json.append("\"genre\":\"").append(escapeJson(chart.genre)).append("\",");
            json.append("\"level\":").append(chart.level).append(",");
            json.append("\"notes\":").append(chart.notes).append(",");
            json.append("\"subtitle\":\"").append(escapeJson(subtitle)).append("\",");
            json.append("\"subartist\":\"").append(escapeJson(subartist)).append("\",");
            json.append("\"maker\":\"").append(escapeJson(maker)).append("\",");
            json.append("\"readme\":\"").append(escapeJson(readme)).append("\",");
            json.append("\"length\":").append(length).append(",");
            json.append("\"minbpm\":").append(chart.minbpm).append(",");
            json.append("\"maxbpm\":").append(chart.maxbpm).append(",");
            json.append("\"mainbpm\":").append(mainbpm).append(",");
            json.append("\"total\":").append(total).append(",");
            json.append("\"notes_normal\":").append(notes_normal).append(",");
            json.append("\"notes_ln\":").append(notes_ln).append(",");
            json.append("\"notes_scratch\":").append(notes_scratch).append(",");
            json.append("\"notes_lnscratch\":").append(notes_lnscratch).append(",");
            json.append("\"hasBga\":").append(hasBga).append(",");
            
            json.append("\"difficulty_tables\":[");
            for (int i = 0; i < diffTables.size(); i++) {
                json.append("\"").append(escapeJson(diffTables.get(i))).append("\"");
                if (i < diffTables.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]");
            json.append("},");

            // 2. Score
            json.append("\"score\":{");
            json.append("\"clear\":\"").append(escapeJson(score.clear != null ? score.clear.name() : "Failed")).append("\",");
            json.append("\"exscore\":").append(score.getExscore()).append(",");
            json.append("\"maxcombo\":").append(score.maxcombo).append(",");
            json.append("\"minbp\":").append(score.minbp).append(",");
            json.append("\"gauge\":").append(score.gauge).append(",");
            json.append("\"random\":").append(score.option).append(",");
            json.append("\"option\":").append(score.option).append(",");
            json.append("\"seed\":").append(score.seed).append(",");
            json.append("\"assist\":").append(score.assist).append(",");
            json.append("\"skin\":\"").append(escapeJson(score.skin)).append("\",");
            json.append("\"deviceType\":\"").append(escapeJson(score.deviceType != null ? score.deviceType.name() : "KEYBOARD")).append("\",");

            // Split Judgements
            json.append("\"epg\":").append(score.epg).append(",");
            json.append("\"lpg\":").append(score.lpg).append(",");
            json.append("\"egr\":").append(score.egr).append(",");
            json.append("\"lgr\":").append(score.lgr).append(",");
            json.append("\"egd\":").append(score.egd).append(",");
            json.append("\"lgd\":").append(score.lgd).append(",");
            json.append("\"ebd\":").append(score.ebd).append(",");
            json.append("\"lbd\":").append(score.lbd).append(",");
            json.append("\"epr\":").append(score.epr).append(",");
            json.append("\"lpr\":").append(score.lpr).append(",");
            json.append("\"ems\":").append(score.ems).append(",");
            json.append("\"lms\":").append(score.lms).append(",");

            // Gauge History
            json.append("\"gaugeHistory\":{");
            if (score.gaugeHistory != null) {
                json.append("\"easy\":").append(floatListToJson(score.gaugeHistory.easy())).append(",");
                json.append("\"groove\":").append(floatListToJson(score.gaugeHistory.groove())).append(",");
                json.append("\"hard\":").append(floatListToJson(score.gaugeHistory.hard())).append(",");
                json.append("\"exhard\":").append(floatListToJson(score.gaugeHistory.exhard()));
            } else {
                json.append("\"easy\":[],\"groove\":[],\"hard\":[],\"exhard\":[]");
            }
            json.append("}");

            json.append("},");

            // 3. Client Metadata
            json.append("\"client\":\"EungaIR-beatoraja\"");
            json.append("}");

            URL url = new URL(apiUrl + "/api/ir/score/submit");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + keyToUse);
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200 && code != 202) {
                return createResponse(false, "Submission failed. Server status: " + code, null);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String respStr = response.toString();
            if (respStr.contains("\"success\":true")) {
                String desc = extractJsonString(respStr, "description");
                return createResponse(true, desc, null);
            } else {
                String error = extractJsonString(respStr, "error");
                return createResponse(false, "Failed: " + error, null);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return createResponse(false, "Failed to submit play data: " + e.getMessage(), null);
        }
    }

    @Override
    public IRResponse<IRScoreData[]> getPlayData(IRPlayerData irpd, IRChartData chart) {
        if (chart == null) {
            // Rivals or multiple play data requests are not fully supported yet in EungaIR
            return createResponse(true, "Rival score fetch not implemented yet.", new IRScoreData[0]);
        }

        try {
            URL url = new URL(apiUrl + "/api/ir/charts/" + chart.sha256 + "/scores");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            if (code != 200) {
                return createResponse(false, "Failed to fetch rankings. Server returned status: " + code, new IRScoreData[0]);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }

            String respStr = response.toString();
            if (respStr.contains("\"success\":true")) {
                IRScoreData[] scores = parseScoresJson(respStr);
                return createResponse(true, "Fetched rankings successfully.", scores);
            } else {
                String desc = extractJsonString(respStr, "description");
                return createResponse(true, desc != null ? desc : "No rankings.", new IRScoreData[0]);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return createResponse(false, "Failed to fetch ranking play data: " + e.getMessage(), new IRScoreData[0]);
        }
    }

    @Override
    public IRResponse<IRScoreData[]> getCoursePlayData(IRPlayerData irpd, IRCourseData course) {
        return createResponse(true, "Course play not supported on EungaIR.", new IRScoreData[0]);
    }

    @Override
    public IRResponse<IRPlayerData[]> getRivals() {
        return createResponse(true, "Rivals not supported on EungaIR.", new IRPlayerData[0]);
    }

    @Override
    public IRResponse<IRTableData[]> getTableDatas() {
        return createResponse(true, "Table lists not supported on EungaIR.", new IRTableData[0]);
    }

    @Override
    public IRResponse<Object> sendCoursePlayData(IRCourseData course, IRScoreData score) {
        return createResponse(true, "Course submissions not supported on EungaIR.", null);
    }

    @Override
    public String getSongURL(IRChartData chart) {
        if (chart != null && chart.sha256 != null) {
            return HOME + "/charts/" + chart.sha256;
        }
        return null;
    }

    @Override
    public String getCourseURL(IRCourseData course) {
        return null;
    }

    @Override
    public String getPlayerURL(IRPlayerData irpd) {
        if (irpd != null && irpd.id != null) {
            return HOME + "/players/" + irpd.id;
        }
        return null;
    }

    // --- HELPER UTILITIES ---

    private <T> IRResponse<T> createResponse(final boolean success, final String message, final T data) {
        return new IRResponse<T>() {
            @Override
            public boolean isSucceeded() { return success; }
            @Override
            public String getMessage() { return message; }
            @Override
            public T getData() { return data; }
        };
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String floatListToJson(java.util.List<Float> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"(.*?)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\\s*([^,}\\s\\[\\]]+)";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private IRScoreData[] parseScoresJson(String jsonStr) {
        java.util.ArrayList<IRScoreData> list = new java.util.ArrayList<>();

        int bodyStart = jsonStr.indexOf("\"body\":[");
        if (bodyStart == -1) return new IRScoreData[0];

        int bracketCount = 1;
        int index = bodyStart + 8;
        StringBuilder bodyBuilder = new StringBuilder();
        while (index < jsonStr.length()) {
            char c = jsonStr.charAt(index);
            if (c == '[') bracketCount++;
            else if (c == ']') {
                bracketCount--;
                if (bracketCount == 0) break;
            }
            bodyBuilder.append(c);
            index++;
        }

        String bodyContent = bodyBuilder.toString().trim();
        if (bodyContent.isEmpty()) return new IRScoreData[0];

        int objectStart = -1;
        int braceCount = 0;
        for (int i = 0; i < bodyContent.length(); i++) {
            char c = bodyContent.charAt(i);
            if (c == '{') {
                if (braceCount == 0) objectStart = i;
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && objectStart != -1) {
                    String objStr = bodyContent.substring(objectStart, i + 1);
                    IRScoreData sd = parseSingleScore(objStr);
                    if (sd != null) {
                        list.add(sd);
                    }
                    objectStart = -1;
                }
            }
        }

        return list.toArray(new IRScoreData[0]);
    }

    private IRScoreData parseSingleScore(String objStr) {
        try {
            String player = extractJsonString(objStr, "player");
            String clearStr = extractJsonString(objStr, "clearStr");
            if (clearStr.isEmpty()) {
                clearStr = "Failed";
            }
            ClearType ct = ClearType.Failed;
            try {
                ct = ClearType.valueOf(clearStr);
            } catch (Exception e) {}

            int epg = parseInt(extractJsonValue(objStr, "epg"), 0);
            int lpg = parseInt(extractJsonValue(objStr, "lpg"), 0);
            int egr = parseInt(extractJsonValue(objStr, "egr"), 0);
            int lgr = parseInt(extractJsonValue(objStr, "lgr"), 0);
            int gd = parseInt(extractJsonValue(objStr, "egd"), 0); // eGD
            int lgd = parseInt(extractJsonValue(objStr, "lgd"), 0);
            int ebd = parseInt(extractJsonValue(objStr, "ebd"), 0);
            int lbd = parseInt(extractJsonValue(objStr, "lbd"), 0);
            int epr = parseInt(extractJsonValue(objStr, "epr"), 0);
            int lpr = parseInt(extractJsonValue(objStr, "lpr"), 0);
            int ems = parseInt(extractJsonValue(objStr, "ems"), 0);
            int lms = parseInt(extractJsonValue(objStr, "lms"), 0);

            int maxcombo = parseInt(extractJsonValue(objStr, "maxcombo"), 0);
            int minbp = parseInt(extractJsonValue(objStr, "minbp"), 0);
            int notes = parseInt(extractJsonValue(objStr, "notes"), 0);
            int playcount = parseInt(extractJsonValue(objStr, "playcount"), 0);
            int clearcount = parseInt(extractJsonValue(objStr, "clearcount"), 0);
            int option = parseInt(extractJsonValue(objStr, "random"), 0);
            long date = parseLong(extractJsonValue(objStr, "date"), 0L);
            int gauge = parseInt(extractJsonValue(objStr, "gauge"), 0);

            bms.player.beatoraja.ScoreData scoreData = new bms.player.beatoraja.ScoreData();
            scoreData.setPlayer(player);
            scoreData.setClear(ct.id);
            scoreData.setNotes(notes);
            scoreData.setEpg(epg);
            scoreData.setLpg(lpg);
            scoreData.setEgr(egr);
            scoreData.setLgr(lgr);
            scoreData.setEgd(gd);
            scoreData.setLgd(lgd);
            scoreData.setEbd(ebd);
            scoreData.setLbd(lbd);
            scoreData.setEpr(epr);
            scoreData.setLpr(lpr);
            scoreData.setCombo(maxcombo);
            scoreData.setMinbp(minbp);
            scoreData.setOption(option);
            scoreData.setPlaycount(playcount);
            scoreData.setClearcount(clearcount);
            scoreData.setDate(date * 1000L);

            return new IRScoreData(scoreData);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int parseInt(String val, int def) {
        try {
            if (val == null || val.trim().isEmpty()) return def;
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private long parseLong(String val, long def) {
        try {
            if (val == null || val.trim().isEmpty()) return def;
            return Long.parseLong(val.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
