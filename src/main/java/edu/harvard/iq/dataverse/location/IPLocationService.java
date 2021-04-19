package edu.harvard.iq.dataverse.location;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import edu.harvard.iq.dataverse.location.GeoIP;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

public class IPLocationService {
    static String dbLocation = "/usr/local/payara5/glassfish/domains/domain1/GeoLite2-City.mmdb";
    static DatabaseReader dbReader = null;

    public static GeoIP getLocation(String ip) {
        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = dbReader.city(ipAddress);
            GeoIP geoIP = new GeoIP(ip);
            geoIP.setCountryName(response.getCountry().getName());
            geoIP.setCountryCode(response.getCountry().getIsoCode());
            geoIP.setContinentCode(response.getContinent().getCode());
            geoIP.setContinentName(response.getContinent().getName());
            geoIP.setCityName(response.getLeastSpecificSubdivision().getName());
            geoIP.setLatitude(response.getLocation().getLatitude().toString());
            geoIP.setLongitude(response.getLocation().getLongitude().toString());
            return geoIP;
        }
        catch (GeoIp2Exception | IOException e) {
            return null;
        }
    }

    static {
        try {
            File database = new File(dbLocation);
            dbReader = new DatabaseReader.Builder(database).build();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}