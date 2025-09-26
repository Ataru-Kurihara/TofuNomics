package org.tofu.tofunomics.models;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class JobChange {
    
    private String uuid;
    private String lastChangeDate;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    public JobChange() {}
    
    public JobChange(String uuid, String lastChangeDate) {
        this.uuid = uuid;
        this.lastChangeDate = lastChangeDate;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getLastChangeDate() {
        return lastChangeDate;
    }
    
    public void setLastChangeDate(String lastChangeDate) {
        this.lastChangeDate = lastChangeDate;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean canChangeJobToday() {
        if (lastChangeDate == null) {
            return true;
        }
        
        try {
            LocalDate lastChange = LocalDate.parse(lastChangeDate, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate today = LocalDate.now();
            return !lastChange.isEqual(today);
        } catch (Exception e) {
            return true;
        }
    }
    
    public void updateToToday() {
        this.lastChangeDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
    
    public static String getTodayDateString() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
    
    @Override
    public String toString() {
        return "JobChange{" +
                "uuid='" + uuid + '\'' +
                ", lastChangeDate='" + lastChangeDate + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}