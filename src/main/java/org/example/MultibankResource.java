package org.example;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.example.config.RealmConfigResolver;
import org.example.eimzo.EIMZOServerService;
import org.example.logger.TelegramLogger;
import org.example.sms.SmsService;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.*;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.services.util.DefaultClientSessionContext;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;

import java.util.*;

@Path("/")
public class MultibankResource {
    private final KeycloakSession session;
    private String clientId;
    private String clientSecret;
    
    @Context
    private UriInfo uriInfo;
    
    @Context
    private HttpHeaders headers;
    // TTL для OTP в секундах (подставь свой)
    private static final int OTP_TTL_SECONDS = 300;

    // Форматер c 6 знаками после секунды и 'Z'
    private static final java.time.format.DateTimeFormatter INSTANT_6 =
            new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
    public MultibankResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("signature-auth")
    @Consumes(MediaType.APPLICATION_JSON)  // Указываем, что принимаем JSON
    @Produces(MediaType.APPLICATION_JSON)
    public Response signature(Map<String, String> requestBody) {
        try {
            RealmModel realm = session.getContext().getRealm();
            String signature = requestBody.get("signature");

            String clientId = requestBody.get("client_id");
            String clientSecret = requestBody.get("client_secret");

            if(clientId == null || clientSecret == null) {
                ClientModel client = realm.getClientByClientId(clientId);
                if (client == null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("client is not found").build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity("client id is required").build();
            }

            this.clientId = clientId;
            this.clientSecret = clientSecret;

            if(signature == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity("signature key is empty").build();
            }

            EIMZOServerService eimzoServerService = new EIMZOServerService();
            boolean isValid =  eimzoServerService.pkcs7VerifyAttached(signature);

            if (!isValid) {
                return Response.status(Response.Status.BAD_REQUEST).entity("signature is not valid").build();
            }

            Map<String, String> result = new java.util.HashMap<>(HackimovSignParser.parseSignature(signature));
            TelegramLogger.sendMessage(result.toString());
            System.out.println("Signature: " + result);

            // Нормализуем данные перед поиском
            String firstName = normalizeString(result.get("person_name"));
            String lastName = normalizeString(result.get("person_surname"));
            String personalId = result.get("person_pin");
            String companyTin = result.get("company_tin");


            // Сначала ищем по уникальному идентификатору (ИНН), если есть
            UserModel user = personalId != null
                    ? session.users().searchForUserByUserAttributeStream(realm, "personPin", personalId)
                    .findFirst()
                    .orElse(null)
                    : null;

            // Если не нашли по ИНН, ищем по имени и фамилии
            if (user == null) {
                System.out.println("************* SEARCH BY NAME ****************");
                TelegramLogger.sendMessage("************* SEARCH BY NAME ****************");
                user = session.users().searchForUserStream(realm, firstName + " " + lastName, 0, 1)
                        .filter(u ->
                                firstName.equalsIgnoreCase(normalizeString(u.getFirstName())) &&
                                        lastName.equalsIgnoreCase(normalizeString(u.getLastName()))
                        )
                        .findFirst()
                        .orElse(null);
            } else {
                System.out.println("************* USER FOUND ****************");
                TelegramLogger.sendMessage("************* USER FOUND ****************");
            }

            // Если пользователь не найден - создаём нового
            if (user == null) {
                System.out.println("************* CREATE USER ****************");
                TelegramLogger.sendMessage("************* CREATE USER ****************");
                String username = generateUsername(firstName, lastName);
                user = session.users().addUser(realm, username);
                user.setFirstName(firstName);
                user.setLastName(lastName);

                List<String> personPins = new ArrayList<>();
                personPins.add(result.get("person_pin"));
                user.setAttribute("personPin", personPins);
                user.setEnabled(true);
            }

            TelegramLogger.sendMessage("************* ISSUE TOKEN ****************");
            System.out.println("************* ISSUE TOKEN ****************");
            AccessTokenResponse tokenResponse = issueTokens(user);
            tokenResponse.setOtherClaims("company_tin", companyTin);
            tokenResponse.setOtherClaims("person_pin", personalId);
            TelegramLogger.sendMessage("************* TOKEN GENERATED ****************");
            System.out.println("************* TOKEN GENERATED ****************");
            return Response.ok(tokenResponse).build();
        } catch (Exception e) {
            TelegramLogger.sendMessage(e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @POST
    @Path("otp-phone-auth")
    @Consumes(MediaType.APPLICATION_JSON)  // Указываем, что принимаем JSON
    @Produces(MediaType.APPLICATION_JSON)
    public Response otp(Map<String, String> requestBody) {
        try {
            String userPhone = requestBody.get("phone");
            RealmModel realm = session.getContext().getRealm();

            String clientId = requestBody.get("client_id");
            String clientSecret = requestBody.get("client_secret");

            if(clientId == null || clientSecret == null) {
                ClientModel client = realm.getClientByClientId(clientId);
                if (client == null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("client is not found").build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity("client id is required").build();
            }

            this.clientId = clientId;
            this.clientSecret = clientSecret;

            if(userPhone == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity("phone key is empty").build();
            }


            this.clientId = clientId;
            this.clientSecret = clientSecret;

            UserModel user = userPhone != null
                    ? session.users().searchForUserByUserAttributeStream(realm, "phone", userPhone)
                    .findFirst()
                    .orElse(null)
                    : null;

            if(user == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            Map<String, String> creds = RealmConfigResolver.resolvePayload(session);
            SmsService smsService = new SmsService(creds);
            OtpService service = new OtpService(smsService);
            Optional<String> otp = service.sendOtp(session, user.getId(), userPhone, "");

            if(otp.isEmpty()) {
                return Response.status(Response.Status.CONFLICT).build();
            }

            System.out.println("************* SEND OTP (" + otp +") ****************");
            Map<String, Object> response = new HashMap<>();
            response.put("phone", userPhone);
            response.put("sms_id", otp);
            response.put("message", "Код OTP отправлен.");
            return Response.ok(response).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
    @POST
    @Path("send-otp")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendOtp(Map<String, String> requestBody) {
        try {
            final String userPhone   = requestBody.get("phone");
            final String clientId    = requestBody.get("client_id");
            final String clientSecret = requestBody.get("client_secret");

            if (isBlank(userPhone)) {
                return jsonError("phone key is empty", Response.Status.BAD_REQUEST);
            }
            if (isBlank(clientId)) {
                return jsonError("client id is required", Response.Status.BAD_REQUEST);
            }
            if (isBlank(clientSecret)) {
                return jsonError("client secret is required", Response.Status.BAD_REQUEST);
            }

            RealmModel realm = session.getContext().getRealm();
            ClientModel client = realm.getClientByClientId(clientId);
            if (client == null) {
                return jsonError("client is not found", Response.Status.BAD_REQUEST);
            }
            // при желании можно сверить секрет:
            // String kcSecret = session.clientCredentialManager().getStoredCredential(...);
            // (опустим для краткости)

            // Находим пользователя по телефону (если есть)
            UserModel user = session.users()
                    .searchForUserByUserAttributeStream(realm, "phone", userPhone)
                    .findFirst()
                    .orElse(null);

            // Отправляем OTP
            Map<String, String> creds = RealmConfigResolver.resolvePayload(session);
            SmsService smsService = new SmsService(creds);
            OtpService service = new OtpService(smsService);
            Optional<String> smsId = service.sendDirectOtp(
                    session,
                    (user != null ? user.getId() : null), // допускаем null
                    userPhone,
                    "" // ip если нужно
            );

            if (smsId.isEmpty()) {
                // Не удалось отправить
                return jsonFail("Не удалось отправить OTP", Response.Status.CONFLICT);
            }

            // Собираем ответ
            String expiresAt = INSTANT_6.format(Instant.now().plusSeconds(OTP_TTL_SECONDS));
            String maskedPhone = maskPhoneKeep3_3(userPhone);

            Map<String, Object> data = new HashMap<>();
            data.put("expires_at", expiresAt);
            data.put("phone", maskedPhone);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "Код OTP отправлен.");
            resp.put("data", data);
            resp.put("success", true);

            return Response.ok(resp).build();

        } catch (Exception e) {
            e.printStackTrace();
            return jsonFail("Unauthorized", Response.Status.UNAUTHORIZED);
        }
    }

    @POST
    @Path("otp-auth")
    @Consumes(MediaType.APPLICATION_JSON)  // Указываем, что принимаем JSON
    @Produces(MediaType.APPLICATION_JSON)
    public Response otpAuth(Map<String, String> requestBody) {
        try {
            String userPhone = requestBody.get("phone");
            String otpCode = requestBody.get("otp");

            RealmModel realm = session.getContext().getRealm();

            String clientId = requestBody.get("client_id");
            String clientSecret = requestBody.get("client_secret");

            if(clientId == null || clientSecret == null) {
                ClientModel client = realm.getClientByClientId(clientId);
                if (client == null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("client is not found").build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity("client id is required").build();
            }

            this.clientId = clientId;
            this.clientSecret = clientSecret;

            if(userPhone == null || otpCode == null || userPhone.isEmpty() || otpCode.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("phone and otp is required").build();
            }

            UserModel user = userPhone != null
                    ? session.users().searchForUserByUserAttributeStream(realm, "phone", userPhone)
                    .findFirst()
                    .orElse(null)
                    : null;

            if(user == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            Map<String, String> creds = RealmConfigResolver.resolvePayload(session);
            SmsService smsService = new SmsService(creds);
            OtpService otpService = new OtpService(smsService);
            boolean result = otpService.verifyOtp(session, user.getId(), otpCode);

            if (!result) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid or expired code"))
                        .build();
            }


            TelegramLogger.sendMessage("************* ISSUE TOKEN ****************");
            AccessTokenResponse tokenResponse = issueTokens(user);
            TelegramLogger.sendMessage("************* TOKEN GENERATED ****************");
            return Response.ok(tokenResponse).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
    @POST
    @Path("/token-via-otp")
    @Consumes(MediaType.APPLICATION_JSON)  // Указываем, что принимаем JSON
    @Produces(MediaType.APPLICATION_JSON)
    public Response otpAuthToken(Map<String, String> requestBody) {
        try {
            String userPhone = requestBody.get("phone");
            String otpCode = requestBody.get("otp");

            RealmModel realm = session.getContext().getRealm();

            String clientId = requestBody.get("client_id");
            String clientSecret = requestBody.get("client_secret");

            if(clientId == null || clientSecret == null) {
                ClientModel client = realm.getClientByClientId(clientId);
                if (client == null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("client is not found").build();
                }
                return Response.status(Response.Status.BAD_REQUEST).entity("client id is required").build();
            }

            this.clientId = clientId;
            this.clientSecret = clientSecret;

            if(userPhone == null || otpCode == null || userPhone.isEmpty() || otpCode.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("phone and otp is required").build();
            }

            // Ищем пользователя по телефону (может быть null, тогда это кейс №3 при валидном OTP)
            UserModel user = session.users()
                    .searchForUserByUserAttributeStream(realm, "phone", userPhone)
                    .findFirst()
                    .orElse(null);

            // Проверяем OTP. Если user == null, verifyDirectOtp должен уметь сам найти sms_id (или см. предыдущий мой ответ).
            Map<String, String> creds = RealmConfigResolver.resolvePayload(session);
            SmsService smsService = new SmsService(creds);
            OtpService otpService = new OtpService(smsService);
            boolean valid = otpService.verifyDirectOtp(session, (user != null ? user.getId() : null), otpCode);

            if (!valid) {
                // ===== case 1 ===== (invalid/expired)
                return oauthInvalidGrant();
            }

            if (user == null) {
                // ===== case 3 ===== (OTP валидный, но пользователя нет — отправляем на регистрацию)
                String registrationUrl = buildRegistrationUrl(
                        "https://auth-staging.multibank.uz/api/register-via-otp",
                        Map.of(
                                "phone", userPhone,
                                "otp", otpCode,
                                "client_id", clientId
                                // при желании добавь redirect_uri, state, scope
                        )
                );
                return registrationRequired(registrationUrl);
            }

            // ===== case 2 ===== (успех — выдаём токены)
            AccessTokenResponse tokenResponse = issueTokens(user);
            TelegramLogger.sendMessage("************* TOKEN GENERATED ****************");
            return Response.ok(tokenResponse).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    @OPTIONS
    @Path("{any:.*}")
    public Response preflight() {
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                .build();
    }

    private AccessTokenResponse issueTokens(UserModel user) throws Exception {
        RealmModel realm = session.getContext().getRealm();

        // 1. Получаем клиента (убедитесь, что clientId существует)
        ClientModel client = realm.getClientByClientId(clientId);
        if (client == null) {
            throw new Exception("Client not found");
        }

        // 2. Устанавливаем клиент в контекст
        session.getContext().setClient(client);

        // 3. Создаем пользовательскую сессию
        UserSessionModel userSession = session.sessions().createUserSession(
                UUID.randomUUID().toString(), // Явный ID сессии
                realm,
                user,
                user.getUsername(),
                session.getContext().getConnection().getRemoteAddr(),
                "signature-auth",
                false,
                null,
                null,
                UserSessionModel.SessionPersistenceState.PERSISTENT
        );

        // 4. Создаем клиентскую сессию
        AuthenticatedClientSessionModel clientSession = session.sessions().createClientSession(
                realm,
                client,
                userSession
        );
        
        // Устанавливаем действие аутентификации
        clientSession.setAction("signature-auth");

        // 5. Создаем ClientSessionContext
        Set<String> scopeParam = new HashSet<>(Arrays.asList("openid", "profile", "email"));
        ClientSessionContext clientSessionCtx = DefaultClientSessionContext.fromClientSessionAndScopeParameter(
                clientSession,
                scopeParam.toString(),
                session
        );

        // 6. Инициализируем TokenManager и EventBuilder с URI
        TokenManager tokenManager = new TokenManager();
        EventBuilder event = new EventBuilder(realm, session, session.getContext().getConnection());
        event.detail("auth_method", "signature-auth");

        // 7. Генерируем токены с правильным контекстом
        AccessTokenResponse response = tokenManager
                .responseBuilder(realm, client, event, session, userSession, clientSessionCtx)
                .generateAccessToken()
                .generateRefreshToken()
                .build();
        
        return response;
    }

    static String generateUsername(String firstName, String lastName) {
        if (firstName == null || lastName == null || firstName.isEmpty() || lastName.isEmpty()) {
            throw new IllegalArgumentException("Имя и фамилия не могут быть пустыми");
        }

        firstName = firstName.trim().toLowerCase();
        lastName = lastName.trim().toLowerCase();

        return lastName + "_" + firstName.charAt(0); // ivanov_p
    }

    private String normalizeString(String input) {
        return input != null
                ? input.trim().toUpperCase()
                : null;
    }
    // --- helpers ---

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Маскируем номер: оставляем первые 3 и последние 3 цифры. */
    private static String maskPhoneKeep3_3(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 6) {
            return "***";
        }
        String start = digits.substring(0, 3);
        String end = digits.substring(digits.length() - 3);
        return start + "******" + end;
    }

    private static Response jsonError(String message, Response.Status status) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", message);
        resp.put("data", null);
        resp.put("success", false);
        return Response.status(status).entity(resp).build();
    }

    private static Response jsonFail(String message, Response.Status status) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", message);
        resp.put("data", null);
        resp.put("success", false);
        return Response.status(status).entity(resp).build();
    }

    // case 1 — OAuth-ошибка invalid_grant (HTTP 400)
    private Response oauthInvalidGrant() {
        Map<String, Object> body = new HashMap<>();
        String desc =
                "The provided authorization grant (e.g., authorization code, resource owner credentials) or refresh token " +
                        "is invalid, expired, revoked, does not match the redirection URI used in the authorization request, or " +
                        "was issued to another client.";
        body.put("error", "invalid_grant");
        body.put("error_description", desc);
        body.put("hint", "");
        body.put("message", desc);
        return Response.status(Response.Status.BAD_REQUEST).entity(body).build(); // 400
    }

    // case 2 — успешная выдача токенов
    private Response tokenSuccess(AccessTokenResponse tr) {
        Map<String, Object> out = new HashMap<>();
        out.put("token_type", (tr.getTokenType() != null ? tr.getTokenType() : "Bearer"));
        out.put("expires_in", tr.getExpiresIn());          // обычно в секундах
        out.put("access_token", tr.getToken());
        out.put("refresh_token", tr.getRefreshToken());
        return Response.ok(out).build();
    }

    // case 3 — требуем регистрацию (HTTP 403)
    private Response registrationRequired(String url) {
        Map<String, Object> data = Map.of("registration_url", url);
        Map<String, Object> out = new HashMap<>();
        out.put("message", "Требуется пройти регистрацию.");
        out.put("data", data);
        out.put("success", false);
        return Response.status(Response.Status.FORBIDDEN).entity(out).build(); // 403
    }
    // Утилита для сборки URL с query-параметрами
    private String buildRegistrationUrl(String base, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(base);
        if (!base.contains("?")) sb.append('?');
        boolean first = !base.contains("?");
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(Objects.toString(e.getValue(), ""), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }
}