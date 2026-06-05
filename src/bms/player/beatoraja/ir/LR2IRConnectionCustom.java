package bms.player.beatoraja.ir;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import bms.player.beatoraja.ClearType;
import bms.model.Mode;

public class LR2IRConnectionCustom implements IRConnection {

    public static final String NAME = "BMS-IR";
    public static final String HOME = "http://www.dream-pro.info/~lavalse/LR2IR/";
    public static final String VERSION = "1.2.7";

    private IRAccount account;

    @Override
    public IRResponse<IRPlayerData> login(IRAccount account) {
        this.account = account;
        System.out.println("[BMS-IR] Verifying Player ID: " + account.id);
        
        try {
            // Call getplayerxml.cgi to verify the ID and fetch the player's nickname
            String param = "id=" + account.id + "&lastupdate=0";
            URL url = new URL("http://www.dream-pro.info/~lavalse/LR2IR/2/getplayerxml.cgi");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Connection", "close");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(param.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return createResponse(false, "Failed to connect to LR2IR server (code: " + responseCode + ")", null);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "MS932"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
            String respStr = response.toString();
            int hashIndex = respStr.indexOf('#');
            String xmlContent = hashIndex != -1 ? respStr.substring(hashIndex + 1).trim() : respStr.trim();

            if (xmlContent.isEmpty()) {
                return createResponse(false, "Invalid player ID (no data returned).", null);
            }

            // Strip optional leading <?xml ... ?> declaration to avoid parse error when wrapped
            if (xmlContent.startsWith("<?xml")) {
                int endDecl = xmlContent.indexOf("?>");
                if (endDecl != -1) {
                    xmlContent = xmlContent.substring(endDecl + 2).trim();
                }
            }

            // Wrap XML in a single root tag to parse correctly in standard parser
            String wrappedXml = "<root>" + xmlContent + "</root>";

            // Parse XML to extract the nickname
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(wrappedXml)));
            doc.getDocumentElement().normalize();

            NodeList nameNodes = doc.getElementsByTagName("rivalname");
            String playerName = "";
            if (nameNodes != null && nameNodes.getLength() > 0) {
                playerName = nameNodes.item(0).getTextContent();
            }

            if (playerName == null || playerName.trim().isEmpty() || playerName.equals("NOPLAYER")) {
                return createResponse(false, "Player ID not found on LR2IR.", null);
            }

