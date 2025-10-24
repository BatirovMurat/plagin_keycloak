package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_otp")
public class OtpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "sms_id")
    private String smsId;

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setSmsId(String smsId) {
        this.smsId = smsId;
    }

    public String getSmsId() {
        return smsId;
    }
}
