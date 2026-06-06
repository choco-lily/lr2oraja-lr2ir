package bms.player.beatoraja.ir;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import bms.player.beatoraja.pattern.LR2Random;

public class LR2IRConnectionCustom implements IRConnection {

    public static final String NAME = "BMS-IR";
    public static final String HOME = "https://www.bms-ir.org/~lavalse/LR2IR/";
    public static final String VERSION = "1.2.9";

    private IRAccount account;

    @Override
    public IRResponse<IRPlayerData> login(IRAccount account) {
        ensureSpoofConfigTemplate();
        this.account = account;
        System.out.println("[BMS-IR] Verifying Player ID: " + account.id);
        
        try {
            // Call getplayerxml.cgi to verify the ID and fetch the player's nickname
            String param = "id=" + account.id + "&lastupdate=0";
            URL url = new URL("https://www.bms-ir.org/~lavalse/LR2IR/2/getplayerxml.cgi");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("User-Agent", getUserAgent());
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

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
            schedulePopn9kCachePrewarm();
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
            // Map random option to compatible seed if RANDOM/ROTATE is used
            long seedVal = score.seed;
            bms.player.beatoraja.pattern.Random resolvedRandom = normalizeLR2Random(bms.player.beatoraja.pattern.Random.getRandom((int)(score.option % 10), chart.mode));
            if (resolvedRandom == bms.player.beatoraja.pattern.Random.RANDOM || resolvedRandom == bms.player.beatoraja.pattern.Random.ROTATE) {
                long resolvedSeed = resolveUploadSeed(chart.mode, score.option, score.seed);
                System.out.println("[BMS-IR] Resolved RANDOM/ROTATE play seed from beatoraja " + score.seed + " to LR2 seed: " + resolvedSeed);
                seedVal = resolvedSeed;
            } else if (resolvedRandom == bms.player.beatoraja.pattern.Random.S_RANDOM) {
                seedVal = 0L;
            }

            // Check seed compatibility with LR2 seed range (0-32766)
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

            int playcount = score.playcount;
            int clearcount = score.clearcount;

            try {
                // Fetch player's current stats for this song from LR2IR
                URL url = new URL("https://www.bms-ir.org/~lavalse/LR2IR/2/getplayerxml.cgi");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("User-Agent", getUserAgent());
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                String fetchParam = "id=" + account.id + "&lastupdate=0";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(fetchParam.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int respCode = conn.getResponseCode();
                if (respCode == 200) {
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
                    if (!xmlContent.isEmpty()) {
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

                        NodeList nList = doc.getElementsByTagName("score");
                        for (int temp = 0; temp < nList.getLength(); temp++) {
                            org.w3c.dom.Node nNode = nList.item(temp);
                            if (nNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element eElement = (Element) nNode;
                                String hash = getTagValue("hash", eElement);
                                if (hash == null || hash.equals("0")) {
                                    hash = getTagValue("songmd5", eElement);
                                }
                                if (hash != null && hash.equalsIgnoreCase(songmd5)) {
                                    int remotePlaycount = Integer.parseInt(getTagValue("playcount", eElement));
                                    int remoteClearcount = 0;
                                    NodeList ccList = eElement.getElementsByTagName("clearcount");
                                    if (ccList != null && ccList.getLength() > 0) {
                                        remoteClearcount = Integer.parseInt(ccList.item(0).getTextContent());
                                    }
                                    playcount = Math.max(remotePlaycount + 1, score.playcount);
                                    clearcount = Math.max(remoteClearcount + (lr2Clear >= 2 ? 1 : 0), score.clearcount);
                                    System.out.println("[BMS-IR] Found remote score for " + songmd5 + ". Remote Playcount: " + remotePlaycount + ", Remote Clearcount: " + remoteClearcount + ". Final playcount: " + playcount + ", clearcount: " + clearcount);
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[BMS-IR] Failed to fetch remote stats: " + e.getMessage());
            }

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
                + "&playcount=" + playcount
                + "&clearcount=" + clearcount
                + "&rate=" + rate
                + "&minbp=" + score.minbp
                + "&totalnotes=" + chart.notes
                + "&opt_history=" + op_best
                + "&opt_this=" + op_best
                + "&line=" + lr2Keymode
                + "&judge=" + (chart.judge >= 0 && chart.judge <= 3 ? chart.judge : 2)
                + "&inputtype=0"
                + "&ghost=" + URLEncoder.encode(encodePlayGhost(score.decodeGhost()), StandardCharsets.UTF_8)
                + "&rseed=" + (int)seedVal
                + "&clear_db=0"
                + "&clear_ex=0"
                + "&clear_sd=0"
                + "&scorehash=" + scorehash;

            param += "&clear_type=" + ((score.clear != null) ? score.clear.id : 0)
                + "&gauge_type=" + score.gauge
                + "&gauge_option=" + lr2Gauge;

            String spoofMode = getSpoofMode();
            if ("ed".equals(spoofMode) || "vanilla".equals(spoofMode)) {
                param += "&client_kind=lr2oraja"
                    + "&client_variant=" + spoofMode
                    + "&client_version=0.0.21"
                    + "&client_hash=" + md5HexForCodeSource("bms.player.beatoraja.MainController")
                    + "&client_hash_algorithm=md5"
                    + "&plugin_hash=" + md5HexForCodeSource("bms.player.beatoraja.ir.LR2IRConnectionCustom")
                    + "&plugin_hash_algorithm=md5";
            }

            System.out.println("[BMS-IR] Parameters prepared. Submitting to score.cgi...");

            URL url = new URL("https://www.bms-ir.org/~lavalse/LR2IR/2/score.cgi");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("User-Agent", getUserAgent());
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

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
                URL url = new URL("https://www.bms-ir.org/~lavalse/LR2IR/2/getplayerxml.cgi");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("User-Agent", getUserAgent());
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);

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
                                if (pg == notes && notes > 0) {
                                    beatorajaClear = 10; // Max Clear (All PG)
                                } else if (pg + gr == notes && notes > 0 && minbp == 0) {
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
            URL url = new URL("https://www.bms-ir.org/~lavalse/LR2IR/2/getrankingxml.cgi");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("User-Agent", getUserAgent());
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

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
                            if (pg == notes && notes > 0) {
                                beatorajaClear = 10; // Max Clear (All PG)
                            } else if (pg + gr == notes && notes > 0 && minbp == 0) {
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
        if (account == null || account.id == null || account.id.isEmpty()) {
            return createResponse(false, "Not logged in.", new IRPlayerData[0]);
        }
        System.out.println("[BMS-IR] Fetching rivals list for player ID: " + account.id);
        try {
            URL url = new URL("https://www.bms-ir.org/~lavalse/LR2IR/search.cgi?mode=mypage&playerid=" + account.id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", getUserAgent());
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return createResponse(false, "Failed to connect to LR2IR server (code: " + responseCode + ")", new IRPlayerData[0]);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "MS932"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }

            String html = response.toString();
            java.util.regex.Pattern rowPattern = java.util.regex.Pattern.compile("<tr><th>\\u30e9\\u30a4\\u30d0\\u30eb</th><td>([\\\\s\\\\S]*?)</td></tr>");
            java.util.regex.Matcher rowMatcher = rowPattern.matcher(html);
            java.util.ArrayList<IRPlayerData> rivals = new java.util.ArrayList<>();

            if (rowMatcher.find()) {
                String cellContent = rowMatcher.group(1);
                java.util.regex.Pattern entryPattern = java.util.regex.Pattern.compile("<a\\\\s+href=\\\"[^\\\"]*playerid=(\\\\d+)\\\"[^>]*>([^<]+)</a>", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher entryMatcher = entryPattern.matcher(cellContent);
                while (entryMatcher.find()) {
                    String rivalId = entryMatcher.group(1);
                    String rivalName = entryMatcher.group(2).trim();
                    System.out.println("[BMS-IR] Found rival: " + rivalName + " (ID: " + rivalId + ")");
                    rivals.add(new IRPlayerData(rivalId, rivalName, ""));
                }
            }

            IRPlayerData[] rivalsArr = rivals.toArray(new IRPlayerData[0]);
            System.out.println("[BMS-IR] Total rivals loaded: " + rivalsArr.length);
            return createResponse(true, "Fetched rivals successfully.", rivalsArr);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[BMS-IR] Error fetching rivals list: " + e.getMessage());
            return createResponse(false, "Exception occurred: " + e.getMessage(), new IRPlayerData[0]);
        }
    }

    private static final java.util.Map<Integer, java.util.Map<String, Integer>> LR2_RANDOM_SEED_BY_LANE_COUNT = buildPrecomputedLR2RandomSeedMaps();
    private static final java.util.Map<String, Integer> LR2_RANDOM_9KEY_SEED_CACHE = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
    private static volatile boolean LR2_RANDOM_9KEY_PREWARM_STARTED = false;

    private static String encodeLR2LanePattern(int n, int n2) {
        int n3;
        int n4;
        LR2Random lR2Random = new LR2Random(n);
        int[] nArray = new int[n2 + 1];
        int[] nArray2 = new int[n2 + 1];
        for (n4 = 0; n4 <= n2; ++n4) {
            nArray[n4] = n4;
        }
        for (n4 = 1; n4 < n2; ++n4) {
            n3 = n4 + lR2Random.nextInt(n2 - n4 + 1);
            int n5 = nArray[n4];
            nArray[n4] = nArray[n3];
            nArray[n3] = n5;
        }
        for (n4 = 1; n4 <= n2; ++n4) {
            nArray2[nArray[n4]] = n4;
        }
        StringBuilder stringBuilder = new StringBuilder(n2);
        for (n3 = 1; n3 <= n2; ++n3) {
            stringBuilder.append((char)(48 + nArray2[n3]));
        }
        return stringBuilder.toString();
    }

    private void schedulePopn9kCachePrewarm() {
        synchronized (LR2IRConnectionCustom.class) {
            if (LR2_RANDOM_9KEY_PREWARM_STARTED) {
                return;
            }
            LR2_RANDOM_9KEY_PREWARM_STARTED = true;
        }
        Thread thread = new Thread(() -> {
            int n2 = LR2_RANDOM_9KEY_SEED_CACHE.size();
            long l = System.currentTimeMillis();
            try {
                for (int n = 0; n < 1000000; ++n) {
                    String string = LR2IRConnectionCustom.encodeLR2LanePattern(n, 9);
                    LR2_RANDOM_9KEY_SEED_CACHE.putIfAbsent(string, n);
                }
            } catch (Throwable throwable) {
                // empty catch block
            }
            int n = LR2_RANDOM_9KEY_SEED_CACHE.size();
            long l2 = System.currentTimeMillis() - l;
            System.out.println("[BMS-IR] popn9k-prewarm-done: iterations=1000000 cachedBefore=" + n2 + " cachedAfter=" + n + " elapsedMs=" + l2);
        }, "BmsIRPopn9KeyPrewarm");
        thread.setDaemon(true);
        try {
            thread.setPriority(1);
        } catch (Exception exception) {
            // empty catch block
        }
        try {
            thread.start();
        } catch (Throwable throwable) {
            synchronized (LR2IRConnectionCustom.class) {
                LR2_RANDOM_9KEY_PREWARM_STARTED = false;
            }
        }
    }

    private long resolveUploadSeed(Mode mode, int n, long l) {
        if (mode == null || n < 0) {
            return 0L;
        }
        bms.player.beatoraja.pattern.Random random = this.normalizeLR2Random(bms.player.beatoraja.pattern.Random.getRandom((int)(n % 10), (Mode)mode));
        if (random == null) {
            return 0L;
        }
        switch (random) {
            case RANDOM: 
            case ROTATE: {
                String string = this.resolveComparableLanePattern(mode, n, l);
                if (string == null) {
                    return 0L;
                }
                Integer n2 = this.resolveComparableLR2Seed(mode, string);
                return n2 != null ? (long)n2.intValue() : 0L;
            }
        }
        return 0L;
    }

    private Integer resolveComparableLR2Seed(Mode mode, String string) {
        int n = this.resolveComparableLaneCount(mode);
        if (n <= 1 || string == null || string.isEmpty()) {
            return null;
        }
        java.util.Map<String, Integer> map = LR2_RANDOM_SEED_BY_LANE_COUNT.get(n);
        if (map != null) {
            return map.get(string);
        }
        if (n != 9) {
            return null;
        }
        Integer n2 = LR2_RANDOM_9KEY_SEED_CACHE.get(string);
        if (n2 != null) {
            return n2;
        }
        Integer n3 = LR2IRConnectionCustom.findLR2RandomSeedByLanePattern(n, string, 6000000);
        if (n3 != null) {
            LR2_RANDOM_9KEY_SEED_CACHE.putIfAbsent(string, n3);
        }
        return n3;
    }

    private String resolveComparableLanePattern(Mode mode, int n, long l) {
        int n2 = this.resolveComparableLaneCount(mode);
        if (n2 <= 1) {
            return null;
        }
        bms.player.beatoraja.pattern.Random random = this.normalizeLR2Random(bms.player.beatoraja.pattern.Random.getRandom((int)(n % 10), (Mode)mode));
        if (random == null) {
            return null;
        }
        switch (random) {
            case RANDOM: {
                return this.encodeComparableRandomLanePattern(l, n2);
            }
            case ROTATE: {
                return this.encodeComparableRotateLanePattern(l, n2);
            }
        }
        return null;
    }

    private int resolveComparableLaneCount(Mode mode) {
        if (mode == null || mode.player <= 0) {
            return 0;
        }
        int n = mode.key / mode.player;
        if (n <= 0) {
            return 0;
        }
        return this.hasScratchLane(mode) ? n - 1 : n;
    }

    private boolean hasScratchLane(Mode mode) {
        return mode != null && mode.scratchKey != null && mode.scratchKey.length > 0;
    }

    private String encodeComparableRandomLanePattern(long l, int n) {
        java.util.Random random = new java.util.Random(l);
        java.util.ArrayList<Integer> arrayList = new java.util.ArrayList<Integer>(n);
        for (int i = 0; i < n; ++i) {
            arrayList.add(i);
        }
        int[] nArray = new int[n];
        for (int i = 0; i < n; ++i) {
            int n2 = random.nextInt(arrayList.size());
            nArray[i] = (Integer)arrayList.remove(n2);
        }
        return this.encodeComparableLanePattern(nArray);
    }

    private String encodeComparableRotateLanePattern(long l, int n) {
        if (n <= 1) {
            return null;
        }
        java.util.Random random = new java.util.Random(l);
        boolean bl = random.nextInt(2) == 1;
        int n2 = random.nextInt(n - 1) + (bl ? 1 : 0);
        int[] nArray = new int[n];
        int n3 = n2;
        for (int i = 0; i < n; ++i) {
            nArray[i] = n3;
            n3 = bl ? (n3 + 1) % n : (n3 + n - 1) % n;
        }
        return this.encodeComparableLanePattern(nArray);
    }

    private String encodeComparableLanePattern(int[] nArray) {
        if (nArray == null || nArray.length == 0) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(nArray.length);
        for (int n : nArray) {
            stringBuilder.append((char)(48 + n + 1));
        }
        return stringBuilder.toString();
    }

    private static java.util.Map<Integer, java.util.Map<String, Integer>> buildPrecomputedLR2RandomSeedMaps() {
        java.util.Map<Integer, java.util.Map<String, Integer>> map = new java.util.LinkedHashMap<>();
        map.put(5, LR2IRConnectionCustom.buildLR2RandomSeedByLanePattern(5, 10000));
        map.put(7, LR2IRConnectionCustom.buildLR2RandomSeedByLanePattern(7, 60000));
        return map;
    }

    private static java.util.Map<String, Integer> buildLR2RandomSeedByLanePattern(int n, int n2) {
        int n3 = 1;
        for (int i = 2; i <= n; ++i) {
            n3 *= i;
        }
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n2 && map.size() < n3; ++i) {
            map.putIfAbsent(LR2IRConnectionCustom.encodeLR2LanePattern(i, n), i);
        }
        return map;
    }

    private static Integer findLR2RandomSeedByLanePattern(int n, String string, int n2) {
        for (int i = 0; i < n2; ++i) {
            if (!LR2IRConnectionCustom.encodeLR2LanePattern(i, n).equals(string)) continue;
            return i;
        }
        return null;
    }

    private bms.player.beatoraja.pattern.Random normalizeLR2Random(bms.player.beatoraja.pattern.Random random) {
        if (random == null) {
            return null;
        }
        switch (random) {
            case IDENTITY: 
            case MIRROR: 
            case RANDOM: 
            case ROTATE: 
            case S_RANDOM: {
                return random;
            }
            case MIRROR_EX: {
                return bms.player.beatoraja.pattern.Random.MIRROR;
            }
            case RANDOM_EX: 
            case RANDOM_PLAYABLE: {
                return bms.player.beatoraja.pattern.Random.RANDOM;
            }
            case ROTATE_EX: {
                return bms.player.beatoraja.pattern.Random.ROTATE;
            }
            case S_RANDOM_EX: 
            case S_RANDOM_PLAYABLE: 
            case S_RANDOM_NO_THRESHOLD: {
                return bms.player.beatoraja.pattern.Random.S_RANDOM;
            }
        }
        return null;
    }

    @Override
    public IRResponse<IRTableData[]> getTableDatas() {
        return createResponse(true, "Table data not supported.", new IRTableData[0]);
    }

    @Override
    public String getSongURL(IRChartData chart) {
        if (chart.md5 != null) {
            return "https://www.bms-ir.org/~lavalse/LR2IR/search.cgi?mode=ranking&bmsmd5=" + chart.md5.toLowerCase();
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
            return "https://www.bms-ir.org/~lavalse/LR2IR/search.cgi?mode=player&playerid=" + irpd.id;
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

    private static File getJarDir() {
        try {
            java.security.CodeSource codeSource = LR2IRConnectionCustom.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                File jarFile = new File(codeSource.getLocation().toURI());
                return jarFile.getParentFile();
            }
        } catch (Exception e) {
            // fallback
        }
        return new File(".");
    }

    private static void ensureSpoofConfigTemplate() {
        File dir = getJarDir();
        File configFile = new File(dir, "bmsir-spoof.txt");
        if (!configFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                String template = "# BMS-IR Client Spoofing Configuration\n"
                        + "# Choose one of the following modes:\n"
                        + "# - lr2 : Spoof as classic LR2 (highly compatible, does not submit modern gauge details, hides beatoraja client info)\n"
                        + "# - ed : Spoof as Endless Dream lr2oraja (User-Agent: BmsIRUpload/100130, submits gauge and variant hashes)\n"
                        + "# - vanilla : Spoof as vanilla lr2oraja (User-Agent: BmsIRUpload/100130, submits gauge and variant hashes)\n"
                        + "mode=lr2\n";
                fos.write(template.getBytes(StandardCharsets.UTF_8));
                System.out.println("[BMS-IR] Created default configuration file: " + configFile.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[BMS-IR] Failed to create configuration file: " + e.getMessage());
            }
        }
    }

    private static String getSpoofMode() {
        File dir = getJarDir();
        File configFile = new File(dir, "bmsir-spoof.txt");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("mode=")) {
                        String value = line.substring(5).trim().toLowerCase();
                        if (value.equals("ed") || value.equals("vanilla") || value.equals("lr2")) {
                            return value;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[BMS-IR] Failed to read configuration file: " + e.getMessage());
            }
        }
        return "lr2"; // Default fallback
    }

    private static String getUserAgent() {
        String mode = getSpoofMode();
        if ("ed".equals(mode) || "vanilla".equals(mode)) {
            return "BmsIRUpload/100130";
        }
        return "LR2";
    }

    private static String md5HexForCodeSource(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, LR2IRConnectionCustom.class.getClassLoader());
            java.security.CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return "";
            }
            java.nio.file.Path path = java.nio.file.Path.of(codeSource.getLocation().toURI());
            if (!java.nio.file.Files.isRegularFile(path)) {
                return "";
            }
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            try (java.io.InputStream inputStream = java.nio.file.Files.newInputStream(path)) {
                int n;
                byte[] byArray = new byte[8192];
                while ((n = inputStream.read(byArray)) >= 0) {
                    if (n > 0) {
                        messageDigest.update(byArray, 0, n);
                    }
                }
            }
            byte[] digest = messageDigest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable throwable) {
            return "";
        }
    }

    public static String encodePlayGhost(int[] judgements) {
        if (judgements == null || judgements.length == 0) {
            return "Z";
        }
        char[] map = {'E', 'D', 'C', 'B', 'A'};
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < judgements.length) {
            int judge = judgements[i];
            char c = (judge >= 0 && judge < 4) ? map[judge] : 'A';
            int count = 1;
            while (i + count < judgements.length) {
                int nextJudge = judgements[i + count];
                char nextC = (nextJudge >= 0 && nextJudge < 4) ? map[nextJudge] : 'A';
                if (nextC == c) {
                    count++;
                } else {
                    break;
                }
            }
            sb.append(c);
            if (count > 1) {
                sb.append(count);
            }
            i += count;
        }
        return sb.toString();
    }
}