            System.out.println("[BMS-IR] Logged in successfully as player: " + playerName);
            IRPlayerData data = new IRPlayerData(account.id, playerName, "");
            return createResponse(true, "Authenticated successfully as " + playerName, data);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[BMS-IR] Login verification failed: " + e.getMessage());
            return createResponse(false, "Login verification failed: " + e.getMessage(), null);
        }
    }

    @Override
    public IRResponse<Object> sendPlayData(IRChartData chart, IRScoreData score) {
        System.out.println("[BMS-IR] Preparing to send play data for chart: " + chart.title + " (MD5: " + chart.md5 + ")");
        
        if (account == null) {
            System.err.println("[BMS-IR] Error: Account is null. Not logged in.");
            return createResponse(false, "Not logged in.", null);
        }

        try {
            // Check seed compatibility with LR2 seed range (0-32766)
            long seedVal = score.seed;
            long p1Seed = seedVal % 16777216;
            long p2Seed = seedVal / 16777216;
            if (p1Seed < 0 || p1Seed > 32766 || p2Seed < 0 || p2Seed > 32766) {
                System.err.println("[BMS-IR] Submission blocked: Random seed is out of LR2 range (0-32766). P1: " + p1Seed + ", P2: " + p2Seed + ". Please use the modified lr2oraja client.");
                return createResponse(false, "Submission blocked: Seed is out of LR2 range. Please use the modified lr2oraja client.", null);
            }

            // 1. Map Clear Type
            int lr2Clear = 0; // NO PLAY
            if (score.clear != null) {
                switch (score.clear) {
                    case Failed:
                        lr2Clear = 1;
                        break;
                    case AssistEasy:
                    case LightAssistEasy:
                        lr2Clear = 1; // Map Assist Easy to Fail
                        break;
                    case Easy:
                        lr2Clear = 2;
                        break;
                    case Normal:
                        lr2Clear = 3;
                        break;
                    case Hard:
                    case ExHard:
                        lr2Clear = 4;
                        break;
                    case FullCombo:
                    case Perfect:
                    case Max:
                        lr2Clear = 5;
                        break;
                    default:
                        lr2Clear = 0;
                        break;
                }
            }

            // 2. Map Keymode (line)
            int lr2Keymode = 7; // Default fallback to 7K
            if (chart.mode != null) {
                switch (chart.mode) {
                    case BEAT_5K:
                    case POPN_5K:
                        lr2Keymode = 5;
                        break;
                    case BEAT_7K:
                        lr2Keymode = 7;
                        break;
                    case POPN_9K:
                        lr2Keymode = 9;
                        break;
                    case BEAT_10K:
                        lr2Keymode = 10;
                        break;
                    case BEAT_14K:
                        lr2Keymode = 14;
                        break;
                }
            }

            // 3. Map Random option and Gauge
            int lr2Random = (score.option >= 0 && score.option <= 5) ? score.option : 0;
            int lr2Gauge = 0; // Default GROOVE
            if (score.gauge == 4 || score.gauge == 5) {
                lr2Gauge = 3; // EASY
            } else if (score.gauge == 1 || score.gauge == 2) {
                lr2Gauge = 1; // HARD
            } else if (score.gauge == 3) {
                lr2Gauge = 2; // HAZARD
            }
            int op_best = lr2Gauge + lr2Random * 10;

            // 4. Calculate stats
            int exscore = score.getExscore();
            int pg = score.epg + score.lpg;
            int gr = score.egr + score.lgr;
            int gd = score.egd + score.lgd;
            int bd = score.ebd + score.lbd;
            int pr = score.epr + score.lpr;
            
            int rate = 0;
            if (chart.notes > 0) {
                rate = (exscore * 100) / (chart.notes * 2);
            }

            String songmd5 = chart.md5 != null ? chart.md5.toLowerCase() : "";
            String passmd5 = md5(account.password);

            // 5. Calculate scorehash
            String scorehashRaw = passmd5 + songmd5 + exscore + lr2Clear;
            String scorehash = md5(scorehashRaw);

            // 6. Build query string using MS932 encoding for Japanese character compatibility
            String param = "songmd5=" + songmd5
                + "&id=" + account.id
                + "&passmd5=" + passmd5
                + "&title=" + URLEncoder.encode(chart.title != null ? chart.title : "", "MS932")
                + "&genre=" + URLEncoder.encode(chart.genre != null ? chart.genre : "", "MS932")
                + "&artist=" + URLEncoder.encode(chart.artist != null ? chart.artist : "", "MS932")
                + "&maxbpm=" + chart.maxbpm
                + "&minbpm=" + chart.minbpm
                + "&&playlevel=" + chart.level
                + "&clear=" + lr2Clear
                + "&exscore=" + exscore
                + "&pg=" + pg
                + "&gr=" + gr
                + "&gd=" + gd
                + "&bd=" + bd
                + "&pr=" + pr
                + "&maxcombo=" + score.maxcombo
                + "&playcount=1"
                + "&clearcount=" + (lr2Clear >= 2 ? 1 : 0)
                + "&rate=" + rate
                + "&minbp=" + score.minbp
                + "&totalnotes=" + chart.notes
                + "&opt_history=" + op_best
                + "&opt_this=" + op_best
                + "&line=" + lr2Keymode
                + "&judge=" + (chart.judge >= 0 && chart.judge <= 3 ? chart.judge : 2)
                + "&inputtype=0"
                + "&ghost=Z"
                + "&rseed=" + (int)score.seed
                + "&clear_db=0"
                + "&clear_ex=0"
                + "&clear_sd=0"
                + "&scorehash=" + scorehash;

            System.out.println("[BMS-IR] Parameters prepared. Submitting to score.cgi...");

            URL url = new URL("http://www.dream-pro.info/~lavalse/LR2IR/2/score.cgi");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Connection", "close");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(param.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            System.out.println("[BMS-IR] Server response code: " + responseCode);

            if (responseCode == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "MS932"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                }
                String respStr = response.toString();
                System.out.println("[BMS-IR] Response content:\n" + respStr);
                return createResponse(true, "Score submitted successfully. Server response: " + respStr, null);
            } else {
                return createResponse(false, "Server returned response code: " + responseCode, null);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[BMS-IR] Error sending play data: " + e.getMessage());
            return createResponse(false, "Exception occurred: " + e.getMessage(), null);
        }
    }

    @Override
    public IRResponse<IRScoreData[]> getPlayData(IRPlayerData irpd, IRChartData chart) {
        if (irpd == null && chart == null) {
            return createResponse(false, "Both player and chart are null.", new IRScoreData[0]);
        }

        if (chart == null) {
            // Fetch ALL scores for a player (Rival score updates)
            System.out.println("[BMS-IR] Fetching rival/player scores for ID: " + irpd.id + " (Name: " + irpd.name + ")");
            
            try {
                String param = "id=" + irpd.id + "&lastupdate=0";
                URL url = new URL("http://www.dream-pro.info/~lavalse/LR2IR/2/getplayerxml.cgi");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(param.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    return createResponse(false, "Server returned response code: " + responseCode, new IRScoreData[0]);
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "MS932"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                }
                String respStr = response.toString();
                int hashIndex = respStr.indexOf('#');
                String xmlContent = hashIndex != -1 ? respStr.substring(hashIndex + 1).trim() : respStr.trim();

                if (xmlContent.isEmpty()) {
                    return createResponse(true, "No scores found.", new IRScoreData[0]);
                }

                // Strip optional leading <?xml ... ?> declaration to avoid parse error when wrapped
                if (xmlContent.startsWith("<?xml")) {
                    int endDecl = xmlContent.indexOf("?>");
                    if (endDecl != -1) {
                        xmlContent = xmlContent.substring(endDecl + 2).trim();
                    }
                }

                String wrappedXml = "<root>" + xmlContent + "</root>";
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(wrappedXml)));
                doc.getDocumentElement().normalize();

                NodeList nameNodes = doc.getElementsByTagName("rivalname");
                String playerName = irpd.name != null ? irpd.name : irpd.id;
                if (nameNodes != null && nameNodes.getLength() > 0) {
                    playerName = nameNodes.item(0).getTextContent();
                }

                NodeList nList = doc.getElementsByTagName("score");
                java.util.ArrayList<IRScoreData> list = new java.util.ArrayList<>();

                for (int temp = 0; temp < nList.getLength(); temp++) {
                    org.w3c.dom.Node nNode = nList.item(temp);
                    if (nNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;

                        String hash = getTagValue("hash", eElement);
                        if (hash == null || hash.equals("0")) {
                            hash = getTagValue("songmd5", eElement);
                        }
                        int lr2Clear = Integer.parseInt(getTagValue("clear", eElement));
                        int combo = Integer.parseInt(getTagValue("combo", eElement));
                        int pg = Integer.parseInt(getTagValue("pg", eElement));
                        int gr = Integer.parseInt(getTagValue("gr", eElement));
                        int gd = Integer.parseInt(getTagValue("gd", eElement));
                        int bd = Integer.parseInt(getTagValue("bd", eElement));
                        int pr = Integer.parseInt(getTagValue("pr", eElement));
                        int minbp = Integer.parseInt(getTagValue("minbp", eElement));
                        int option = Integer.parseInt(getTagValue("option", eElement));
                        int playcount = Integer.parseInt(getTagValue("playcount", eElement));
                        int notes = Integer.parseInt(getTagValue("notes", eElement));

                        bms.player.beatoraja.ScoreData scoreData = new bms.player.beatoraja.ScoreData();
                        scoreData.setPlayer(playerName);
                        scoreData.setSha256(hash); // LR2IR score lists return BMS MD5 in <hash>

                        int beatorajaClear = 1; // Failed
                        switch (lr2Clear) {
                            case 2: beatorajaClear = 4; break; // Easy
                            case 3: beatorajaClear = 5; break; // Normal
                            case 4: beatorajaClear = 6; break; // Hard
                            case 5:
                                if (pg + gr == notes && notes > 0 && minbp == 0) {
                                    beatorajaClear = 9; // Perfect Clear
                                } else {
                                    beatorajaClear = 8; // FullCombo
                                }
                                break;
                        }
                        scoreData.setClear(beatorajaClear);
                        scoreData.setNotes(notes);
                        scoreData.setEpg(pg);
                        scoreData.setLpg(0);
                        scoreData.setEgr(gr);
                        scoreData.setLgr(0);
                        scoreData.setEgd(gd);
                        scoreData.setLgd(0);
                        scoreData.setEbd(bd);
                        scoreData.setLbd(0);
                        scoreData.setEpr(pr);
                        scoreData.setLpr(0);
                        scoreData.setCombo(combo);
                        scoreData.setMinbp(minbp);
                        scoreData.setOption(option);
                        scoreData.setPlaycount(playcount);

                        list.add(new IRScoreData(scoreData));
                    }
                }

                IRScoreData[] scores = list.toArray(new IRScoreData[0]);
                System.out.println("[BMS-IR] Successfully fetched " + scores.length + " scores for player: " + playerName);
                return createResponse(true, "Fetched rival scores successfully.", scores);

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("[BMS-IR] Error fetching rival play data: " + e.getMessage());
                return createResponse(false, "Exception occurred: " + e.getMessage(), new IRScoreData[0]);
            }
        }

        // Fetch scores for a specific chart
        System.out.println("[BMS-IR] Fetching ranking data for chart: " + chart.title + " (MD5: " + chart.md5 + ")");
        
        if (account == null) {
            return createResponse(false, "Not logged in.", new IRScoreData[0]);
        }

        try {
            String songmd5 = chart.md5 != null ? chart.md5.toLowerCase() : "";
            
            // Build parameter string
            String param = "songmd5=" + songmd5
                + "&id=" + account.id
                + "&lastupdate=";

            System.out.println("[BMS-IR] Sending request to getrankingxml.cgi...");
            URL url = new URL("http://www.dream-pro.info/~lavalse/LR2IR/2/getrankingxml.cgi");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Connection", "close");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(param.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return createResponse(false, "Server returned response code: " + responseCode, new IRScoreData[0]);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "MS932"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
            String respStr = response.toString();

            // Find # delimiter
            int hashIndex = respStr.indexOf('#');
            String xmlContent;
            if (hashIndex != -1) {
                xmlContent = respStr.substring(hashIndex + 1).trim();
            } else {
                xmlContent = respStr.trim();
            }

            if (xmlContent.isEmpty()) {
                return createResponse(true, "No ranking data.", new IRScoreData[0]);
            }

            // Strip optional leading <?xml ... ?> declaration to avoid parse error when wrapped
            if (xmlContent.startsWith("<?xml")) {
                int endDecl = xmlContent.indexOf("?>");
                if (endDecl != -1) {
                    xmlContent = xmlContent.substring(endDecl + 2).trim();
                }
            }

            // Wrap XML in a single root tag to parse correctly in standard parser
            String wrappedXml = "<root>" + xmlContent + "</root>";

            // Parse XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(wrappedXml)));
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("score");
            java.util.ArrayList<IRScoreData> list = new java.util.ArrayList<>();

            for (int temp = 0; temp < nList.getLength(); temp++) {
                org.w3c.dom.Node nNode = nList.item(temp);
                if (nNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;

                    String name = getTagValue("name", eElement);
                    int lr2Clear = Integer.parseInt(getTagValue("clear", eElement));
                    int combo = Integer.parseInt(getTagValue("combo", eElement));
                    int pg = Integer.parseInt(getTagValue("pg", eElement));
                    int gr = Integer.parseInt(getTagValue("gr", eElement));
                    int gd = Integer.parseInt(getTagValue("gd", eElement));
                    int bd = Integer.parseInt(getTagValue("bd", eElement));
                    int pr = Integer.parseInt(getTagValue("pr", eElement));
                    int minbp = Integer.parseInt(getTagValue("minbp", eElement));
                    int option = Integer.parseInt(getTagValue("option", eElement));
                    int playcount = Integer.parseInt(getTagValue("playcount", eElement));
                    int notes = Integer.parseInt(getTagValue("notes", eElement));

                    bms.player.beatoraja.ScoreData scoreData = new bms.player.beatoraja.ScoreData();
                    scoreData.setPlayer(name);
                    scoreData.setSha256(chart.sha256);
                    
                    int beatorajaClear = 1; // Failed
                    switch (lr2Clear) {
                        case 2: beatorajaClear = 4; break; // Easy
                        case 3: beatorajaClear = 5; break; // Normal
                        case 4: beatorajaClear = 6; break; // Hard
                        case 5:
                            if (pg + gr == notes && notes > 0 && minbp == 0) {
                                beatorajaClear = 9; // Perfect Clear
                            } else {
                                beatorajaClear = 8; // FullCombo
                            }
                            break;
                    }
                    scoreData.setClear(beatorajaClear);
                    scoreData.setNotes(notes);
                    scoreData.setEpg(pg);
                    scoreData.setLpg(0);
                    scoreData.setEgr(gr);
                    scoreData.setLgr(0);
                    scoreData.setEgd(gd);
                    scoreData.setLgd(0);
                    scoreData.setEbd(bd);
                    scoreData.setLbd(0);
                    scoreData.setEpr(pr);
                    scoreData.setLpr(0);
                    scoreData.setCombo(combo);
                    scoreData.setMinbp(minbp);
                    scoreData.setOption(option);
                    scoreData.setPlaycount(playcount);

                    list.add(new IRScoreData(scoreData));
                }
            }

            IRScoreData[] scores = list.toArray(new IRScoreData[0]);
            System.out.println("[BMS-IR] Successfully parsed " + scores.length + " ranking entries.");
            return createResponse(true, "Fetched rankings successfully.", scores);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[BMS-IR] Error fetching play data: " + e.getMessage());
            return createResponse(false, "Exception occurred: " + e.getMessage(), new IRScoreData[0]);
        }
    }

    @Override
    public IRResponse<Object> sendCoursePlayData(IRCourseData course, IRScoreData score) {
        return createResponse(true, "Course play not supported.", null);
    }

    @Override
    public IRResponse<IRScoreData[]> getCoursePlayData(IRPlayerData irpd, IRCourseData course) {
        return createResponse(true, "Retrieve course play data not supported.", new IRScoreData[0]);
    }

    @Override
    public IRResponse<IRPlayerData[]> getRivals() {
        return createResponse(true, "Rivals list not supported.", new IRPlayerData[0]);
    }

    @Override
    public IRResponse<IRTableData[]> getTableDatas() {
        return createResponse(true, "Table data not supported.", new IRTableData[0]);
    }

    @Override
    public String getSongURL(IRChartData chart) {
        if (chart.md5 != null) {
            return "http://www.dream-pro.info/~lavalse/LR2IR/search.cgi?mode=ranking&bmsmd5=" + chart.md5.toLowerCase();
        }
        return null;
    }

    @Override
    public String getCourseURL(IRCourseData course) {
        return null;
    }

    @Override
    public String getPlayerURL(IRPlayerData irpd) {
        if (irpd.id != null) {
            return "http://www.dream-pro.info/~lavalse/LR2IR/search.cgi?mode=player&playerid=" + irpd.id;
        }
        return null;
    }

    private <T> IRResponse<T> createResponse(final boolean success, final String message, final T data) {
        return new IRResponse<T>() {
            @Override
            public boolean isSucceeded() {
                return success;
            }

            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public T getData() {
                return data;
            }
        };
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            org.w3c.dom.Node node = nodeList.item(0);
            if (node != null) {
                return node.getTextContent();
            }
        }
        return "0";
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
