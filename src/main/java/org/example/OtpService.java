package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import org.example.entity.OtpEntity;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.*;
import org.example.sms.SmsService;

import java.util.Map;
import java.util.Optional;

public class OtpService {

    private final SmsService smsService;

    public OtpService(SmsService smsService) {
        this.smsService = smsService;
    }

    public Optional<String> sendOtp(KeycloakSession session, String userId, String phone, String ip) {
        Map<String, Object> data = Map.of(
                "phone", phone,
                "message", "NIKOMU NE PEREDAVAYTE KOD! Kod: {otp}",
                "prefix", false,
                "ip", ip,
                "otp_length", 6,
                "nickname", "Multibank"
        );

        try {
            String response = smsService.sendSms(data);
            Optional<String> smsId = Optional.of(response);
            EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();

            OtpEntity otp = em.createQuery(
                            "SELECT o FROM OtpEntity o WHERE o.userId = :userId", OtpEntity.class)
                    .setParameter("userId", userId)
                    .getResultStream() // безопасный способ вместо try-catch
                    .findFirst()
                    .orElseGet(OtpEntity::new);

            otp.setUserId(userId);
            otp.setSmsId(smsId.orElse(null));
            em.persist(otp); // persist будет работать и на update, если объект managed

            return smsId;
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
    public Optional<String> sendDirectOtp(KeycloakSession session,
                                          @Nullable String userId,
                                          String phone,
                                          String ip) {
        Map<String, Object> data = Map.of(
                "phone", phone,
                "message", "NIKOMU NE PEREDAVAYTE KOD! Kod: {otp}",
                "prefix", false,
                "ip", ip,
                "otp_length", 6,
                "nickname", "Multibank"
        );

        try {
            String response = smsService.sendSms(data);
            Optional<String> smsId = Optional.ofNullable(response);

            EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();

            // Если userId == null, пропускаем поиск по нему (JPQL с null ничего не найдёт)
            OtpEntity otp = null;
            if (userId != null && !userId.isBlank()) {
                otp = em.createQuery(
                                "SELECT o FROM OtpEntity o WHERE o.userId = :userId",
                                OtpEntity.class)
                        .setParameter("userId", userId)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElse(null);
            }
            if (otp == null) {
                otp = new OtpEntity();
            }
            otp.setUserId(userId);                // допускаем null
            otp.setSmsId(smsId.orElse(null));     // безопасно, даже если ответа нет
            em.persist(otp); // persist будет работать и на update, если объект managed
            return smsId;
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public boolean verifyOtp(KeycloakSession session, String userId, String otpCode) {

        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();

        OtpEntity otp = em.createQuery(
                        "SELECT o FROM OtpEntity o WHERE o.userId = :userId",
                        OtpEntity.class
                )
                .setParameter("userId", userId)
                .getSingleResult();


        Map<String, Object> data = Map.of(
                "sms_id", otp.getSmsId(),
                "otp", otpCode
        );

        try {
            return smsService.checkOtp(data);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean verifyDirectOtp(KeycloakSession session,
                                   @Nullable String userId,
                                   String otpCode) {

        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();

        OtpEntity otp = null;

        if (userId != null) {
            // ищем по userId
            otp = em.createQuery(
                            "SELECT o FROM OtpEntity o WHERE o.userId = :userId",
                            OtpEntity.class
                    )
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        }

        if (otp == null) {
            // если userId пустой или не нашли — ищем по smsId
            otp = em.createQuery(
                            "SELECT o FROM OtpEntity o WHERE o.smsId IS NOT NULL ORDER BY o.id DESC",
                            OtpEntity.class
                    )
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        }

        if (otp == null) {
            return false; // ни по userId, ни по smsId не нашли
        }

        Map<String, Object> data = Map.of(
                "sms_id", otp.getSmsId(),
                "otp", otpCode
        );

        try {
            return smsService.checkOtp(data);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}

