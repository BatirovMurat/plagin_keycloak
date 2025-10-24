package org.example;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.util.encoders.Base64;

import java.text.SimpleDateFormat;
import java.util.*;

public class HackimovSignParser {
    private static final String OID_PERSON_FULL_NAME = "2.5.4.3";
    private static final String OID_PERSON_NAME = "2.5.4.41";
    private static final String OID_PERSON_SURNAME = "2.5.4.4";
    private static final String OID_COMPANY_NAME = "2.5.4.10";
    private static final String OID_PERSON_POSITION = "2.5.4.12";
    private static final String OID_COMPANY_TIN = "1.2.860.3.16.1.1";
    private static final String OID_PERSON_TIN = "0.9.2342.19200300.100.1.1";
    private static final String OID_PERSON_PIN = "1.2.860.3.16.1.2";

    public static Map<String, String> parseSignature(String base64Signature) {
        try {
            byte[] decodedSignature = Base64.decode(base64Signature);
            CMSSignedData signedData = new CMSSignedData(decodedSignature);
            SignerInformationStore signers = signedData.getSignerInfos();
            Collection<SignerInformation> signerCollection = signers.getSigners();
            Collection<X509CertificateHolder> certs = signedData.getCertificates().getMatches(null);

            SignerInformation lastSigner = null;
            X509CertificateHolder lastCertHolder = null;

            for (SignerInformation signer : signerCollection) {
                for (X509CertificateHolder certHolder : certs) {
                    if (signer.getSID().match(certHolder)) {
                        lastSigner = signer;
                        lastCertHolder = certHolder;
                    }
                }
            }

            if (lastSigner != null && lastCertHolder != null) {
                Map<String, String> signerInfo = extractSignerInfo(lastCertHolder);
                signerInfo.put("signed_at", getSigningTime(lastSigner));
                return signerInfo;
            } else {
                return Collections.singletonMap("error", "Подписант не найден");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.singletonMap("error", "Ошибка обработки подписи");
        }
    }

    private static Map<String, String> extractSignerInfo(X509CertificateHolder certHolder) {
        Map<String, String> info = new HashMap<>();
        X500Name subject = certHolder.getSubject();

        info.put("person_full_name", getRDNValue(subject, OID_PERSON_FULL_NAME));
        info.put("person_name", getRDNValue(subject, OID_PERSON_NAME));
        info.put("person_surname", getRDNValue(subject, OID_PERSON_SURNAME));
        info.put("person_position", getRDNValue(subject, OID_PERSON_POSITION));
        info.put("company_name", getRDNValue(subject, OID_COMPANY_NAME));
        info.put("company_tin", getRDNValue(subject, OID_COMPANY_TIN));
        info.put("person_tin", getRDNValue(subject, OID_PERSON_TIN));
        info.put("person_pin", getRDNValue(subject, OID_PERSON_PIN));

        return info;
    }

    private static String getRDNValue(X500Name x500Name, String oid) {
        RDN[] rdns = x500Name.getRDNs(new ASN1ObjectIdentifier(oid));
        if (rdns.length > 0) {
            return rdns[0].getFirst().getValue().toString();
        }
        return "";
    }

    private static String getSigningTime(SignerInformation signer) {
        AttributeTable signedAttrs = signer.getSignedAttributes();
        if (signedAttrs == null) {
            return "Дата подписания отсутствует";
        }

        Attribute signingTimeAttr = signedAttrs.get(PKCSObjectIdentifiers.pkcs_9_at_signingTime);
        if (signingTimeAttr == null) {
            return "Дата подписания отсутствует";
        }

        try {
            ASN1Encodable attrValue = signingTimeAttr.getAttrValues().getObjectAt(0);
            if (attrValue instanceof ASN1UTCTime) {
                return ((ASN1UTCTime) attrValue).getAdjustedTime();
            } else if (attrValue instanceof ASN1GeneralizedTime) {
                return ((ASN1GeneralizedTime) attrValue).getTimeString();
            } else {
                return "Неизвестный формат даты";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка получения даты";
        }
    }

    private static String formatDate(String dateStr) {
        try {
            SimpleDateFormat originalFormat = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
            originalFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            SimpleDateFormat targetFormat = new SimpleDateFormat("yyyyMMddHHmmss'GMT+00:00'");
            targetFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date date = originalFormat.parse(dateStr);
            return targetFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка даты";
        }
    }
}

