package edu.harvard.iq.dataverse.location;

public class MetricResult {
    private String name;
    private long views;
    private long downloads;
    private String other;

    public String getOther() {
        return this.other;
    }

    public void setOther(String other) {
        this.other = other;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getViews() {
        return this.views;
    }

    public void setViews(long views) {
        this.views = views;
    }

    public long getDownloads() {
        return this.downloads;
    }

    public void setDownloads(long downloads) {
        this.downloads = downloads;
    }

    public MetricResult(String name, long views, long downloads) {
        this.name = name;
        this.views = views;
        this.downloads = downloads;
    }

    public MetricResult(String name, long views, long downloads, String other) {
        this.name = name;
        this.views = views;
        this.downloads = downloads;
        this.other = other;
    }

    public String toString() {
        return "Metric{name='" + this.name + '\'' + ", views=" + this.views + ", downloads=" + this.downloads + '}';
    }
}