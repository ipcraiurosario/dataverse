package edu.harvard.iq.dataverse.location;

public class GeoIP {
    private String ip;
    private String continentName;
    private String continentCode;
    private String countryName;
    private String countryCode;
    private String cityName;
    private String latitude;
    private String longitude;

    public GeoIP(String ip) {
        this.ip = ip;
    }

    public GeoIP(String ip, String continentName, String continentCode, String countryName, String countryCode, String cityName) {
        this.ip = ip;
        this.continentName = continentName;
        this.continentCode = continentCode;
        this.countryName = countryName;
        this.countryCode = countryCode;
        this.cityName = cityName;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setContinentName(String continentName) {
        this.continentName = continentName;
    }

    public void setContinentCode(String continentCode) {
        this.continentCode = continentCode;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getIp() {
        return this.ip;
    }

    public String getContinentName() {
        return this.continentName;
    }

    public String getContinentCode() {
        return this.continentCode;
    }

    public String getCountryName() {
        return this.countryName;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public String getCityName() {
        return this.cityName;
    }

    public String getLatitude() {
        return this.latitude;
    }

    public String getLongitude() {
        return this.longitude;
    }

    public String toString() {
        return "GeoIP{ip='" + this.ip + '\'' + ", continentName='" + this.continentName + '\'' + ", continentCode='" + this.continentCode + '\'' + ", countryName='" + this.countryName + '\'' + ", countryCode='" + this.countryCode + '\'' + ", cityName='" + this.cityName + '\'' + ", latitude='" + this.latitude + '\'' + ", longitude='" + this.longitude + '\'' + '}';
    }
}