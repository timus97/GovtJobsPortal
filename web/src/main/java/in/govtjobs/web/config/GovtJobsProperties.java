package in.govtjobs.web.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "govtjobs")
public class GovtJobsProperties {

    private final Ops ops = new Ops();
    private final Student student = new Student();
    private final Session session = new Session();
    private final Mail mail = new Mail();
    private final Catalog catalog = new Catalog();
    private final RateLimit rateLimit = new RateLimit();

    public Ops getOps() {
        return ops;
    }

    public Student getStudent() {
        return student;
    }

    public Session getSession() {
        return session;
    }

    public Mail getMail() {
        return mail;
    }

    public Catalog getCatalog() {
        return catalog;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Ops {
        private List<String> allowedSuffixes = new ArrayList<>(List.of(".gov.in", ".nic.in"));
        private List<String> extraHosts = new ArrayList<>(List.of(
                "ibps.in",
                "sbi.co.in",
                "sbi.bank.in",
                "bank.sbi",
                "recruitment.sbi.bank.in",
                "becil.com",
                "nta.ac.in",
                "rbi.org.in",
                "opportunities.rbi.org.in",
                "nabard.org",
                "licindia.in",
                "gate2027.iitm.ac.in",
                "aiimsexams.ac.in",
                "aiims.edu",
                "ongcindia.com"));
        private String userAgent = "NoExamSarkariBot/1.0";
        private int connectTimeoutSeconds = 15;
        private int requestTimeoutSeconds = 20;
        private int maxBodyBytes = 512 * 1024;

        public List<String> getAllowedSuffixes() {
            return allowedSuffixes;
        }

        public void setAllowedSuffixes(List<String> allowedSuffixes) {
            this.allowedSuffixes = allowedSuffixes;
        }

        public List<String> getExtraHosts() {
            return extraHosts;
        }

        public void setExtraHosts(List<String> extraHosts) {
            this.extraHosts = extraHosts;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public int getMaxBodyBytes() {
            return maxBodyBytes;
        }

        public void setMaxBodyBytes(int maxBodyBytes) {
            this.maxBodyBytes = maxBodyBytes;
        }
    }

    public static class Student {
        private int passwordMin = 10;
        private long maxFileBytes = 5L * 1024 * 1024;
        private String dataDir = "";
        private String filesDir = "";
        private boolean importJson = true;

        public int getPasswordMin() {
            return passwordMin;
        }

        public void setPasswordMin(int passwordMin) {
            this.passwordMin = passwordMin;
        }

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public String getDataDir() {
            return dataDir;
        }

        public void setDataDir(String dataDir) {
            this.dataDir = dataDir;
        }

        public String getFilesDir() {
            return filesDir;
        }

        public void setFilesDir(String filesDir) {
            this.filesDir = filesDir;
        }

        public boolean isImportJson() {
            return importJson;
        }

        public void setImportJson(boolean importJson) {
            this.importJson = importJson;
        }
    }

    public static class Session {
        private String secret = "";
        private int studentTtlDays = 14;
        private int opsTtlHours = 12;
        private boolean cookieSecure = false;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getStudentTtlDays() {
            return studentTtlDays;
        }

        public void setStudentTtlDays(int studentTtlDays) {
            this.studentTtlDays = studentTtlDays;
        }

        public int getOpsTtlHours() {
            return opsTtlHours;
        }

        public void setOpsTtlHours(int opsTtlHours) {
            this.opsTtlHours = opsTtlHours;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    public static class Mail {
        private String from = "Sarkari Desk <onboarding@resend.dev>";
        private String publicSiteUrl = "http://localhost:8090";
        private String resendApiKey = "";
        private int resetTtlHours = 1;

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getPublicSiteUrl() {
            return publicSiteUrl;
        }

        public void setPublicSiteUrl(String publicSiteUrl) {
            this.publicSiteUrl = publicSiteUrl;
        }

        public String getResendApiKey() {
            return resendApiKey;
        }

        public void setResendApiKey(String resendApiKey) {
            this.resendApiKey = resendApiKey;
        }

        public int getResetTtlHours() {
            return resetTtlHours;
        }

        public void setResetTtlHours(int resetTtlHours) {
            this.resetTtlHours = resetTtlHours;
        }
    }

    public static class Catalog {
        private int pageSize = 12;
        private String source = "json";
        private boolean seedDummy = false;

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public boolean isSeedDummy() {
            return seedDummy;
        }

        public void setSeedDummy(boolean seedDummy) {
            this.seedDummy = seedDummy;
        }
    }

    public static class RateLimit {
        private int windowMinutes = 15;
        private int loginMax = 5;
        private int collectMax = 20;

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        public int getLoginMax() {
            return loginMax;
        }

        public void setLoginMax(int loginMax) {
            this.loginMax = loginMax;
        }

        public int getCollectMax() {
            return collectMax;
        }

        public void setCollectMax(int collectMax) {
            this.collectMax = collectMax;
        }
    }
}
