package edu.harvard.iq.dataverse.orcid;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Vivo {
    private static final Logger logger = Logger.getLogger(Vivo.class.getCanonicalName());
    private final String VIVO_URL = "http://research-hub.urosario.edu.co/";
    private final String VIVO_SEARCH_URL = "http://research-hub.urosario.edu.co/search?querytext=ORCID_ID";
    private final String searchPaternVivo = "h3.shortview_person-name";
    private final String searchPaternIdent = "div.shortview_person-social > a";

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private String getURL(String uri, Boolean isJson) {
        InputStreamReader reader = null;
        try {
            int read;
            URL url = new URL(uri);
            URLConnection uc = url.openConnection();
            if (isJson.booleanValue()) {
                uc.setRequestProperty("Accept", "application/json");
            }
            reader = new InputStreamReader(uc.getInputStream());
            StringBuffer buffer = new StringBuffer();
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1) {
                buffer.append(chars, 0, read);
            }
            String string = buffer.toString();
            return string;
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public Map<String, String> obtenDatos(String orcid) {
        LinkedHashMap<String, String> datos = new LinkedHashMap<String, String>();
        datos.put("orcid", "https://orcid.org/" + orcid);
        try {
            Document doc = Jsoup.parse((String)this.getURL("http://research-hub.urosario.edu.co/search?querytext=ORCID_ID".replace("ORCID_ID", orcid), false));
            Elements personas = doc.select("h3.shortview_person-name");
            if (personas.size() == 1) {
                Element persona = (Element)personas.get(0);
                Elements enlaces = persona.getElementsByAttributeValue("title", "name");
                for (Object enlace : enlaces) {
                    datos.put("vivo", "http://research-hub.urosario.edu.co/"
                            + ((Element)enlace).attr("href"));
                }
                Elements identificadores = doc.select("div.shortview_person-social > a");
                for (Element identificador : identificadores) {
                    String value = identificador.attr("href");
                    if (value.contains("google")) {
                        datos.put("google", value);
                    }
                    if (value.contains("elsevier")) {
                        datos.put("elsevier", value);
                    }
                    if (value.contains("scopus")) {
                        datos.put("scopus", value);
                    }
                    if (value.contains("scienti.colciencias")) {
                        datos.put("cvlac", value);
                    }
                    if (value.contains("plu.mx")) {
                        datos.put("plumx", value);
                    }
                    if (value.contains("researcherid")) {
                        datos.put("researcherid", value);
                    }
                    if (!value.contains("twitter")) continue;
                    datos.put("twitter", value);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return datos;
    }
}